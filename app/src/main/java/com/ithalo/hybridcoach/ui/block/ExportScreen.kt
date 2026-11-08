package com.ithalo.hybridcoach.ui.block

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ithalo.hybridcoach.R
import com.ithalo.hybridcoach.data.ExportedSummary
import com.ithalo.hybridcoach.data.PlanEntity
import com.ithalo.hybridcoach.data.PlanRepository
import com.ithalo.hybridcoach.ui.common.InfoCard
import com.ithalo.hybridcoach.ui.common.ScreenColumn
import com.ithalo.hybridcoach.ui.common.ScreenScaffold
import com.ithalo.hybridcoach.ui.common.appViewModel
import com.ithalo.hybridcoach.ui.common.shareText
import com.ithalo.hybridcoach.ui.retro.PixelIcon
import com.ithalo.hybridcoach.ui.retro.RetroButton
import com.ithalo.hybridcoach.ui.retro.RetroOutlinedButton
import com.ithalo.hybridcoach.ui.retro.RetroWindow
import com.ithalo.hybridcoach.ui.retro.pixelFloat
import com.ithalo.hybridcoach.ui.theme.Retro
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ExportState(val loaded: Boolean = false, val plan: PlanEntity? = null, val summary: ExportedSummary? = null)

class ExportViewModel(private val repository: PlanRepository) : ViewModel() {
    private val _state = MutableStateFlow(ExportState())
    val state: StateFlow<ExportState> = _state

    init {
        viewModelScope.launch {
            _state.value = ExportState(loaded = true, plan = repository.activePlan())
        }
    }

    fun generate() {
        val plan = _state.value.plan ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(summary = repository.exportSummary(plan))
        }
    }
}

@Composable
fun ExportScreen(onBack: () -> Unit) {
    val vm = appViewModel { ExportViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.copied)
    val shareTitle = stringResource(R.string.export_share_title)

    ScreenScaffold(stringResource(R.string.export_program), onBack, snackbar) {
        ScreenColumn {
            when {
                !state.loaded -> Unit
                state.plan == null -> InfoCard(stringResource(R.string.today_no_plan))
                else -> {
                    RetroWindow(stringResource(R.string.export_window), Modifier.fillMaxWidth()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                            PixelIcon("floppy", Modifier.pixelFloat(), tint = Retro.colors.accent2, size = 32.dp)
                            Column(Modifier.weight(1f)) {
                                Text(state.plan!!.name, style = Retro.type.heading)
                                Text(stringResource(R.string.export_explain), style = Retro.type.body)
                            }
                        }
                        RetroButton(onClick = vm::generate, modifier = Modifier.fillMaxWidth()) {
                            PixelIcon("floppy", size = 16.dp)
                            Text(stringResource(R.string.export_generate))
                        }
                    }
                    state.summary?.let { summary ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            RetroOutlinedButton(
                                onClick = { clipboard.setText(AnnotatedString(summary.text)); scope.launch { snackbar.showSnackbar(copiedMessage) } },
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(R.string.export_copy_text)) }
                            RetroOutlinedButton(
                                onClick = { clipboard.setText(AnnotatedString(summary.json)); scope.launch { snackbar.showSnackbar(copiedMessage) } },
                                modifier = Modifier.weight(1f),
                            ) { Text(stringResource(R.string.export_copy_json)) }
                        }
                        RetroOutlinedButton(onClick = { shareText(context, summary.text, shareTitle) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.action_share))
                        }
                        TerminalBox(header = stringResource(R.string.export_terminal_header, summary.text.length), text = summary.text)
                    }
                }
            }
        }
    }
}
