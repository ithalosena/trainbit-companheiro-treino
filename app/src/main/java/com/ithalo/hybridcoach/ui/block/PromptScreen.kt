package com.ithalo.hybridcoach.ui.block

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.ithalo.hybridcoach.core.prompt.PromptBuilder
import com.ithalo.hybridcoach.core.questionnaire.IssueSeverity
import com.ithalo.hybridcoach.core.questionnaire.QuestionnaireIssue
import com.ithalo.hybridcoach.core.questionnaire.QuestionnaireRules
import com.ithalo.hybridcoach.data.PlanRepository
import com.ithalo.hybridcoach.ui.common.InfoCard
import com.ithalo.hybridcoach.ui.common.ScreenColumn
import com.ithalo.hybridcoach.ui.common.ScreenScaffold
import com.ithalo.hybridcoach.ui.common.appViewModel
import com.ithalo.hybridcoach.ui.common.issueText
import com.ithalo.hybridcoach.ui.common.shareText
import com.ithalo.hybridcoach.ui.common.terminalFrame
import com.ithalo.hybridcoach.ui.retro.BlinkingCursor
import com.ithalo.hybridcoach.ui.retro.HourglassSpinner
import com.ithalo.hybridcoach.ui.retro.PixelIcon
import com.ithalo.hybridcoach.ui.retro.RetroButton
import com.ithalo.hybridcoach.ui.retro.RetroOutlinedButton
import com.ithalo.hybridcoach.ui.retro.TypewriterText
import com.ithalo.hybridcoach.ui.theme.Retro
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface PromptState {
    data object Loading : PromptState
    data object NoAnswers : PromptState
    data class Ready(val prompt: String, val issues: List<QuestionnaireIssue>) : PromptState
}

class PromptViewModel(repository: PlanRepository) : ViewModel() {
    private val _state = MutableStateFlow<PromptState>(PromptState.Loading)
    val state: StateFlow<PromptState> = _state

    init {
        viewModelScope.launch {
            val answers = repository.loadQuestionnaire()
            _state.value = if (answers == null) {
                PromptState.NoAnswers
            } else {
                val builder = PromptBuilder(repository.asset("prompt_template.md"), repository.asset("example_plan.json"))
                PromptState.Ready(builder.build(answers, repository.latestSummaryText()), QuestionnaireRules.check(answers))
            }
        }
    }
}

@Composable
fun PromptScreen(onBack: () -> Unit, onImport: () -> Unit, onQuestionnaire: () -> Unit) {
    val vm = appViewModel { PromptViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.copied)
    val shareTitle = stringResource(R.string.prompt_share_title)

    ScreenScaffold(stringResource(R.string.prompt_program), onBack, snackbar) {
        ScreenColumn {
            when (val s = state) {
                PromptState.Loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HourglassSpinner()
                    Text(stringResource(R.string.retro_sc_loading), style = Retro.type.mono)
                }
                PromptState.NoAnswers -> {
                    InfoCard(stringResource(R.string.prompt_no_answers))
                    RetroButton(onClick = onQuestionnaire) { Text(stringResource(R.string.block_questionnaire)) }
                }
                is PromptState.Ready -> {
                    InfoCard(stringResource(R.string.prompt_external_warning), isError = true)
                    s.issues.filter { it.severity != IssueSeverity.INFO }.forEach { InfoCard(issueText(it)) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        RetroButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(s.prompt))
                                scope.launch { snackbar.showSnackbar(copiedMessage) }
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.action_copy)) }
                        RetroOutlinedButton(onClick = { shareText(context, s.prompt, shareTitle) }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.action_share))
                        }
                    }
                    Text(stringResource(R.string.prompt_next_step), style = Retro.type.body)
                    RetroOutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                        PixelIcon("floppy", size = 16.dp)
                        Text(stringResource(R.string.prompt_go_import))
                    }
                    TerminalBox(
                        header = stringResource(R.string.prompt_terminal_header, s.prompt.length),
                        text = s.prompt,
                    )
                }
            }
        }
    }
}

/** Monospace "terminal" panel: typed command line with a blinking cursor, then selectable text. */
@Composable
fun TerminalBox(header: String, text: String) {
    val c = Retro.colors
    var typed by remember(header) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .terminalFrame(c),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TypewriterText(header, style = Retro.type.mono, color = c.accent2, charDelayMs = 25, onDone = { typed = true })
            if (typed) BlinkingCursor(color = c.accent2)
        }
        SelectionContainer {
            Text(text, style = Retro.type.mono.copy(fontSize = Retro.type.bodySmall.fontSize, lineHeight = Retro.type.bodySmall.lineHeight), color = c.ink)
        }
    }
}
