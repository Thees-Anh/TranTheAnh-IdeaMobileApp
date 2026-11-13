package com.pawquest

import com.pawquest.localization.*
import com.pawquest.domain.model.*
import kotlin.test.*

class LocalizationTest {
    @Test fun supportedLanguageFallback() {
        assertEquals(AppLanguage.VIETNAMESE, AppLanguage.fromTag("vi-VN"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("fr"))
    }
    @Test fun switchPersistsAndRestores() {
        var saved = "en"
        AppLocale.initialize(AppLanguage.ENGLISH) { saved = it.tag }
        try {
            AppLocale.select(AppLanguage.VIETNAMESE)
            assertEquals("vi", saved)
            assertEquals("Nhà thú cưng", tr("Pet Home"))
            AppLocale.initialize(AppLanguage.fromTag(saved))
            assertEquals("Mua", tr("Buy"))
            AppLocale.select(AppLanguage.ENGLISH)
            assertEquals("Rainbow Window", tr("Cửa Sổ Cầu Vồng"))
        } finally { AppLocale.initialize(AppLanguage.ENGLISH) }
    }
    @Test fun templatesPreserveUserContentAndReorderArguments() {
        assertEquals("Thế giới nhỏ của My Cat Shop", translate("My Cat Shop's little world", AppLanguage.VIETNAMESE))
        assertEquals("Cấp 4 → Cấp 5", translate("Lv. 4 → Lv. 5", AppLanguage.VIETNAMESE))
        assertEquals("Đã dùng 150 Xu. Số dư còn lại: 50 Xu.", translate("Spent 150 Coins. Remaining balance: 50 Coins.", AppLanguage.VIETNAMESE))
        assertEquals("You still have “Học bài tiếng Việt” to complete.", translate("Bạn còn nhiệm vụ “Học bài tiếng Việt” cần hoàn thành.", AppLanguage.ENGLISH))
        assertEquals("An original title", translate("An original title", AppLanguage.VIETNAMESE))
    }
    @Test fun allDialoguesAndCareMessagesHaveBothLanguages() {
        PetDialogues.lines.values.flatten().forEach {
            assertNotEquals(it, translate(it, AppLanguage.ENGLISH), it)
        }
        listOf("drank","already_hydrated","fed","played","sleep_started","touched","woken","no_food",
            "already_full","not_enough_energy","not_enough_hunger","not_enough_water","already_happy",
            "fully_rested","already_sleeping","pet_sleeping","unknown").forEach {
            val message = PetActionResult("test", PetCareAction.TOUCH, it, false, 0).message
            assertNotEquals(message, translate(message, AppLanguage.VIETNAMESE), message)
        }
        AuthFailure.entries.forEach { assertNotEquals(it.userMessage, translate(it.userMessage, AppLanguage.VIETNAMESE)) }
    }
    @Test fun catalogPairsKeepPlaceholderSets() {
        val placeholder = Regex("\\{\\d+\\}")
        translationPairs().forEach { (en, vi) ->
            assertTrue(en.isNotBlank() && vi.isNotBlank())
            assertEquals(placeholder.findAll(en).map { it.value }.toSet(), placeholder.findAll(vi).map { it.value }.toSet(), en)
        }
    }
}
