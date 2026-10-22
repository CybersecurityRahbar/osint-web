package com.cyberphantom.osintworkbench.ui

import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.cyberphantom.osintworkbench.data.PrivateSourceBot
import com.cyberphantom.osintworkbench.data.PrivateSourceKind
import com.cyberphantom.osintworkbench.data.PrivateSourceTextMatcher
import com.cyberphantom.osintworkbench.data.PrivateSourcesStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.coroutines.resume

@Composable
fun PrivateSourcesSearchPanel() {
    val context = LocalContext.current
    val configStore = remember { PrivateSourcesStore(context) }
    var bots by remember { mutableStateOf(configStore.loadBots()) }
    var selectedBotId by rememberSaveable { mutableStateOf<String?>(bots.firstOrNull()?.id) }
    var query by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(PrivateSourceKind.NUMBER) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("اختر مصدراً وأدخل قيمة البحث.") }
    var result by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val selectedBot = bots.firstOrNull { it.id == selectedBotId }

    LaunchedEffect(bots) {
        if (bots.none { it.id == selectedBotId }) selectedBotId = bots.firstOrNull()?.id
    }
    LaunchedEffect(selectedBot?.id) {
        val bot = selectedBot
        if (bot != null && bot.command(kind).isBlank()) {
            kind = if (bot.numberCommand.isNotBlank()) PrivateSourceKind.NUMBER else PrivateSourceKind.NAME
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Surface(color = WorkbenchColors.Surface, shape = RoundedCornerShape(21.dp), border = BorderStroke(1.dp, WorkbenchColors.Border)) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    androidx.compose.material3.Icon(Icons.Filled.Public, contentDescription = null, tint = WorkbenchColors.Teal, modifier = Modifier.size(23.dp))
                    Column(Modifier.weight(1f)) {
                        Text("المصادر الخاصة", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("اختر بوتاً مضبوظاً وأرسل طلباً واحداً؛ يتابع التطبيق مطالبة البوت ويعرض الرد تلقائياً.", color = WorkbenchColors.TextSecondary, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                }

                if (bots.isEmpty()) {
                    Text("لا توجد مصادر مضبوطة. افتح الإعدادات ← إعداد المصادر الخاصة، وأضف بوتاً وأوامره أولاً.", color = WorkbenchColors.Amber, fontSize = 12.sp, lineHeight = 18.sp)
                } else {
                    Text("اختر المصدر", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        bots.forEach { bot ->
                            FilterChip(
                                selected = bot.id == selectedBotId,
                                onClick = {
                                    selectedBotId = bot.id
                                    result = null
                                    status = "تم اختيار ${bot.name}."
                                    if (bot.command(kind).isBlank()) kind = if (bot.numberCommand.isNotBlank()) PrivateSourceKind.NUMBER else PrivateSourceKind.NAME
                                },
                                label = { Text("${bot.name} · @${bot.username}", fontSize = 11.sp) }
                            )
                        }
                    }
                    Text("نوع البحث", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (selectedBot?.numberCommand?.isNotBlank() == true) {
                            FilterChip(selected = kind == PrivateSourceKind.NUMBER, onClick = { kind = PrivateSourceKind.NUMBER; result = null; status = "جاهز للبحث بالرقم." }, label = { Text("بحث بالرقم") })
                        }
                        if (selectedBot?.nameCommand?.isNotBlank() == true) {
                            FilterChip(selected = kind == PrivateSourceKind.NAME, onClick = { kind = PrivateSourceKind.NAME; result = null; status = "جاهز للبحث بالاسم." }, label = { Text("بحث بالاسم") })
                        }
                    }
                    OutlinedTextField(
                        value = query, onValueChange = { query = it; result = null },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(if (kind == PrivateSourceKind.NUMBER) "الرقم المطلوب" else "الاسم المطلوب") },
                        placeholder = { Text(if (kind == PrivateSourceKind.NUMBER) "أدخل الرقم" else "أدخل الاسم") },
                        colors = privateSearchFieldColors()
                    )
                    Button(
                        onClick = {
                            val bot = selectedBot
                            val targetView = webView
                            if (bot == null) status = "أضف بوتاً من الإعدادات أولاً."
                            else if (query.isBlank()) status = "أدخل قيمة البحث أولاً."
                            else if (bot.command(kind).isBlank()) status = "هذا النوع غير مضبوط لهذا البوت. عدّل إعداداته."
                            else if (targetView == null) status = "نافذة Telegram لم تجهز بعد. انتظر لحظة ثم أعد المحاولة."
                            else {
                                val searchValue = query.trim()
                                val selectedKind = kind
                                busy = true
                                result = null
                                scope.launch {
                                    try {
                                        result = TelegramWebLookup.search(targetView, bot, selectedKind, searchValue) { status = it }
                                        status = "اكتمل البحث من ${bot.name}."
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (error: Exception) {
                                        status = error.message ?: "تعذر إكمال البحث عبر Telegram."
                                    } finally {
                                        busy = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        enabled = !busy && selectedBot != null && query.isNotBlank() && selectedBot.command(kind).isNotBlank(),
                        shape = RoundedCornerShape(13.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WorkbenchColors.Teal, contentColor = Color(0xFF06231F))
                    ) {
                        if (busy) {
                            CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp, color = Color(0xFF06231F))
                            Spacer(Modifier.size(7.dp))
                            Text("يجري تنفيذ الطلب...")
                        } else {
                            androidx.compose.material3.Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(7.dp))
                            Text("بحث تلقائي عبر البوت", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Text(status, color = if (status.contains("تعذر") || status.contains("لم يتم") || status.contains("خطأ")) WorkbenchColors.Amber else WorkbenchColors.TextSecondary, fontSize = 11.sp, lineHeight = 17.sp)
                result?.takeIf { it.isNotBlank() }?.let { text ->
                    Surface(Modifier.fillMaxWidth(), color = WorkbenchColors.SurfaceElevated, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, WorkbenchColors.Border)) {
                        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("نتيجة البوت", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = WorkbenchColors.Teal)
                            Text(text, color = WorkbenchColors.TextPrimary, fontSize = 12.sp, lineHeight = 19.sp)
                        }
                    }
                }
            }
        }

        // Login is handled in Settings; WebView site storage/cookies are shared within this app.
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(1.dp).alpha(0.01f),
            factory = { webContext ->
                WebView(webContext).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.loadsImagesAutomatically = true
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) { CookieManager.getInstance().flush() }
                    }
                    webChromeClient = WebChromeClient()
                    webView = this
                    loadUrl("https://web.telegram.org/k/")
                }
            },
            update = { view -> if (webView !== view) webView = view }
        )
    }
}

