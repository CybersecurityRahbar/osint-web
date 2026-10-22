package com.cyberphantom.osintworkbench.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateSourceTextMatcherTest {
    @Test
    fun matchesConfiguredNumberPromptEvenWithArabicHamzaVariation() {
        assertTrue(
            PrivateSourceTextMatcher.matchesPrompt(
                "الرجاء إرسال الرقم للبحث عنه",
                "الرجاء ارسال الرقم"
            )
        )
    }

    @Test
    fun matchesConfiguredNamePrompt() {
        assertTrue(
            PrivateSourceTextMatcher.matchesPrompt(
                "الرجاء ارسال الاسم الذي تريد البحث عنه",
                "الرجاء ارسال الاسم"
            )
        )
    }

    @Test
    fun doesNotTreatUnrelatedReplyAsPrompt() {
        assertFalse(
            PrivateSourceTextMatcher.matchesPrompt(
                "لا توجد نتائج لهذا البحث",
                "الرجاء ارسال الرقم"
            )
        )
    }

    @Test
    fun blankConfiguredPromptDoesNotMatchEverything() {
        assertFalse(PrivateSourceTextMatcher.matchesPrompt("أي رسالة", ""))
    }

    @Test
    fun botChoosesCommandFromItsSavedConfiguration() {
        val bot = PrivateSourceBot(
            id = "test",
            name = "Test",
            username = "test_bot",
            numberCommand = "/lookup_phone",
            nameCommand = "/lookup_name",
            numberPrompt = "أرسل الرقم",
            namePrompt = "أرسل الاسم"
        )
        assertTrue(bot.command(PrivateSourceKind.NUMBER) == "/lookup_phone")
        assertTrue(bot.command(PrivateSourceKind.NAME) == "/lookup_name")
    }
}
