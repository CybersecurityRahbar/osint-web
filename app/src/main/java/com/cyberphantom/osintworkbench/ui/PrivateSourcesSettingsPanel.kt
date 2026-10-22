package com.cyberphantom.osintworkbench.ui

import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyberphantom.osintworkbench.data.PrivateSourceBot
import com.cyberphantom.osintworkbench.data.PrivateSourcesStore
import java.util.UUID

private enum class PrivateSourcesSettingsTab {
    CONFIGURATION,
    TELEGRAM_LOGIN
}

@Composable
fun PrivateSourcesSettingsPanel(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { PrivateSourcesStore(context) }
    var bots by remember { mutableStateOf(store.loadBots()) }
    var selectedTab by rememberSaveable { mutableStateOf(PrivateSourcesSettingsTab.CONFIGURATION) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var botName by rememberSaveable { mutableStateOf("") }
    var botUsername by rememberSaveable { mutableStateOf("") }
    var numberCommand by rememberSaveable { mutableStateOf("") }
    var nameCommand by rememberSaveable { mutableStateOf("") }
    var numberPrompt by rememberSaveable { mutableStateOf("الرجاء ارسال الرقم") }
    var namePrompt by rememberSaveable { mutableStateOf("الرجاء ارسال الاسم") }
    var formMessage by remember { mutableStateOf<String?>(null) }

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
        formMessage = if (editingId == null) {
            "تمت إضافة المصدر. ستظهر أزراره تلقائياً في البحث."
        } else {
            "تم حفظ تعديلات المصدر."
        }
        editingId = null
        botName = ""
        botUsername = ""
        numberCommand = ""
        nameCommand = ""
        numberPrompt = "الرجاء ارسال الرقم"
        namePrompt = "الرجاء ارسال الاسم"
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 18.dp, top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconButton(onClick = onBack) {
                androidx.compose.material3.Icon(Icons.Filled.ArrowBack, contentDescription = "العودة إلى الإعدادات", tint = WorkbenchColors.Teal)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("إعداد المصادر الخاصة", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                Text("إدارة بوتات البحث وتسجيل Telegram", fontSize = 11.sp, color = WorkbenchColors.TextSecondary)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            SettingsTabButton(
                title = "إعداد المصادر",
                selected = selectedTab == PrivateSourcesSettingsTab.CONFIGURATION,
                modifier = Modifier.weight(1f),
                onClick = { selectedTab = PrivateSourcesSettingsTab.CONFIGURATION }
            )
            SettingsTabButton(
                title = "تسجيل الدخول",
                selected = selectedTab == PrivateSourcesSettingsTab.TELEGRAM_LOGIN,
                modifier = Modifier.weight(1f),
                onClick = { selectedTab = PrivateSourcesSettingsTab.TELEGRAM_LOGIN }
            )
        }

        androidx.compose.material3.HorizontalDivider(color = WorkbenchColors.Border)

        when (selectedTab) {
            PrivateSourcesSettingsTab.CONFIGURATION -> {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    Surface(
                        color = WorkbenchColors.Surface,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, WorkbenchColors.Border)
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("إدارة المصادر", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                "أضف كل بوت تستخدمه. أدخل اسم المستخدم كما يظهر في Telegram، واضبط أوامر الرقم والاسم وعبارات الطلب التي يرسلها البوت. ستظهر أنماط البحث المفعلة تلقائياً في شاشة البحث.",
                                color = WorkbenchColors.TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 17.sp
                            )
                        }
                    }

                    if (bots.isEmpty()) {
                        Surface(color = WorkbenchColors.Surface, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, WorkbenchColors.Border)) {
                            Text("لم تتم إضافة أي بوت بعد.", modifier = Modifier.fillMaxWidth().padding(14.dp), color = WorkbenchColors.TextSecondary, fontSize = 11.sp)
                        }
                    } else {
                        bots.forEach { bot ->
                            Surface(
                                color = WorkbenchColors.Surface,
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, WorkbenchColors.Border)
                            ) {
                                Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                        Box(
                                            Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).padding(7.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            androidx.compose.material3.Icon(Icons.Filled.Public, contentDescription = null, tint = WorkbenchColors.Teal)
                                        }
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(bot.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text("@${bot.username}", color = WorkbenchColors.Teal, fontSize = 11.sp)
                                        }
                                    }
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

                    HorizontalSourceDivider()

                    Text(if (editingId == null) "إضافة بوت جديد" else "تعديل إعدادات البوت", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    ConfigField(botName, { botName = it }, "اسم البوت كما يظهر في Telegram")
                    ConfigField(botUsername, { botUsername = it }, "اسم المستخدم (مثال: Yemennumber_bot)")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            ConfigField(numberCommand, { numberCommand = it }, "أمر البحث بالرقم")
                        }
                        Column(Modifier.weight(1f)) {
                            ConfigField(nameCommand, { nameCommand = it }, "أمر البحث بالاسم")
                        }
                    }
                    ConfigField(numberPrompt, { numberPrompt = it }, "عبارة طلب الرقم من البوت")
                    ConfigField(namePrompt, { namePrompt = it }, "عبارة طلب الاسم من البوت")
                    Text(
                        "مثال: «الرجاء ارسال الرقم» أو «الرجاء ارسال الاسم». ينتظر التطبيق رسالة جديدة تحتوي هذه العبارة بعد إرسال الأمر، ثم يرسل القيمة.",
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
                        ) {
                            Text("تفريغ الحقول", fontSize = 11.sp)
                        }
                    }
                    formMessage?.let {
                        Text(it, color = WorkbenchColors.Teal, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                    Spacer(Modifier.height(5.dp))
                }
            }

            PrivateSourcesSettingsTab.TELEGRAM_LOGIN -> TelegramLoginTab(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SettingsTabButton(title: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(13.dp)).clickable(onClick = onClick),
        color = if (selected) Color(0xFF153A3A) else WorkbenchColors.Surface,
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(1.dp, if (selected) WorkbenchColors.Teal.copy(alpha = 0.7f) else WorkbenchColors.Border)
    ) {
        Text(
            title,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 13.dp),
            color = if (selected) WorkbenchColors.Teal else WorkbenchColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TelegramLoginTab(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var loginStatus by remember { mutableStateOf("يتم تحميل Telegram Web…") }
    var loginWebView by remember { mutableStateOf<WebView?>(null) }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "سجّل الدخول إلى حساب Telegram من هنا. هذه الشاشة مخصصة لتسجيل الدخول فقط؛ البحث وإرسال أوامر البوتات يتمان من قسم البحث.",
            color = WorkbenchColors.TextSecondary,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.Icon(Icons.Filled.Login, contentDescription = null, tint = WorkbenchColors.Teal, modifier = Modifier.size(18.dp))
            Text(loginStatus, modifier = Modifier.weight(1f), color = WorkbenchColors.TextSecondary, fontSize = 10.sp, lineHeight = 14.sp)
            Button(
                onClick = { loginWebView?.loadUrl("https://web.telegram.org/k/") },
                enabled = loginWebView != null,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WorkbenchColors.Teal, contentColor = Color(0xFF06231F))
            ) {
                Text("إعادة تحميل", fontSize = 10.sp)
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            color = Color(0xFF0B1015),
            shape = RoundedCornerShape(13.dp),
            border = BorderStroke(1.dp, WorkbenchColors.Border)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { webContext ->
                    WebView(webContext).apply {
                        settings.javaScriptEnabled = true
                        settings.javaScriptCanOpenWindowsAutomatically = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.loadsImagesAutomatically = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.setSupportZoom(false)
                        isFocusable = true
                        isFocusableInTouchMode = true
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                                loginStatus = "جارٍ تحميل Telegram Web…"
                            }
                            override fun onPageFinished(view: WebView, url: String) {
                                CookieManager.getInstance().flush()
                                loginStatus = if (url.contains("web.telegram.org")) {
                                    "تم تحميل Telegram؛ اضغط داخل الصفحة للتمرير وإدخال رمز الدخول."
                                } else {
                                    "تم تحميل صفحة ويب."
                                }
                            }
                            override fun onReceivedError(
                                view: WebView,
                                request: WebResourceRequest,
                                error: WebResourceError
                            ) {
                                if (request.isForMainFrame) {
                                    loginStatus = "تعذر تحميل Telegram. تحقق من الاتصال ثم اضغط إعادة تحميل."
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
            "مرّر داخل نافذة Telegram نفسها. عند ظهور لوحة المفاتيح، استخدمها لإدخال رقم الهاتف أو رمز التحقق. لا تمسح بيانات التطبيق ولا تسجّل الخروج للحفاظ على الجلسة.",
            color = WorkbenchColors.TextSecondary,
            fontSize = 10.sp,
            lineHeight = 15.sp
        )
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
private fun HorizontalSourceDivider() {
    androidx.compose.material3.HorizontalDivider(color = WorkbenchColors.Border)
}
