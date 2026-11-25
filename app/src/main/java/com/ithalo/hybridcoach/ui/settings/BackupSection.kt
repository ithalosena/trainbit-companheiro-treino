package com.ithalo.hybridcoach.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ithalo.hybridcoach.R
import com.ithalo.hybridcoach.data.BackupException
import com.ithalo.hybridcoach.data.BackupPreview
import com.ithalo.hybridcoach.ui.common.HelpText
import com.ithalo.hybridcoach.ui.common.SectionTitle
import com.ithalo.hybridcoach.ui.common.appContainer
import com.ithalo.hybridcoach.ui.retro.PixelIcon
import com.ithalo.hybridcoach.ui.retro.RetroButton
import com.ithalo.hybridcoach.ui.retro.RetroDialog
import com.ithalo.hybridcoach.ui.retro.RetroIconKind
import com.ithalo.hybridcoach.ui.retro.RetroOutlinedButton
import com.ithalo.hybridcoach.ui.theme.Retro
import kotlinx.coroutines.launch

/**
 * Ajustes > Dados > Backup: saves everything to a `.zip` the user picks (Drive, Downloads...) and restores from one.
 * A restore shows what is inside before replacing anything, keeps the current database aside and restarts the app.
 */
@Composable
fun BackupSection() {
    val backup = appContainer().backup
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<BackupPreview?>(null) }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            message = if (runCatching { backup.export(uri) }.isSuccess) R.string.backup_saved else R.string.backup_save_failed
            busy = false
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            try {
                preview = backup.inspect(uri)
                message = null
            } catch (e: BackupException) {
                message = when (e.reason) {
                    BackupException.Reason.NEWER_APP -> R.string.backup_newer
                    BackupException.Reason.CORRUPT -> R.string.backup_corrupt
                    BackupException.Reason.NOT_A_BACKUP -> R.string.backup_not_a_backup
                }
            }
            busy = false
        }
    }

    SectionTitle(stringResource(R.string.backup_title))
    HelpText(stringResource(R.string.backup_help))
    RetroButton(onClick = { save.launch(backup.suggestedName()) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
        PixelIcon("floppy", size = 16.dp)
        Text(stringResource(R.string.backup_save))
    }
    RetroOutlinedButton(onClick = { pick.launch(arrayOf("application/zip", "application/octet-stream")) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
        PixelIcon("folder", size = 16.dp)
        Text(stringResource(R.string.backup_restore))
    }
    message?.let { Text(stringResource(it), style = Retro.type.bodySmall) }

    preview?.let { p ->
        RetroDialog(
            onDismissRequest = { preview = null },
            title = stringResource(R.string.backup_restore),
            icon = RetroIconKind.WARNING,
            text = {
                Text(stringResource(R.string.backup_confirm, p.exportedOn, p.appVersion, p.blocks, p.sets, p.runs))
            },
            confirmButton = {
                RetroButton(onClick = {
                    preview = null
                    busy = true
                    scope.launch { backup.restore() }
                }) { Text(stringResource(R.string.backup_restore_confirm)) }
            },
            dismissButton = {
                RetroOutlinedButton(onClick = { preview = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
