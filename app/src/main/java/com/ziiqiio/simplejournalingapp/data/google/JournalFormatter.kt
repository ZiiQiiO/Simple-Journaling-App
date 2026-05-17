package com.ziiqiio.simplejournalingapp.data.google

import com.ziiqiio.simplejournalingapp.data.db.JournalEntry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class FormattedSections(
    val separator: String,
    val dateTime: String,
    val moodEnergy: String,
    val entry: String
) {
    fun joined(): String = separator + dateTime + moodEnergy + entry
}

object JournalFormatter {

    val MOOD_EMOJI = listOf("😞", "😕", "😐", "🙂", "😄")
    val MOOD_WORD = listOf("Awful", "Low", "Neutral", "Good", "Great")
    val ENERGY_EMOJI = listOf("🐌", "🐢", "🐕", "🐎", "🦅")

    fun moodLabel(index: Int): String {
        val i = index.coerceIn(0, MOOD_EMOJI.lastIndex)
        return "${MOOD_EMOJI[i]} ${MOOD_WORD[i]}"
    }

    fun energyIcon(value: Int): String {
        val i = (value - 1).coerceIn(0, ENERGY_EMOJI.lastIndex)
        return ENERGY_EMOJI[i]
    }

    fun energyLabel(value: Int): String {
        val level = value.coerceIn(1, ENERGY_EMOJI.size)
        return "${energyIcon(level)} $level/5"
    }

    fun formatDateHeading(timestampMillis: Long, zone: ZoneId): String {
        val zoned = Instant.ofEpochMilli(timestampMillis).atZone(zone)
        // e.g. "Tuesday, May 13, 2026 — 9:42 PM"
        return zoned.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy '—' h:mm a"))
    }

    fun formatTagsLine(tagsCsv: String): String? {
        val list = tagsCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (list.isEmpty()) return null
        return "Tags: ${list.joinToString(", ")}"
    }

    /**
     * Build the three sections needed for the Docs insert flow.
     *  - separator: a horizontal rule line + a blank line (precedes the date paragraph)
     *  - dateTime: the date string followed by a single newline (styled SUBTITLE)
     *  - moodEnergy: the mood and energy paragraph (styled HEADING_4)
     *  - entry: tags / prompt / body / trailing blanks (normal text paragraphs)
     */
    fun formatSections(entry: JournalEntry): FormattedSections {
        val zone = ZoneId.systemDefault()
        return formatSections(entry, zone)
    }

    fun formatSections(entry: JournalEntry, zone: ZoneId): FormattedSections {
        val sep = "────────────────────\n\n"
        val dateTime = formatDateHeading(entry.timestamp, zone) + "\n"
        val moodEnergy = "Mood: ${moodLabel(entry.moodIndex)}   |   Energy: ${energyLabel(entry.energyLevel)}\n"

        val sb = StringBuilder()
        formatTagsLine(entry.tags)?.let { sb.append(it).append('\n') }
        entry.promptUsed?.takeIf { it.isNotBlank() }?.let {
            sb.append("Prompt: ").append(it).append('\n')
        }
        sb.append('\n')
        sb.append(entry.body)
        if (!entry.body.endsWith("\n")) sb.append('\n')
        sb.append('\n').append('\n')

        return FormattedSections(separator = sep, dateTime = dateTime, moodEnergy = moodEnergy, entry = sb.toString())
    }

    /** Convenience: full block as a single string (for previews / read-only views). */
    fun format(entry: JournalEntry, zone: ZoneId = ZoneId.systemDefault()): String =
        formatSections(entry, zone).joined()
}
