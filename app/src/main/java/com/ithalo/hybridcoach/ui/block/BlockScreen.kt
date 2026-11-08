package com.ithalo.hybridcoach.ui.block

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ithalo.hybridcoach.R
import com.ithalo.hybridcoach.ui.common.MenuRow
import com.ithalo.hybridcoach.ui.common.ScreenColumn
import com.ithalo.hybridcoach.ui.common.ScreenScaffold
import com.ithalo.hybridcoach.ui.common.SectionTitle
import com.ithalo.hybridcoach.ui.theme.Retro

/**
 * Block hub, as a plain grouped menu: the three steps that create a new block (numbered, step 1
 * highlighted), then follow-up tools. Opened from the Plano tab.
 */
@Composable
fun BlockScreen(onBack: () -> Unit, onQuestionnaire: () -> Unit, onPrompt: () -> Unit, onImport: () -> Unit, onExport: () -> Unit, onHistory: () -> Unit) {
    ScreenScaffold(stringResource(R.string.program_block), onBack) {
        ScreenColumn {
            Text(stringResource(R.string.block_intro), style = Retro.type.body, color = Retro.colors.inkSoft)

            SectionTitle(stringResource(R.string.block_group_new))
            MenuRow("list", stringResource(R.string.block_questionnaire), stringResource(R.string.block_questionnaire_desc), step = 1, highlight = true, onClick = onQuestionnaire)
            MenuRow("lightning", stringResource(R.string.block_prompt), stringResource(R.string.block_prompt_desc), step = 2, onClick = onPrompt)
            MenuRow("floppy", stringResource(R.string.block_import), stringResource(R.string.block_import_desc), step = 3, onClick = onImport)

            SectionTitle(stringResource(R.string.block_group_track))
            MenuRow("folder", stringResource(R.string.block_export), stringResource(R.string.block_export_desc), onClick = onExport)
            MenuRow("chart", stringResource(R.string.block_history), stringResource(R.string.block_history_desc), onClick = onHistory)

            Text(stringResource(R.string.disclaimer), style = Retro.type.bodySmall, color = Retro.colors.inkSoft)
        }
    }
}
