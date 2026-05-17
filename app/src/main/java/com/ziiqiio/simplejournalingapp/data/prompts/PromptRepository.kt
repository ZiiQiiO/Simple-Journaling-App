package com.ziiqiio.simplejournalingapp.data.prompts

import android.content.Context
import com.ziiqiio.simplejournalingapp.R
import com.ziiqiio.simplejournalingapp.data.db.JournalDao
import com.ziiqiio.simplejournalingapp.data.db.RecentPrompt
import kotlin.random.Random

data class PromptSuggestion(val id: Int, val text: String)

class PromptRepository(
    context: Context,
    private val dao: JournalDao,
    private val recentWindow: Int = 20
) {
    private val prompts: List<String> = context.resources
        .getStringArray(R.array.journal_prompts)
        .toList()

    suspend fun pickRandomPrompt(): PromptSuggestion? {
        if (prompts.isEmpty()) return null
        val excluded = dao.recentPromptIds(recentWindow).toSet()
        val pool = prompts.indices.filter { it !in excluded }
        val chosenId = if (pool.isEmpty()) {
            // Should be impossible with 100 prompts & 20 window, but guard anyway.
            Random.nextInt(prompts.size)
        } else {
            pool[Random.nextInt(pool.size)]
        }
        dao.insertRecentPrompt(RecentPrompt(chosenId, System.currentTimeMillis()))
        dao.pruneRecentPrompts(recentWindow)
        return PromptSuggestion(chosenId, prompts[chosenId])
    }
}
