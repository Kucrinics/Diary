package ru.diary.app.notifications

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.diary.app.MainActivity
import ru.diary.app.R
import ru.diary.app.ui.theme.DiaryTheme

/**
 * Полноэкранное напоминание. Показывается по full-screen intent,
 * когда уведомление пришло при выключенном/заблокированном экране.
 */
class ReminderFullScreenActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // На Android < 8.1 атрибуты манифеста showWhenLocked/turnScreenOn не работают,
        // поэтому включаем экран вручную.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        val taskId = intent.getIntExtra(NotificationHelper.EXTRA_ID, -1)
        val title = intent.getStringExtra(NotificationHelper.EXTRA_TITLE)
            ?: getString(R.string.notif_label)

        // Если активность показана напрямую (без уведомления) — звучим сами.
        ReminderSoundPlayer.start(this, taskId)

        enableEdgeToEdge()
        setContent {
            DiaryTheme {
                ReminderScreen(
                    title = title,
                    label = getString(R.string.notif_label),
                    dismissLabel = getString(R.string.notif_dismiss),
                    openLabel = getString(R.string.notif_open),
                    onOpen = { openApp(taskId) },
                    onDismiss = { dismiss(taskId) }
                )
            }
        }
    }

    private fun openApp(taskId: Int) {
        ReminderSoundPlayer.stop(taskId)
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
        finish()
    }

    /** Останавливает звук и закрывает окно, но НЕ отменяет уведомление —
     *  запись остаётся в шторке уведомлений до тапа или свайпа. */
    private fun dismiss(taskId: Int) {
        ReminderSoundPlayer.stop(taskId)
        finish()
    }

    override fun onDestroy() {
        val taskId = intent.getIntExtra(NotificationHelper.EXTRA_ID, -1)
        ReminderSoundPlayer.stop(taskId)
        super.onDestroy()
    }

    companion object {
        fun newIntent(context: Context, taskId: Int, title: String): Intent =
            Intent(context, ReminderFullScreenActivity::class.java)
                .putExtra(NotificationHelper.EXTRA_ID, taskId)
                .putExtra(NotificationHelper.EXTRA_TITLE, title)
    }
}

@Composable
private fun ReminderScreen(
    title: String,
    label: String,
    dismissLabel: String,
    openLabel: String,
    onOpen: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 32.dp)
        ) {
            // Верхняя часть: заголовок по центру свободного места.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            // Нижняя часть: всегда место для кнопок.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(dismissLabel)
                }
                Button(
                    onClick = onOpen,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(openLabel)
                }
            }
        }
    }
}