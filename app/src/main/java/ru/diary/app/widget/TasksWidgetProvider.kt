package ru.diary.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.text.TextPaint
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt
import ru.diary.app.DiaryApp
import ru.diary.app.MainActivity
import ru.diary.app.R
import ru.diary.app.data.db.DateUtils
import ru.diary.app.data.model.Task
import ru.diary.app.data.model.TaskOccurrences

/**
 * Виджет «Задачи» для домашнего экрана.
 * Показывает задачи на сегодня (не выполненные) и открывает приложение по тапу.
 * Строки задач имеют layout_weight и поэтому растягиваются на всю высоту виджета,
 * занимая всю его площадь при любом размере. Длинный заголовок переносится
 * на несколько строк, а сверх максимума строк остаток скрывается в «ещё N».
 */
class TasksWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            appWidgetManager.updateAppWidget(appWidgetId, buildRemoteViews(context, appWidgetManager, appWidgetId))
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        // Виджет изменил размер — перерисовываем под новую геометрию.
        appWidgetManager.updateAppWidget(appWidgetId, buildRemoteViews(context, appWidgetManager, appWidgetId))
    }

    /**
     * Принудительное обновление всех размещённых виджетов.
     * Вызывается из приложения после изменения данных о задачах.
     */
    fun requestUpdate(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, TasksWidgetProvider::class.java))
        if (ids.isEmpty()) return
        onUpdate(context, manager, ids)
    }

    private fun buildRemoteViews(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_tasks)
        val (locale, viewContext) = localeContext(context)
        val dateLocal = DateUtils.parseDate(DateUtils.today())

        val isoDate = DateUtils.toIsoDate(dateLocal)
        val tasks: List<Task> = runBlocking {
            val app = viewContext.applicationContext as? DiaryApp
                ?: return@runBlocking emptyList()
            val repo = app.container.taskRepository
            // Реальные задачи дня объединяем с виртуальными вхождениями повторов,
            // чтобы ежедневная задача, созданная вчера, была видна и сегодня.
            // Порядок тот же, что в списке приложения.
            val dayTasks = repo.observeForDate(isoDate).first()
            val templates = repo.observeRecurringTemplates().first()
            TaskOccurrences.expandForDate(dayTasks, templates, isoDate)
                .filter { !it.isDeleted }
        }

        // Масштабируемся под фактические размеры виджета (опции от лаунчера).
        val (heightDp, widthDp) = widgetSizeFor(context, appWidgetManager, appWidgetId)
        val showDate = widthDp >= DATE_MIN_WIDTH_DP
        views.setViewVisibility(R.id.widget_date, if (showDate) View.VISIBLE else View.GONE)

        // Расстояние между строками фиксированное (2dp): каждая задача — одна строка
        // фиксированной высоты. Число показанных задач выбирается так, чтобы они
        // заполнили доступную высоту виджета целиком; остальные уходят в «ещё N».
        val metrics = context.resources.displayMetrics
        val linePx = textLineHeightPx(metrics, 13f)
        val marginPx = dpPx(metrics, 2f)
        // Отступы корня 14dp сверху + 14dp снизу, шапка 15sp, блок разделителя 10+1+6dp.
        val overheadPx = dpPx(metrics, 28f) + textLineHeightPx(metrics, 15f) + dpPx(metrics, 17f)
        // Строка «ещё N»: отступ 4dp сверху + текст 12sp.
        val morePx = dpPx(metrics, 4f) + textLineHeightPx(metrics, 12f)
        val availPx = (heightDp * metrics.density).roundToInt()
        val lines = IntArray(tasks.size) { 1 }
        val allFit = fitCount(lines, linePx, marginPx, overheadPx, availPx) == tasks.size
        val rowsToShow = if (allFit) {
            tasks.size
        } else {
            fitCount(lines, linePx, marginPx, overheadPx + morePx, availPx)
        }.coerceAtLeast(if (tasks.isEmpty()) 0 else 1)

        views.setTextViewText(R.id.widget_title, viewContext.getString(R.string.widget_label))
        if (showDate) {
            views.setTextViewText(
                R.id.widget_date,
                dateLocal.format(DateTimeFormatter.ofPattern("d MMMM", locale))
            )
        }

        val rowIds = listOf(
            R.id.widget_row1, R.id.widget_row2, R.id.widget_row3, R.id.widget_row4,
            R.id.widget_row5, R.id.widget_row6, R.id.widget_row7, R.id.widget_row8,
            R.id.widget_row9, R.id.widget_row10, R.id.widget_row11, R.id.widget_row12,
            R.id.widget_row13, R.id.widget_row14, R.id.widget_row15, R.id.widget_row16
        )
        val timeIds = listOf(
            R.id.widget_time1, R.id.widget_time2, R.id.widget_time3, R.id.widget_time4,
            R.id.widget_time5, R.id.widget_time6, R.id.widget_time7, R.id.widget_time8,
            R.id.widget_time9, R.id.widget_time10, R.id.widget_time11, R.id.widget_time12,
            R.id.widget_time13, R.id.widget_time14, R.id.widget_time15, R.id.widget_time16
        )
        val titleIds = listOf(
            R.id.widget_title1, R.id.widget_title2, R.id.widget_title3, R.id.widget_title4,
            R.id.widget_title5, R.id.widget_title6, R.id.widget_title7, R.id.widget_title8,
            R.id.widget_title9, R.id.widget_title10, R.id.widget_title11, R.id.widget_title12,
            R.id.widget_title13, R.id.widget_title14, R.id.widget_title15, R.id.widget_title16
        )
        val starIds = listOf(
            R.id.widget_star1, R.id.widget_star2, R.id.widget_star3, R.id.widget_star4,
            R.id.widget_star5, R.id.widget_star6, R.id.widget_star7, R.id.widget_star8,
            R.id.widget_star9, R.id.widget_star10, R.id.widget_star11, R.id.widget_star12,
            R.id.widget_star13, R.id.widget_star14, R.id.widget_star15, R.id.widget_star16
        )
        val completedColor = context.getColor(R.color.widget_completed)
        val normalColor = context.getColor(R.color.widget_text)
        val timeColor = context.getColor(R.color.widget_time)

        val visible = rowsToShow
        for (i in rowIds.indices) {
            val show = i < visible
            views.setViewVisibility(rowIds[i], if (show) View.VISIBLE else View.GONE)
            if (show) {
                val task = tasks[i]
                views.setTextViewText(
                    timeIds[i],
                    task.time?.let { formatTimeTo(it, locale) } ?: EMA_DASH
                )
                // Цвет задачи в виджете показываем оттенком времени: так он
                // читается и не мешает основному тексту.
                views.setTextColor(
                    timeIds[i],
                    if (!task.isCompleted) task.color ?: timeColor else timeColor
                )
                views.setTextViewText(titleIds[i], task.title.toWidgetLine())
                views.setInt(titleIds[i], "setPaintFlags",
                    if (task.isCompleted) STRIKE_THRU_FLAG else 0)
                views.setTextColor(titleIds[i],
                    if (task.isCompleted) completedColor else normalColor)
                views.setViewVisibility(starIds[i],
                    if (task.isImportant) View.VISIBLE else View.GONE)
            }
        }

        val overflow = tasks.size - visible
        if (overflow > 0) {
            views.setViewVisibility(R.id.widget_more, View.VISIBLE)
            views.setTextViewText(R.id.widget_more, viewContext.getString(R.string.widget_more, overflow))
        } else {
            views.setViewVisibility(R.id.widget_more, View.GONE)
        }

        if (tasks.isEmpty()) {
            views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
            views.setTextViewText(R.id.widget_empty, viewContext.getString(R.string.widget_empty))
        } else {
            views.setViewVisibility(R.id.widget_empty, View.GONE)
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pending)

        return views
    }

    /** Достаёт размеры виджета из опций лаунчера (в dp). */
    private fun widgetSizeFor(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ): Pair<Int, Int> {
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        // Берём максимальную границу: лаунчер может присылать min меньше фактической
        // высоты, а max точнее совпадает с выделенной виджету областью.
        val heightDp = maxOf(
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0),
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        )
        val widthDp = maxOf(
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0),
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)
        )
        return heightDp to widthDp
    }

    /** Приводит заголовок к одной строке: переносы и повторные пробелы схлопываются. */
    private fun String.toWidgetLine(): String = replace(Regex("\\s+"), " ").trim()

    /** Высота одной строки текста заданного размера (sp) в пикселях. */
    private fun textLineHeightPx(metrics: DisplayMetrics, sp: Float): Int {
        val fm = textPaintFor(metrics, sp).fontMetrics
        return Math.ceil((fm.bottom - fm.top).toDouble()).toInt()
    }

    /** Общая кисть для текста заданного размера (sp) в пикселях. */
    private fun textPaintFor(metrics: DisplayMetrics, sp: Float): TextPaint =
        TextPaint().apply { textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, metrics) }

    /** Переводит dp в пиксели. */
    private fun dpPx(metrics: DisplayMetrics, dp: Float): Int =
        Math.round(dp * metrics.density)

    /** Контекст и локаль согласно языку, выбранному в настройках приложения. */
    private fun localeContext(context: Context): Pair<Locale, Context> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lang = prefs.getString(KEY_LANGUAGE, "") ?: ""
        if (lang.isEmpty()) {
            // Язык «как в системе»: берём локаль из конфигурации, а не Locale.getDefault() —
            // иначе виджет отрисовал бы дату на языке, выбранном ранее вручную.
            val sysLocale = context.resources.configuration.locales[0]
            return sysLocale to context
        }
        val locale = Locale(lang)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return locale to context.createConfigurationContext(config)
    }

    private fun formatTimeTo(timeIso: String, locale: Locale): String =
        LocalTime.parse(timeIso, DateTimeFormatter.ofPattern("HH:mm"))
            .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))

    companion object {
        private const val EMA_DASH = "–"
        private const val PREFS_NAME = "diary_settings"
        private const val KEY_LANGUAGE = "language"
        private const val STRIKE_THRU_FLAG = android.graphics.Paint.STRIKE_THRU_TEXT_FLAG

        /** Минимальная ширина (dp), при которой в шапке виджета показывается дата. */
        private const val DATE_MIN_WIDTH_DP = 150

        /** Максимум строк задач в виджете. */
        internal const val MAX_ROWS = 16

        /**
         * Сколько задач помещается в свободную высоту виджета (px) с учётом того,
         * что каждая задача занимает `lineCounts[i]` строк текста высоты `linePx`
         * плюс отступ `marginPx` между задачами. Возвращает число целых задач,
         * которые полностью помещаются (задачи не обрезаются посередине).
         * @param overheadPx высота фиксированных элементов (px) до первой задачи.
         */
        internal fun fitCount(
            lineCounts: IntArray,
            linePx: Int,
            marginPx: Int,
            overheadPx: Int,
            availPx: Int
        ): Int {
            var used = overheadPx
            var count = 0
            for (lines in lineCounts) {
                used += lines * linePx + marginPx
                if (used > availPx) break
                count++
                if (count >= MAX_ROWS) break
            }
            return count
        }

        /** Принудительно перерисовывает все размещённые виджеты (если они есть). */
        fun refresh(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val component = ComponentName(app, TasksWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return
            TasksWidgetProvider().onUpdate(app, manager, ids)
        }
    }
}