private data class TelegramDomMessage(val key: String, val text: String, val outgoing: Boolean, val incoming: Boolean)
private data class TelegramDomSnapshot(val url: String, val title: String, val hasComposer: Boolean, val hasSendButton: Boolean, val messages: List<TelegramDomMessage>)

private object TelegramWebLookup {
    suspend fun search(webView: WebView, bot: PrivateSourceBot, kind: PrivateSourceKind, query: String, onStatus: (String) -> Unit): String {
        require(query.isNotBlank()) { "أدخل قيمة البحث أولاً." }
        val username = bot.username.trim().removePrefix("@")
        require(username.matches(Regex("[A-Za-z0-9_]{5,32}"))) { "اسم المستخدم المضبوط لهذا البوت غير صالح." }
        val command = bot.command(kind).trim()
        require(command.startsWith("/")) { "أمر البحث لهذا النوع غير مضبوط بصورة صحيحة." }

        onStatus("فتح محادثة @${username}...")
        webView.loadUrl("https://web.telegram.org/k/#@${username}")
        val ready = waitForSnapshot(webView, 35_000L) { s -> s.hasComposer && s.hasSendButton && s.title.isNotBlank() && isTargetChat(s, bot) }
        if (ready == null) {
            val last = readSnapshot(webView)
            if (!last.hasComposer || !last.hasSendButton) throw IllegalStateException("جلسة Telegram غير جاهزة. افتح الإعدادات ← اتصال حساب Telegram، وأكمل تسجيل الدخول ثم أعد البحث.")
            throw IllegalStateException("تعذر التأكد من أن المحادثة المفتوحة هي ${bot.name} (@${username}). تأكد أن اسم البوت في الإعدادات مطابق للاسم الظاهر في Telegram.")
        }

        val beforeCommand = ready.messages.map { it.key }.toSet()
        onStatus("إرسال أمر البوت: ${command}")
        sendMessage(webView, command)
        val commandEcho = waitForSnapshot(webView, 14_000L) { s -> s.messages.any { it.outgoing && it.text.trim() == command && it.key !in beforeCommand } }
        if (commandEcho == null) throw IllegalStateException("لم أتمكن من تأكيد إرسال الأمر؛ أوقفت العملية حتى لا أكرر الرسالة تلقائياً.")

        // Use the message set captured before sending the command so a fast prompt cannot be missed.
        val commandBaseline = beforeCommand
        val promptText = bot.prompt(kind).ifBlank { if (kind == PrivateSourceKind.NUMBER) "الرجاء ارسال الرقم" else "الرجاء ارسال الاسم" }
        onStatus("تم إرسال الأمر. أنتظر رسالة البوت التي تطلب القيمة…")
        val promptSnapshot = waitForSnapshot(webView, 28_000L) { s ->
            s.messages.any { m -> m.incoming && m.key !in commandBaseline && PrivateSourceTextMatcher.matchesPrompt(m.text, promptText) }
        }
        if (promptSnapshot == null) throw IllegalStateException("لم تصل رسالة الطلب المتوقعة خلال المهلة. راجع عبارة طلب الرقم/الاسم في الإعدادات؛ لم أرسل القيمة دون تأكيد مطالبة البوت.")

        val beforeValue = promptSnapshot.messages.map { it.key }.toSet()
        onStatus("وصل طلب البوت للقيمة. إرسال قيمة البحث الآن…")
        sendMessage(webView, query)
        val valueEcho = waitForSnapshot(webView, 14_000L) { s -> s.messages.any { it.outgoing && it.text.trim() == query.trim() && it.key !in beforeValue } }
        if (valueEcho == null) throw IllegalStateException("لم أتمكن من تأكيد إرسال قيمة البحث؛ لم أكررها تلقائياً.")

        // The snapshot before sending the query is the baseline; the reply may arrive before the outgoing echo is observed.
        val resultBaseline = beforeValue
        onStatus("تم إرسال القيمة. أنتظر نتيجة البوت…")
        val deadline = SystemClock.elapsedRealtime() + 35_000L
        val captured = linkedMapOf<String, String>()
        var lastNewAt = 0L
        while (SystemClock.elapsedRealtime() < deadline) {
            val snapshot = readSnapshot(webView)
            snapshot.messages.filter { m -> m.incoming && m.key !in resultBaseline && m.text.isNotBlank() }.forEach { m ->
                if (!captured.containsKey(m.key)) {
                    captured[m.key] = m.text.trim()
                    lastNewAt = SystemClock.elapsedRealtime()
                }
            }
            if (captured.isNotEmpty() && SystemClock.elapsedRealtime() - lastNewAt >= 1_800L) {
                return captured.values.take(12).joinToString("\n\n")
            }
            delay(650L)
        }
        if (captured.isNotEmpty()) return captured.values.take(12).joinToString("\n\n")
        throw IllegalStateException("تم إرسال الطلب، لكن لم تصل رسالة نتيجة خلال 35 ثانية.")
    }

