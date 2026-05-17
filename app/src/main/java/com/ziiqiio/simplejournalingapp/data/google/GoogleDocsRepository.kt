package com.ziiqiio.simplejournalingapp.data.google

import android.content.Context
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.docs.v1.Docs
import com.google.api.services.docs.v1.model.BatchUpdateDocumentRequest
import com.google.api.services.docs.v1.model.AddDocumentTabRequest
import com.google.api.services.docs.v1.model.Body
import com.google.api.services.docs.v1.model.EndOfSegmentLocation
import com.google.api.services.docs.v1.model.InsertTextRequest
import com.google.api.services.docs.v1.model.Location
import com.google.api.services.docs.v1.model.ParagraphStyle
import com.google.api.services.docs.v1.model.Range
import com.google.api.services.docs.v1.model.Request
import com.google.api.services.docs.v1.model.Tab
import com.google.api.services.docs.v1.model.TabProperties
import com.google.api.services.docs.v1.model.UpdateParagraphStyleRequest
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.ziiqiio.simplejournalingapp.data.db.JournalEntry
import com.ziiqiio.simplejournalingapp.data.prefs.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class GoogleDocFile(
    val id: String,
    val name: String,
    val modifiedTime: String?
)

/**
 * Strategy: monthly Doc inside a single "Journal" folder on Drive.
 * Doc IDs cached in DataStore keyed by YYYY-MM. If the user supplies an
 * override Doc ID in Settings, we append to that single doc instead.
 */
