package ru.diary.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * ID приложения в RuStore. Совпадает с release `applicationId`
 * (у debug-сборки есть суффикс `.debug`, поэтому нельзя брать `context.packageName`).
 */
const val RUSTORE_APP_ID = "ru.diary.app"

/** Deep link, открывающий карточку приложения в приложении RuStore. */
internal fun ruStoreDeepLink(appId: String = RUSTORE_APP_ID): Uri =
    Uri.parse("rustore://apps.rustore.ru/app/$appId")

/** Веб-страница приложения в RuStore — запасной вариант, если RuStore не установлен. */
internal fun ruStoreWebUrl(appId: String = RUSTORE_APP_ID): Uri =
    Uri.parse("https://apps.rustore.ru/app/$appId")

/**
 * Открывает страницу приложения в RuStore.
 *
 * Сначала пробуем deep link в приложение RuStore, а если оно не установлено
 * (deep link не обрабатывается) — открываем веб-версию страницы.
 */
fun openAppInRuStore(context: Context, appId: String = RUSTORE_APP_ID) {
    val appContext = context.applicationContext
    val deepLink = Intent(Intent.ACTION_VIEW, ruStoreDeepLink(appId))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    try {
        appContext.startActivity(deepLink)
        return
    } catch (_: Exception) {
        // RuStore не установлен — откроем веб-страницу.
    }

    val web = Intent(Intent.ACTION_VIEW, ruStoreWebUrl(appId))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    try {
        appContext.startActivity(web)
    } catch (_: Exception) {
        // Совсем нет обработчика — молча выходим.
    }
}