    private fun isTargetChat(s: TelegramDomSnapshot, bot: PrivateSourceBot): Boolean {
        val route = s.url.lowercase()
        val username = bot.username.removePrefix("@").lowercase()
        if (route.contains("#@${username}") || route.contains("%40${username}")) return true
        val title = PrivateSourceTextMatcher.normalize(s.title)
        val expected = PrivateSourceTextMatcher.normalize(bot.name)
        return expected.isNotBlank() && title.isNotBlank() && (title == expected || title.contains(expected) || expected.contains(title))
    }

    private suspend fun sendMessage(webView: WebView, message: String) {
        val script = """
          (function(message) {
            const editor = document.querySelector('.input-message-input[contenteditable="true"]') || document.querySelector('#editable-message-text[contenteditable="true"]');
            if (!editor) return JSON.stringify({ok:false,error:'composer_not_found'});
            editor.focus();
            try { document.execCommand('selectAll', false, null); document.execCommand('insertText', false, message); } catch (_) {}
            let current = (editor.innerText || editor.textContent || '').trim();
            if (current !== message) {
              editor.textContent = message;
              editor.dispatchEvent(new InputEvent('input', {bubbles:true,inputType:'insertText',data:message}));
            }
            current = (editor.innerText || editor.textContent || '').trim();
            if (current !== message) return JSON.stringify({ok:false,error:'composer_value_mismatch'});
            const send = document.querySelector('.btn-send') || document.querySelector('button.main-button.send');
            if (!send) return JSON.stringify({ok:false,error:'send_button_not_found'});
            if (send.disabled || send.getAttribute('aria-disabled') === 'true') return JSON.stringify({ok:false,error:'send_button_disabled'});
            send.click();
            return JSON.stringify({ok:true});
          })(${JSONObject.quote(message)})
        """.trimIndent()
        val result = runCatching { JSONObject(evaluate(webView, script)) }.getOrNull()
        if (result?.optBoolean("ok") != true) {
            throw IllegalStateException(when (result?.optString("error")) {
                "composer_not_found" -> "لم أجد حقل الرسائل في Telegram Web."
                "composer_value_mismatch" -> "تعذر إدخال النص في محرر Telegram؛ لم يتم الضغط على إرسال."
                "send_button_not_found" -> "لم أجد زر الإرسال في Telegram Web."
                "send_button_disabled" -> "زر الإرسال غير متاح حالياً."
                else -> "تعذر إرسال الرسالة عبر واجهة Telegram."
            })
        }
    }

