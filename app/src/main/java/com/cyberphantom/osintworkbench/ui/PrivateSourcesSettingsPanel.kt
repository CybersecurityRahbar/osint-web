package com.cyberphantom.osintworkbench.ui

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.cyberphantom.osintworkbench.data.PrivateSourceBot
import com.cyberphantom.osintworkbench.data.PrivateSourcesStore
import java.util.UUID

@Composable
fun PrivateSourcesSettingsPanel() {
    val context = LocalContext.current
    val store = remember { PrivateSourcesStore(context) }
    var bots by remember { mutableStateOf(store.loadBots()) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var botName by rememberSaveable { mutableStateOf("") }
    var botUsername by rememberSaveable { mutableStateOf("") }
    var numberCommand by rememberSaveable { mutableStateOf("") }
    var nameCommand by rememberSaveable { mutableStateOf("") }
    var numberPrompt by rememberSaveable { mutableStateOf("الرجاء ارسال الرقم") }
    var namePrompt by rememberSaveable { mutableStateOf("الرجاء ارسال الاسم") }
    var formMessage by remember { mutableStateOf<String?>(null) }
    var loginWebView by remember { mutableStateOf<WebView?>(null) }
    var loginStatus by remember { mutableStateOf("سجّل الدخول من النافذة أدناه؛ ستبقى جلسة الويب على هذا الجهاز ما دامت صالحة.") }

    fun resetForm() {
        editingId = null
        botName = ""
        botUsername = ""
        numberCommand = ""
        nameCommand = ""
        numberPrompt = "الرجاء ارسال الرقم"
        namePrompt = "الرجاء ارسال الاسم"
        formMessage = null
    }

    fun saveBot() {
        val cleanName = botName.trim()
        val cleanUsername = botUsername.trim().removePrefix("@")
        if (cleanName.isBlank() || cleanUsername.isBlank()) {
            formMessage = "أدخل اسم البوت واسم المستخدم أولاً."
            return
        }
        if (!cleanUsername.matches(Regex("[A-Za-z0-9_]{5,32}"))) {
            formMessage = "اسم المستخدم غير صالح. أدخل الاسم بعد @ من دون رابط."
            return
        }
        if (numberCommand.isBlank() && nameCommand.isBlank()) {
            formMessage = "أدخل أمراً واحداً على الأقل للرقم أو الاسم."
            return
        }
        if ((numberCommand.isNotBlank() && !numberCommand.trim().startsWith("/")) ||
            (nameCommand.isNotBlank() && !nameCommand.trim().startsWith("/"))) {
            formMessage = "يجب أن يبدأ كل أمر بالرمز /."
            return
        }

        val bot = PrivateSourceBot(
            id = editingId ?: UUID.randomUUID().toString(),
            name = cleanName,
            username = cleanUsername,
            numberCommand = numberCommand.trim(),
            nameCommand = nameCommand.trim(),
            numberPrompt = numberPrompt.trim().ifBlank { "الرجاء ارسال الرقم" },
            namePrompt = namePrompt.trim().ifBlank { "الرجاء ارسال الاسم" }
        )
        val updated = if (editingId == null) {
            bots.filterNot { it.username.equals(cleanUsername, ignoreCase = true) } + bot
        } else {
            bots.map { if (it.id == editingId) bot else it }
                .filterNot { it.id != bot.id && it.username.equals(cleanUsername, ignoreCase = true) }
        }
        store.saveBots(updated)
        bots = updated
        formMessage = if (editingId == null) "تمت إضافة المصدر. ستظهر أزراره تلقائياً في البحث." else "تم حفظ تعديلات المصدر."
        editingId = null
        botName = ""
        botUsername = ""
        numberCommand = ""
        nameCommand = ""
        numberPrompt = "الرجاء ارسال الرقم"
        namePrompt = "الرجاء ارسال الاسم"
    }

    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Surface(
            color = WorkbenchColors.Surface,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, WorkbenchColors.Border)
        ) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    androidx.compose.material3.Icon(Icons.Filled.Public, contentDescription = null, tint = WorkbenchColors.Teal, modifier = Modifier.size(23.dp))
                    Column(Modifier.weight(1f)) {
                        Text("اتصال حساب Telegram", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("تسجيل الدخول من الإعدادات مرة واحدة، وليس من شاشة البحث.", color = WorkbenchColors.TextSecondary, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                }
                Text(
                    loginStatus,
                    color = WorkbenchColors.TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 17.sp
                )
                Button(
                    onClick = { loginWebView?.loadUrl("https://web.telegram.org/k/") },
                    enabled = loginWebView != null,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WorkbenchColors.Teal, contentColor = Color(0xFF06231F))
                ) {
                    Text("فتح Telegram Web / إكمال تسجيل الدخول")
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().height(390.dp),
                    color = Color(0xFF0B1015),
                    shape = RoundedCornerShape(13.dp),
                    border = BorderStroke(1.dp, WorkbenchColors.Border)
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxWidth(),
                        factory = { webContext ->
                            WebView(webContext).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.databaseEnabled = true
                                settings.loadsImagesAutomatically = true
                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView, url: String) {
                                        CookieManager.getInstance().flush()
                                        loginStatus = if (url.contains("web.telegram.org")) {
                                            "تم تحميل Telegram Web. أكمل الدخول هنا إذا ظهرت شاشة تسجيل الدخول."
                                        } else {
                                            "تم تحميل صفحة ويب."
                                        }
                                    }
                                }
                                webChromeClient = WebChromeClient()
                                loginWebView = this
                                loadUrl("https://web.telegram.org/k/")
                            }
                        },
                        update = { view ->
                            if (loginWebView !== view) loginWebView = view
                        }
                    )
                }
                Text(
                    "لا تستخدم تسجيل الخروج ولا تمسح بيانات التطبيق إذا أردت استمرار الجلسة. إذا طلب Telegram إعادة التحقق، أكملها من هذه النافذة.",
                    color = WorkbenchColors.TextSecondary,
                    fontSize = 10.sp,
                    lineHeight = 16.sp
                )
            }
        }

        Surface(
            color = WorkbenchColors.Surface,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, WorkbenchColors.Border)
        ) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("إعداد المصادر الخاصة", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    "أضف كل بوت تستخدمه. اسم المحادثة يجب أن يطابق الاسم الظاهر في Telegram حتى يستطيع التطبيق التحقق من أنه فتح المحادثة الصحيحة. اكتب الأوامر كما ترسلها بالضبط، واكتب الجزء المميز من رسالة البوت التي تطلب الرقم أو الاسم.",
                    color = WorkbenchColors.TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 17.sp
                )

                if (bots.isEmpty()) {
                    Text("لم تتم إضافة أي بوت بعد.", color = WorkbenchColors.TextSecondary, fontSize = 11.sp)
                } else {
                    bots.forEach { bot ->
                        Surface(
                            color = WorkbenchColors.SurfaceElevated,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, WorkbenchColors.Border)
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(bot.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("@${bot.username}", color = WorkbenchColors.Teal, fontSize = 11.sp)
                                Text(
                                    "أمر الرقم: ${bot.numberCommand.ifBlank { "غير مفعّل" }} · أمر الاسم: ${bot.nameCommand.ifBlank { "غير مفعّل" }}",
                                    color = WorkbenchColors.TextSecondary,
                                    fontSize = 10.sp,
                                    lineHeight = 15.sp
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            editingId = bot.id
                                            botName = bot.name
                                            botUsername = bot.username
                                            numberCommand = bot.numberCommand
                                            nameCommand = bot.nameCommand
                                            numberPrompt = bot.numberPrompt
                                            namePrompt = bot.namePrompt
                                            formMessage = null
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = WorkbenchColors.Blue, contentColor = Color.White)
                                    ) {
                                        androidx.compose.material3.Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.size(5.dp))
                                        Text("تعديل", fontSize = 11.sp)
                                    }
                                    Button(
                                        onClick = {
                                            val updated = bots.filterNot { it.id == bot.id }
                                            store.saveBots(updated)
                                            bots = updated
                                            if (editingId == bot.id) resetForm()
                                            formMessage = "تم حذف إعداد المصدر من هذه القائمة."
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5B2933), contentColor = Color.White)
                                    ) {
                                        androidx.compose.material3.Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.size(5.dp))
                                        Text("حذف", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalLineDivider()

                Text(if (editingId == null) "إضافة بوت جديد" else "تعديل إعدادات البوت", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                ConfigField(botName, { botName = it }, "اسم البوت كما يظهر في Telegram")
                ConfigField(botUsername, { botUsername = it }, "اسم المستخدم (مثال: Yemennumber_bot)")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        ConfigField(numberCommand, { numberCommand = it }, "أمر البحث بالرقم")
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        ConfigField(nameCommand, { nameCommand = it }, "أمر البحث بالاسم")
                    }
                }
                ConfigField(numberPrompt, { numberPrompt = it }, "عبارة طلب الرقم من البوت")
                ConfigField(namePrompt, { namePrompt = it }, "عبارة طلب الاسم من البوت")
                Text(
                    "مثال: «الرجاء ارسال الرقم» أو «الرجاء ارسال الاسم». تتم مقارنة العبارة مع رسالة جديدة بعد إرسال الأمر، ثم يرسل التطبيق القيمة تلقائياً.",
                    color = WorkbenchColors.TextSecondary,
                    fontSize = 10.sp,
                    lineHeight = 16.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { saveBot() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WorkbenchColors.Teal, contentColor = Color(0xFF06231F))
                    ) {
                        androidx.compose.material3.Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.size(5.dp))
                        Text(if (editingId == null) "إضافة / حفظ" else "حفظ التعديلات", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { resetForm() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WorkbenchColors.SurfaceElevated, contentColor = WorkbenchColors.TextPrimary)
                    ) { Text("تفريغ الحقول", fontSize = 11.sp) }
                }
                if (formMessage != null) {
                    Text(formMessage.orEmpty(), color = WorkbenchColors.Teal, fontSize = 11.sp, lineHeight = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun ConfigField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label, fontSize = 10.sp) },
        colors = OutlinedTextFieldDefaults.colors(
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
    )
}

@Composable
private fun HorizontalLineDivider() {
    androidx.compose.material3.HorizontalDivider(color = WorkbenchColors.Border)
}
