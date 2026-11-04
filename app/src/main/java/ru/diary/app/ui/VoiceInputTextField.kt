package ru.diary.app.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import ru.diary.app.MainActivity
import ru.diary.app.R
import java.util.Locale

@Composable
fun VoiceInputTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    placeholder: @Composable (() -> Unit)? = null
) {
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!matches.isNullOrEmpty()) {
                val recognized = matches[0]
                if (value.isBlank()) {
                    onValueChange(recognized)
                } else {
                    onValueChange("$value $recognized")
                }
            }
        } else if (result.resultCode == Activity.RESULT_CANCELED) {
            // Пользователь отменил распознавание
        }
    }

    fun startListening() {
        val mainActivity = context.findActivity() as? MainActivity ?: return
        mainActivity.requestAudioPermissionIfNeeded { granted ->
            if (granted) {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.voice_input))
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
                try {
                    isListening = true
                    speechLauncher.launch(intent)
                } catch (e: ActivityNotFoundException) {
                    isListening = false
                    Toast.makeText(
                        context,
                        context.getString(R.string.voice_unavailable),
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    isListening = false
                    Toast.makeText(
                        context,
                        context.getString(R.string.voice_unavailable),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                if (ActivityCompat.shouldShowRequestPermissionRationale(mainActivity, Manifest.permission.RECORD_AUDIO)) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.audio_permission_required),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        context.getString(R.string.audio_permission_required),
                        Toast.LENGTH_LONG
                    ).show()
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            }
        }
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = placeholder,
        singleLine = singleLine,
        modifier = modifier,
        trailingIcon = {
            IconButton(onClick = { startListening() }) {
                if (isListening) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = stringResource(R.string.voice_input),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    )
}

/**
 * Разворачивает цепочку [ContextWrapper], чтобы найти [Activity].
 * Компоузный контекст внутри `Dialog`/`AlertDialog` — это `ContextThemeWrapper`,
 * а не сама Activity, поэтому прямой каст `context as? MainActivity` не срабатывает.
 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