    private suspend fun waitForSnapshot(webView: WebView, timeoutMillis: Long, predicate: (TelegramDomSnapshot) -> Boolean): TelegramDomSnapshot? {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        while (SystemClock.elapsedRealtime() < deadline) {
            val snapshot = readSnapshot(webView)
            if (predicate(snapshot)) return snapshot
            delay(650L)
        }
        return null
    }

    private suspend fun readSnapshot(webView: WebView): TelegramDomSnapshot {
        val script = """
          (function() {
            const titleNode = document.querySelector('.chat-info .peer-title') || document.querySelector('.MiddleHeader .fullName, .MiddleHeader .info .title');
            const composer = document.querySelector('.input-message-input[contenteditable="true"]') || document.querySelector('#editable-message-text[contenteditable="true"]');
            const send = document.querySelector('.btn-send') || document.querySelector('button.main-button.send');
            const bubbles = Array.from(document.querySelectorAll('.bubble:not(.service):not(.is-date)'));
            const messages = bubbles.slice(-100).map((bubble, index) => {
              const node = bubble.querySelector('.message') || bubble.querySelector('.text-content') || bubble;
              const text = (node.innerText || node.textContent || '').trim();
              const outgoing = bubble.classList.contains('is-out') || bubble.classList.contains('out');
              const incoming = bubble.classList.contains('is-in') || bubble.classList.contains('in') || !outgoing;
              const key = bubble.getAttribute('data-mid') || bubble.getAttribute('data-message-id') || bubble.id || (String(index) + ':' + (outgoing ? 'out:' : 'in:') + text);
              return {key:String(key),text:String(text),outgoing:!!outgoing,incoming:!!incoming};
            }).filter(item => item.text.length > 0);
            return JSON.stringify({url:String(location.href || ''),title:String(titleNode ? titleNode.innerText || titleNode.textContent || '' : '').trim(),hasComposer:!!composer,hasSendButton:!!send,messages:messages});
          })()
        """.trimIndent()
        val raw = evaluate(webView, script)
        val root = runCatching { JSONObject(raw) }.getOrElse { throw IllegalStateException("تعذر قراءة حالة Telegram Web. أعد فتح قسم البحث وحاول مرة أخرى.") }
        val array = root.optJSONArray("messages") ?: JSONArray()
        val messages = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(TelegramDomMessage(item.optString("key"), item.optString("text"), item.optBoolean("outgoing"), item.optBoolean("incoming")))
            }
        }
        return TelegramDomSnapshot(root.optString("url"), root.optString("title"), root.optBoolean("hasComposer"), root.optBoolean("hasSendButton"), messages)
    }

    private suspend fun evaluate(webView: WebView, script: String): String =
        suspendCancellableCoroutine { continuation ->
            webView.evaluateJavascript(script) { raw ->
                if (continuation.isActive) {
                    val decoded = runCatching { JSONTokener(raw ?: "null").nextValue() as? String }.getOrNull()
                    continuation.resume(decoded ?: raw.orEmpty())
                }
            }
        }

}

@Composable
private fun privateSearchFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = WorkbenchColors.Teal,
    unfocusedBorderColor = WorkbenchColors.Border,
    focusedLabelColor = WorkbenchColors.Teal,
    unfocusedLabelColor = WorkbenchColors.TextSecondary,
    focusedTextColor = WorkbenchColors.TextPrimary,
    unfocusedTextColor = WorkbenchColors.TextPrimary,
    cursorColor = WorkbenchColors.Teal,
    focusedContainerColor = WorkbenchColors.Background.copy(alpha = 0.40f),
    unfocusedContainerColor = WorkbenchColors.Background.copy(alpha = 0.40f)
)
