package com.jf.checkin.logic.zip

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ArchiveNamingTest {

    @Test
    fun datePrefix_usesTwoDigitYearMonthDay() {
        assertEquals("26.11.12", datePrefix(LocalDate.of(2026, 11, 12)))
        assertEquals("26.01.05", datePrefix(LocalDate.of(2026, 1, 5)))
    }

    @Test
    fun sanitizeName_replacesIllegalCharacters() {
        assertEquals("张_三_", sanitizeName("张/三?"))
        assertEquals("a_b_c_d_e_f_g_h_i", sanitizeName("a\\b:c*d\"e<f>g|h\ti"))
    }

    @Test
    fun sanitizeName_trimsAndFallsBack() {
        assertEquals("未命名", sanitizeName("   "))
        assertEquals("未命名", sanitizeName("..."))
        assertEquals("张三", sanitizeName("  .张三.  "))
    }

    @Test
    fun sanitizeName_limitsTo24Characters() {
        val long = "一二三四五六七八九十一二三四五六七八九十一二三四五六七八"
        assertEquals(long.take(24), sanitizeName(long))
    }

    @Test
    fun archiveFileName_buildsZipName() {
        assertEquals("26.11.12-张三.zip", archiveFileName("26.11.12", "张三"))
        assertEquals("26.11.12-未命名.zip", archiveFileName("26.11.12", ""))
    }

    @Test
    fun imageEntryName_padsIndex() {
        assertEquals("26.11.12-01.jpg", imageEntryName("26.11.12", 1, "jpg"))
        assertEquals("26.11.12-12.png", imageEntryName("26.11.12", 12, "png"))
        assertEquals("26.11.12-100.webp", imageEntryName("26.11.12", 100, "webp"))
    }

    @Test
    fun customArchiveFileName_acceptsFullNameWithOrWithoutZip() {
        assertEquals("家长会照片.zip", customArchiveFileName("家长会照片"))
        assertEquals("家长会照片.zip", customArchiveFileName("家长会照片.zip"))
        assertEquals("家长会照片.zip", customArchiveFileName("家长会照片.ZIP"))
        assertEquals("家长会照片.zip", customArchiveFileName("  .家长会照片.zip.  "))
    }

    @Test
    fun customArchiveFileName_doesNotDuplicateZipSuffix() {
        assertEquals("final.zip", customArchiveFileName("final.zip.zip"))
    }

    @Test
    fun customArchiveFileName_sanitizesAndFallsBack() {
        assertEquals("a_b_c.zip", customArchiveFileName("a/b:c"))
        assertEquals("未命名.zip", customArchiveFileName("   "))
        assertEquals("未命名.zip", customArchiveFileName("..."))
    }
}
