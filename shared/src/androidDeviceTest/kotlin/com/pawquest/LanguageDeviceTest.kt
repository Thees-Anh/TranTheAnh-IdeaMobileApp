package com.pawquest

import com.pawquest.localization.*
import com.pawquest.domain.model.PetDialogues
import org.junit.Assert.*
import org.junit.Test

/** Runs on Android's ICU regex implementation, which differs from host JVM regex. */
class LanguageDeviceTest {
    @Test fun templatesAndCatalogueInitializeOnAndroid() {
        assertEquals("Cấp 4 → Cấp 5", translate("Lv. 4 → Lv. 5", AppLanguage.VIETNAMESE))
        assertEquals("Đã dùng 150 Xu. Số dư còn lại: 50 Xu.",
            translate("Spent 150 Coins. Remaining balance: 50 Coins.", AppLanguage.VIETNAMESE))
        assertEquals("Rainbow Window", translate("Cửa Sổ Cầu Vồng", AppLanguage.ENGLISH))
        PetDialogues.lines.values.flatten().forEach { assertNotEquals(it, translate(it, AppLanguage.ENGLISH)) }
    }
}
