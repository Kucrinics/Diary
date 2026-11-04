package ru.diary.app.util

import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Проверка ссылок на RuStore: они должны вести на опубликованный `ru.diary.app`,
 * а не на debug-пакет с суффиксом `.debug`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoreLinksTest {

    @Test
    fun `app id matches published release id`() {
        assertEquals("ru.diary.app", RUSTORE_APP_ID)
        assertFalse(RUSTORE_APP_ID.endsWith(".debug"))
    }

    @Test
    fun `deep link points to published app id`() {
        assertEquals(
            "rustore://apps.rustore.ru/app/ru.diary.app",
            ruStoreDeepLink().toString()
        )
    }

    @Test
    fun `web url points to published app id`() {
        assertEquals(
            "https://apps.rustore.ru/app/ru.diary.app",
            ruStoreWebUrl().toString()
        )
    }

    @Suppress("DEPRECATION")
    @Test
    fun `opens rustore deep link ignoring debug applicationId suffix`() {
        val app = ApplicationProvider.getApplicationContext<Application>()

        // Регистрируем «обработчик» deep link, чтобы startActivity не бросил исключение.
        val resolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "ru.rustore.app"
                name = "ru.rustore.app.MainActivity"
            }
        }
        shadowOf(app.packageManager).addResolveInfoForIntent(
            Intent(Intent.ACTION_VIEW, ruStoreDeepLink()),
            resolveInfo
        )

        openAppInRuStore(app)

        val started = shadowOf(app).nextStartedActivity
        assertNotNull("Приложение не попыталось открыть RuStore", started)
        assertEquals(ruStoreDeepLink(), started!!.data)
    }
}
