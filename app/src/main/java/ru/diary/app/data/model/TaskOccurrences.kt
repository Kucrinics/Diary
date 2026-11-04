package ru.diary.app.data.model

/**
 * Объединение реальных задач дня с «виртуальными» вхождениями повторяющихся серий.
 *
 * Виртуальное вхождение не хранится в БД: у него отрицательный id
 * (`-(templateId + VIRTUAL_ID_OFFSET)`), а дата равна отображаемому дню.
 * Благодаря общему helper'у экран задач и виджет считают вхождения одинаково.
 */
object TaskOccurrences {

    /** Смещение, отделяющее виртуальные id от реальных (реальные id положительные). */
    const val VIRTUAL_ID_OFFSET = 1000L

    /** Виртуальный id вхождения для шаблона серии. */
    fun virtualId(templateId: Long): Long = -(templateId + VIRTUAL_ID_OFFSET)

    /** id шаблона по виртуальному id вхождения (null для реальных задач). */
    fun templateIdOf(taskId: Long): Long? =
        if (taskId < 0) -taskId - VIRTUAL_ID_OFFSET else null

    /**
     * Формирует список задач на дату: реальные записи + виртуальные вхождения
     * активных шаблонов. Реальная материализованная копия вхождения имеет
     * приоритет над виртуальной, поэтому дубликат не создаётся.
     */
    fun expandForDate(
        dateTasks: List<Task>,
        templates: List<Task>,
        date: String
    ): List<Task> {
        val originIdsOnDate = dateTasks.mapNotNull { it.repeatOriginId }.toSet()
        val real = dateTasks
            .filter { it.id !in originIdsOnDate }
            .filter {
                it.repeatOriginId != null ||
                    it.repeatType == RepeatType.NONE.value ||
                    RecurrenceUtils.isActiveOnDate(it, date)
            }
        val virtual = templates.asSequence()
            .filter { it.id !in originIdsOnDate }
            .filter { it.date != date }
            .filter { RecurrenceUtils.isActiveOnDate(it, date) }
            .map { it.copy(id = virtualId(it.id), date = date) }
            .toList()
        return sortedForDisplay(real + virtual).distinctBy { it.id }
    }

    /** Порядок отображения: со временем — по времени, затем по sort_order и id. */
    fun sortedForDisplay(tasks: List<Task>): List<Task> =
        tasks.sortedWith(
            compareBy<Task> { it.time == null }
                .thenBy { it.time }
                .thenBy { it.sortOrder }
                .thenBy { it.id }
        )
}
