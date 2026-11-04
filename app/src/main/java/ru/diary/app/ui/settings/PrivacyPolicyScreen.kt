package ru.diary.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.diary.app.R

/** Заголовок раздела вида «1. Общие положения». */
private val SectionHeadingRegex = Regex("""^\d+\.\s+\S""")

/** Подзаголовок вида «2.1. Данные, создаваемые пользователем». */
private val SubHeadingRegex = Regex("""^\d+\.\d+""")

/** Символы, которыми начинается элемент списка. */
private val BulletChars = setOf('-', '–', '—', '•', '·')

/** Одна строка политики с уровнем отступа (в шагах по 12.dp). */
private data class PolicyLine(
    val text: String,
    val level: Int,
    val isHeading: Boolean
)

/**
 * Разбирает сплошной текст политики на строки и назначает каждой уровень
 * отступа: заголовок раздела — 0, его содержимое — 1, подпункты и их
 * содержимое — 2. Пустые строки становятся разделителями между абзацами.
 */
private fun parsePolicy(policy: String): List<PolicyLine> {
    val result = mutableListOf<PolicyLine>()
    var started = false
    var bodyLevel = 0

    for (raw in policy.split('\n')) {
        val line = raw.trim()
        when {
            line.isEmpty() -> result.add(PolicyLine("", 0, false))
            SubHeadingRegex.containsMatchIn(line) -> {
                started = true
                bodyLevel = 2
                result.add(PolicyLine(line, 1, true))
            }
            SectionHeadingRegex.containsMatchIn(line) -> {
                started = true
                bodyLevel = 1
                result.add(PolicyLine(line, 0, true))
            }
            else -> {
                // Обычный абзац или элемент списка — отступ по текущему пункту.
                result.add(PolicyLine(line, if (started) bodyLevel else 0, false))
            }
        }
    }
    return result
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(
    onBack: () -> Unit
) {
    val scrollState = rememberLazyListState()
    val policy = stringResource(id = R.string.privacy_policy_text)
    val lines = remember(policy) { parsePolicy(policy) }

    // Системная кнопка «Back» должна возвращать назад, а не закрывать приложение.
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.privacy_policy_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.privacy_back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            state = scrollState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            items(lines) { line ->
                if (line.text.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                } else {
                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (line.isHeading) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(start = (line.level * 12).dp)
                    )
                }
            }
        }
    }
}
