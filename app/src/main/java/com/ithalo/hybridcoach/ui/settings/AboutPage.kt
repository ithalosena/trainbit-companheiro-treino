package com.ithalo.hybridcoach.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ithalo.hybridcoach.R
import com.ithalo.hybridcoach.ui.common.HelpText
import com.ithalo.hybridcoach.ui.common.InfoCard
import com.ithalo.hybridcoach.ui.common.SectionTitle
import com.ithalo.hybridcoach.ui.common.appContainer
import com.ithalo.hybridcoach.ui.retro.RetroOutlinedButton
import com.ithalo.hybridcoach.ui.theme.Retro

private val FONT_LICENSES = listOf(
    "DepartureMono" to "licenses/DepartureMono-OFL.txt",
    "DotGothic16" to "licenses/DotGothic16-OFL.txt",
    "Doto" to "licenses/Doto-OFL.txt",
)

/**
 * Ajustes > Sobre: version, the health notice, what is kept (nothing leaves the phone), credits and the licenses
 * of the fonts (the OFL asks for its text to travel with the app).
 */
@Composable
fun AboutPage() {
    val context = LocalContext.current
    val repository = appContainer().repository
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    var openLicense by rememberSaveable { mutableStateOf<String?>(null) }
    Text(stringResource(R.string.product_name), style = Retro.type.title)
    Text(stringResource(R.string.about_version, version), style = Retro.type.body, color = Retro.colors.inkSoft)
    SectionTitle(stringResource(R.string.about_health))
    InfoCard(stringResource(R.string.disclaimer))
    SectionTitle(stringResource(R.string.about_privacy))
    Text(stringResource(R.string.about_privacy_body), style = Retro.type.body)
    SectionTitle(stringResource(R.string.about_credits))
    Text(stringResource(R.string.about_credits_body), style = Retro.type.body)
    SectionTitle(stringResource(R.string.about_licenses))
    HelpText(stringResource(R.string.about_licenses_help))
    FONT_LICENSES.forEach { (name, path) ->
        RetroOutlinedButton(
            onClick = { openLicense = if (openLicense == path) null else path },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(name) }
        if (openLicense == path) {
            val text = remember(path) { runCatching { repository.asset(path) }.getOrDefault("") }
            Text(text, style = Retro.type.bodySmall, color = Retro.colors.inkSoft)
        }
    }
}
