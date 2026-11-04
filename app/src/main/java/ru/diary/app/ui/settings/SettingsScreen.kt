package ru.diary.app.ui.settings

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Star

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import ru.diary.app.R
import ru.diary.app.di.AppContainer
import ru.diary.app.util.openAppInRuStore
import ru.diary.app.util.restartApp
import ru.diary.app.widget.TasksWidgetProvider
import ru.rustore.sdk.review.RuStoreReviewManagerFactory
import ru.diary.app.data.settings.ReminderSounds
import ru.diary.app.data.settings.SettingsManager
import ru.diary.app.data.settings.ThemePresets
import ru.diary.app.notifications.NotificationHelper

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settingsManager: SettingsManager,
    container: AppContainer,
    onOpenPrivacyPolicy: () -> Unit = {}
) {
    val config by settingsManager.config.collectAsState()
    val reminderSound by settingsManager.reminderSound.collectAsState()
    val context = LocalContext.current
    var widgetCount by remember { mutableStateOf(placedWidgetCount(context)) }
    val scope = rememberCoroutineScope()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            AppearanceSection(
                isDarkMode = config.isDarkMode,
                onDarkModeChange = { settingsManager.setDarkMode(it) },
                selectedAccentKey = config.accentColorKey,
                onAccentSelect = { settingsManager.setAccentColor(it) }
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.settings_language),
                style = MaterialTheme.typography.titleSmall.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            LanguagePicker(
                selectedCode = config.languageCode,
                onSelect = { code ->
                    settingsManager.setLanguage(code)
                    (context as ComponentActivity).recreate()
                }
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            ReminderSoundSection(
                value = reminderSound,
                onSelect = { picked ->
                    settingsManager.setReminderSound(picked)
                    // У канала уведомлений звук фиксируется при создании,
                    // поэтому после смены мелодии пересоздаём канал.
                    NotificationHelper.createChannel(context)
                }
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.settings_widget),
                style = MaterialTheme.typography.titleSmall.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            SettingsHint(
                text = if (widgetCount > 0) stringResource(R.string.settings_widget_added)
                else stringResource(R.string.settings_widget_not_added)
            )
            Spacer(Modifier.height(8.dp))
            WidgetAddButton(onPlaced = {
                scope.launch {
                    delay(1200)
                    widgetCount = placedWidgetCount(context)
                }
            })
            Spacer(Modifier.height(8.dp))
            SettingsHint(text = stringResource(R.string.settings_widget_remove_hint))

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.settings_rate),
                style = MaterialTheme.typography.titleSmall.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            SettingsHint(text = stringResource(R.string.settings_rate_hint))
            Spacer(Modifier.height(8.dp))
            RateStars()

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.settings_privacy_policy),
                style = MaterialTheme.typography.titleSmall.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            SettingsHint(text = stringResource(R.string.settings_privacy_policy_hint))
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onOpenPrivacyPolicy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.settings_privacy_policy_open))
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
 private fun RateStars() {
     val context = LocalContext.current
     val reviewManager = remember { RuStoreReviewManagerFactory.create(context) }
       Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
           repeat(5) {
               Icon(
                   imageVector = Icons.Outlined.Star,
                   contentDescription = stringResource(R.string.settings_rate),
                   tint = MaterialTheme.colorScheme.primary,
                   modifier = Modifier
                       .size(44.dp)
                        .clickable {
                             reviewManager.requestReviewFlow()
                                 .addOnSuccessListener { info ->
                                     reviewManager.launchReviewFlow(info)
                                         .addOnFailureListener { openAppInRuStore(context) }
                                 }
                                 .addOnFailureListener { openAppInRuStore(context) }
                        }
                )
           }
       }
   }

 @Composable
 private fun WidgetAddButton(onPlaced: () -> Unit) {
     val context = LocalContext.current
     Button(onClick = {
         if (requestWidgetPin(context)) onPlaced()
     }) {
         Text(stringResource(R.string.settings_widget_add))
     }
 }

 /**
  * Подпись-подсказка с фиксированной высотой в две строки: размер строки
  * одинаков на всех языках независимо от длины перевода, а весь текст помещается.
  */
 @Composable
 private fun SettingsHint(text: String) {
     val style = MaterialTheme.typography.bodySmall
     val twoLinesHeight = with(LocalDensity.current) { (style.lineHeight * 2f).toDp() }
     Text(
         text = text,
         style = style,
         color = MaterialTheme.colorScheme.onSurfaceVariant,
modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = twoLinesHeight)
     )
 }

 private fun requestWidgetPin(context: Context): Boolean {
     val manager = AppWidgetManager.getInstance(context)
     val component = ComponentName(context, TasksWidgetProvider::class.java)
     val accepted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
         manager.requestPinAppWidget(component, null, null)
     } else {
         false
     }
     if (!accepted) {
         Toast.makeText(context, context.getString(R.string.settings_widget_fallback), Toast.LENGTH_LONG).show()
     }
     return accepted
 }
 
 /** Количество размещённых на рабочем столе виджетов текущего провайдера. */
 private fun placedWidgetCount(context: Context): Int =
     AppWidgetManager.getInstance(context)
         .getAppWidgetIds(ComponentName(context, TasksWidgetProvider::class.java))
         .size

  private val languageOptions = listOf(
      "" to R.string.settings_language_system,
      "en" to R.string.lang_en,
      "ru" to R.string.lang_ru,
      "es" to R.string.lang_es,
      "de" to R.string.lang_de,
      "ja" to R.string.lang_ja,
      "fr" to R.string.lang_fr,
      "pt" to R.string.lang_pt,
      "it" to R.string.lang_it,
      "nl" to R.string.lang_nl,
      "pl" to R.string.lang_pl,
      "tr" to R.string.lang_tr,
      "zh" to R.string.lang_zh
  )

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  private fun LanguagePicker(selectedCode: String, onSelect: (String) -> Unit) {
      var expanded by remember { mutableStateOf(false) }
      val selectedLabel = languageOptions.firstOrNull { it.first == selectedCode }?.let { (_, res) ->
          stringResource(res)
      } ?: stringResource(R.string.settings_language_system)

      ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
          OutlinedTextField(
              value = selectedLabel,
              onValueChange = {},
              readOnly = true,
              trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
              modifier = Modifier.menuAnchor(
                  androidx.compose.material3.MenuAnchorType.PrimaryNotEditable
              ).fillMaxWidth(),
              singleLine = true
          )
          ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
              languageOptions.forEach { (code, labelRes) ->
                  DropdownMenuItem(
                      text = { Text(stringResource(labelRes)) },
                      onClick = {
                          if (code != selectedCode) onSelect(code)
                          expanded = false
                      }
                  )
              }
          }
      }
  }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderSoundSection(
    value: String,
    onSelect: (String) -> Unit
) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val data = result.data
        @Suppress("DEPRECATION")
        val picked = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        } else {
            data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }
        val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val stored = when {
            picked == null -> ReminderSounds.SILENT
            defaultUri != null && picked.toString() == defaultUri.toString() -> ReminderSounds.DEFAULT
            else -> ReminderSounds.storedValue(picked.toString())
        }
        if (picked != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    picked,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
        onSelect(stored)
    }

    fun openPicker() {
        val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val existing = when (ReminderSounds.kindOf(value)) {
            ReminderSounds.Kind.DEFAULT -> defaultUri
            ReminderSounds.Kind.SILENT -> null
            ReminderSounds.Kind.CUSTOM -> runCatching { Uri.parse(value) }.getOrNull()
        }
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_TITLE,
                context.getString(R.string.settings_reminder_sound)
            )
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, defaultUri)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            if (existing != null) {
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
            }
        }
        try {
            picker.launch(intent)
        } catch (_: Exception) {
            Toast.makeText(
                context,
                context.getString(R.string.settings_reminder_sound_unavailable),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val currentTitle = when (ReminderSounds.kindOf(value)) {
        ReminderSounds.Kind.DEFAULT -> stringResource(R.string.settings_reminder_sound_default)
        ReminderSounds.Kind.SILENT -> stringResource(R.string.settings_reminder_sound_silent)
        ReminderSounds.Kind.CUSTOM -> runCatching {
            Uri.parse(value).let { uri ->
                RingtoneManager.getRingtone(context, uri)?.getTitle(context)
            }
        }.getOrNull().orEmpty().ifBlank {
            stringResource(R.string.settings_reminder_sound_default)
        }
    }

    Text(
        text = stringResource(R.string.settings_reminders),
        style = MaterialTheme.typography.titleSmall.copy(
            fontSize = MaterialTheme.typography.titleMedium.fontSize
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(6.dp))
    SettingsHint(text = stringResource(R.string.settings_reminder_sound_hint))
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = ReminderSounds.kindOf(value) == ReminderSounds.Kind.DEFAULT,
            onClick = { onSelect(ReminderSounds.DEFAULT) },
            label = { Text(stringResource(R.string.settings_reminder_sound_default)) }
        )
        FilterChip(
            selected = ReminderSounds.kindOf(value) == ReminderSounds.Kind.SILENT,
            onClick = { onSelect(ReminderSounds.SILENT) },
            label = { Text(stringResource(R.string.settings_reminder_sound_silent)) }
        )
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        onClick = { openPicker() },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.settings_reminder_sound_pick))
    }
    Spacer(Modifier.height(6.dp))
    Text(
        text = stringResource(R.string.settings_reminder_sound_current, currentTitle),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AppearanceSection(
    isDarkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    selectedAccentKey: String,
    onAccentSelect: (String) -> Unit
) {
    var sectionExpanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { sectionExpanded = !sectionExpanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.settings_appearance),
                style = MaterialTheme.typography.titleSmall.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                imageVector = if (sectionExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AnimatedVisibility(visible = sectionExpanded) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.settings_dark_mode))
                    Switch(checked = isDarkMode, onCheckedChange = onDarkModeChange)
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.settings_accent_color))
                Spacer(Modifier.height(8.dp))
                AccentColorPicker(
                    selectedAccentKey = selectedAccentKey,
                    isDarkMode = isDarkMode,
                    onAccentSelect = onAccentSelect
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentColorPicker(
    selectedAccentKey: String,
    isDarkMode: Boolean,
    onAccentSelect: (String) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ThemePresets.accentColors.forEach { option ->
            val color = Color(if (isDarkMode) option.primaryDark else option.primaryLight)
            val selected = option.key == selectedAccentKey
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape
                    )
                    .clickable(enabled = !selected) { onAccentSelect(option.key) },
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    val checkColor = if (color.luminance() > 0.5f) Color.Black else Color.White
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = checkColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