class GoogleDocsRepository(
    private val context: Context,
    private val auth: GoogleAuthManager,
    private val prefs: AppPreferences
) {

    private val httpTransport = NetHttpTransport()
    private val jsonFactory = GsonFactory.getDefaultInstance()

    private fun docsClient(): Docs {
        val account = auth.currentAccount() ?: error("No signed-in Google account")
        val credential = auth.credential(account)
        return Docs.Builder(httpTransport, jsonFactory, credential)
            .setApplicationName("Simple Journaling App")
            .build()
    }

    private fun driveClient(): Drive {
        val account = auth.currentAccount() ?: error("No signed-in Google account")
        val credential = auth.credential(account)
        return Drive.Builder(httpTransport, jsonFactory, credential)
            .setApplicationName("Simple Journaling App")
            .build()
    }

    suspend fun resolveTargetDocId(entry: JournalEntry, zone: ZoneId): String = withContext(Dispatchers.IO) {
        val override = prefs.overrideDocId.first()
        if (!override.isNullOrBlank()) return@withContext override

        val ym = YearMonth.from(
            java.time.Instant.ofEpochMilli(entry.timestamp).atZone(zone).toLocalDate()
        )
        val key = ym.format(DateTimeFormatter.ofPattern("yyyy-MM"))
        prefs.getMonthlyDocId(key)?.let { return@withContext it }

        val folderId = ensureJournalFolder()
        val docId = createMonthlyDoc(folderId, ym)
        prefs.setMonthlyDocId(key, docId)
        docId
    }

    suspend fun searchDocs(query: String): List<GoogleDocFile> = withContext(Dispatchers.IO) {
        val drive = driveClient()
        val safeQuery = query.trim().replace("\\", "\\\\").replace("'", "\\'")
        val filters = mutableListOf(
            "mimeType = 'application/vnd.google-apps.document'",
            "trashed = false"
        )
        if (safeQuery.isNotBlank()) {
            filters += "name contains '$safeQuery'"
        }

        drive.files().list()
            .setQ(filters.joinToString(" and "))
            .setSpaces("drive")
            .setOrderBy("modifiedTime desc")
            .setPageSize(20)
            .setFields("files(id, name, modifiedTime)")
            .execute()
            .files
            .orEmpty()
            .map { file ->
                GoogleDocFile(
                    id = file.id,
                    name = file.name ?: "Untitled Google Doc",
                    modifiedTime = file.modifiedTime?.toStringRfc3339()
                )
            }
    }

    suspend fun getDocTitle(docId: String): String? = withContext(Dispatchers.IO) {
        driveClient().files().get(docId)
            .setFields("name")
            .execute()
            .name
    }

    private suspend fun ensureJournalFolder(): String = withContext(Dispatchers.IO) {
        val cached = prefs.journalFolderId.first()
        if (!cached.isNullOrBlank()) return@withContext cached

        val drive = driveClient()
        val q = "name = 'Journal' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
        val existing = drive.files().list()
            .setQ(q)
            .setSpaces("drive")
            .setFields("files(id, name)")
            .execute()
            .files
            .orEmpty()
            .firstOrNull()

        val id = existing?.id ?: run {
            val meta = File().apply {
                name = "Journal"
                mimeType = "application/vnd.google-apps.folder"
            }
            drive.files().create(meta).setFields("id").execute().id
        }
        prefs.setJournalFolderId(id)
        id
    }

    private fun createMonthlyDoc(folderId: String, ym: YearMonth): String {
        val title = "Journal — ${ym.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${ym.year}"
        val drive = driveClient()
        val meta = File().apply {
            name = title
            this.mimeType = "application/vnd.google-apps.document"
            parents = listOf(folderId)
        }
        val created = drive.files().create(meta).setFields("id").execute()
        val docId = created.id

        val docs = docsClient()
        val requests = listOf(
            Request().setInsertText(
                InsertTextRequest()
                    .setText("$title\n\n")
                    .setEndOfSegmentLocation(EndOfSegmentLocation())
            ),
            Request().setUpdateParagraphStyle(
                UpdateParagraphStyleRequest()
                    .setRange(Range().setStartIndex(1).setEndIndex(1 + title.length))
                    .setParagraphStyle(ParagraphStyle().setNamedStyleType("HEADING_1"))
                    .setFields("namedStyleType")
            )
        )
        docs.documents().batchUpdate(docId, BatchUpdateDocumentRequest().setRequests(requests)).execute()
        return docId
    }

    suspend fun appendEntry(docId: String, entry: JournalEntry, zone: ZoneId): Unit = withContext(Dispatchers.IO) {
        val docs = docsClient()
        val sections = JournalFormatter.formatSections(entry)
        val year = java.time.Instant.ofEpochMilli(entry.timestamp).atZone(zone).year.toString()
        val tabId = ensureYearTab(docs, docId, year)

        // Step 1: append the separator at end of body.
        docs.documents().batchUpdate(
            docId,
            BatchUpdateDocumentRequest().setRequests(
                listOf(
                    Request().setInsertText(
                        InsertTextRequest()
                            .setText(sections.separator)
                            .setEndOfSegmentLocation(EndOfSegmentLocation().setTabId(tabId))
                    )
                )
            )
        ).execute()

        // Step 2: find the current end index and insert the formatted entry with styling.
        val doc1 = docs.documents().get(docId).setIncludeTabsContent(true).execute()
        val targetBody = findTabBody(doc1.tabs.orEmpty(), tabId) ?: doc1.body
        // The doc body's last content element's endIndex is "one past the last visible char";
        // we insert at endIndex - 1 so the new text goes before the implicit final newline.
        val endIdx1 = (targetBody.content.lastOrNull()?.endIndex ?: 1) - 1

        val dateTimeStart = endIdx1
        val dateTimeEnd = dateTimeStart + sections.dateTime.length
        val moodEnergyStart = dateTimeEnd
        val moodEnergyEnd = moodEnergyStart + sections.moodEnergy.length
        val entryStart = moodEnergyEnd
        val entryEnd = entryStart + sections.entry.length

        val requests = mutableListOf<Request>()
        requests += Request().setInsertText(
            InsertTextRequest()
                .setText(sections.dateTime + sections.moodEnergy + sections.entry)
                .setLocation(Location().setIndex(dateTimeStart).setTabId(tabId))
        )
        requests += Request().setUpdateParagraphStyle(
            UpdateParagraphStyleRequest()
                .setRange(Range().setStartIndex(dateTimeStart).setEndIndex(dateTimeEnd))
                .setParagraphStyle(ParagraphStyle().setNamedStyleType("SUBTITLE"))
                .setFields("namedStyleType")
        )
        requests += Request().setUpdateParagraphStyle(
            UpdateParagraphStyleRequest()
                .setRange(Range().setStartIndex(moodEnergyStart).setEndIndex(moodEnergyEnd))
                .setParagraphStyle(ParagraphStyle().setNamedStyleType("HEADING_4"))
                .setFields("namedStyleType")
        )
        if (entryEnd > entryStart) {
            requests += Request().setUpdateParagraphStyle(
                UpdateParagraphStyleRequest()
                    .setRange(Range().setStartIndex(entryStart).setEndIndex(entryEnd))
                    .setParagraphStyle(ParagraphStyle().setNamedStyleType("NORMAL_TEXT"))
                    .setFields("namedStyleType")
            )
        }

        docs.documents().batchUpdate(docId, BatchUpdateDocumentRequest().setRequests(requests)).execute()
    }

    private fun ensureYearTab(docs: Docs, docId: String, year: String): String {
        val doc = docs.documents().get(docId).setIncludeTabsContent(true).execute()
        findTabByTitle(doc.tabs.orEmpty(), year)?.let { return it }

        val response = docs.documents().batchUpdate(
            docId,
            BatchUpdateDocumentRequest().setRequests(
                listOf(
                    Request().setAddDocumentTab(
                        AddDocumentTabRequest()
                            .setTabProperties(TabProperties().setTitle(year))
                    )
                )
            )
        ).execute()

        return response.replies
            ?.firstOrNull()
            ?.addDocumentTab
            ?.tabProperties
            ?.tabId
            ?: error("Google Docs did not return the new $year tab ID.")
    }

    private fun findTabByTitle(tabs: List<Tab>, title: String): String? {
        tabs.forEach { tab ->
            val props = tab.tabProperties
            if (props?.title == title) return props.tabId
            findTabByTitle(tab.childTabs.orEmpty(), title)?.let { return it }
        }
        return null
    }

    private fun findTabBody(tabs: List<Tab>, tabId: String): Body? {
        tabs.forEach { tab ->
            if (tab.tabProperties?.tabId == tabId) return tab.documentTab?.body
            findTabBody(tab.childTabs.orEmpty(), tabId)?.let { return it }
        }
        return null
    }
}
