package com.cyberphantom.osintworkbench.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.text.Normalizer

enum class PrivateSourceKind { NUMBER, NAME }

data class PrivateSourceBot(
    val id: String,
    val name: String,
    val username: String,
    val numberCommand: String,
    val nameCommand: String,
    val numberPrompt: String,
    val namePrompt: String
) {
    fun command(kind: PrivateSourceKind): String =
        if (kind == PrivateSourceKind.NUMBER) numberCommand else nameCommand

    fun prompt(kind: PrivateSourceKind): String =
        if (kind == PrivateSourceKind.NUMBER) numberPrompt else namePrompt
}

/**
 * Stores locally configured Telegram sources. No credentials or Telegram session
 * tokens are stored here; the WebView owns its own site storage.
 */
class PrivateSourcesStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "osint_private_sources",
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun loadBots(): List<PrivateSourceBot> {
        val raw = preferences.getString(KEY_BOTS, null)
        if (raw == null) {
            val defaults = listOf(
                PrivateSourceBot(
                    id = "yemennumber-default",
                    name = "بوت كاشف الارقام اليمنية 967",
                    username = "Yemennumber_bot",
                    numberCommand = "/search_by_number",
                    nameCommand = "/search_by_name",
                    numberPrompt = "الرجاء ارسال الرقم",
                    namePrompt = "الرجاء ارسال الاسم"
                )
            )
            saveBots(defaults)
            return defaults
        }

        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val username = item.optString("username").trim().removePrefix("@")
                    val name = item.optString("name").trim()
                    if (username.isBlank() || name.isBlank()) continue
                    add(
                        PrivateSourceBot(
                            id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                            name = name,
                            username = username,
                            numberCommand = item.optString("numberCommand").trim(),
                            nameCommand = item.optString("nameCommand").trim(),
                            numberPrompt = item.optString("numberPrompt").ifBlank { "الرجاء ارسال الرقم" },
                            namePrompt = item.optString("namePrompt").ifBlank { "الرجاء ارسال الاسم" }
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun saveBots(bots: List<PrivateSourceBot>) {
        val array = JSONArray()
        bots.forEach { bot ->
            array.put(
                JSONObject()
                    .put("id", bot.id)
                    .put("name", bot.name.trim())
                    .put("username", bot.username.trim().removePrefix("@"))
                    .put("numberCommand", bot.numberCommand.trim())
                    .put("nameCommand", bot.nameCommand.trim())
                    .put("numberPrompt", bot.numberPrompt.trim())
                    .put("namePrompt", bot.namePrompt.trim())
            )
        }
        preferences.edit().putString(KEY_BOTS, array.toString()).apply()
    }

    companion object {
        private const val KEY_BOTS = "configured_bots_v1"
    }
}

/** Text matching helpers shared by the prompt-driven Telegram flow and its unit tests. */
object PrivateSourceTextMatcher {
    fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .lowercase()
            .trim()

    fun matchesPrompt(message: String, configuredPhrase: String): Boolean {
        val expected = normalize(configuredPhrase)
        return expected.isNotBlank() && normalize(message).contains(expected)
    }
}
