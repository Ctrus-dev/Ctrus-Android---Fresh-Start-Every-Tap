package mo.dev.ctrus.ui.intro

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EscalatorWarning
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import mo.dev.ctrus.R
import mo.dev.ctrus.childmode.AppMode
import mo.dev.ctrus.theme.pastelBackground
import mo.dev.ctrus.ui.common.IntroCard
import mo.dev.ctrus.ui.dashboard.DefaultModel3DSize
import mo.dev.ctrus.ui.dashboard.RotatingModel3DView

/**
 * Port of iOS's ModeChoiceIntroScreen (spec Part B, 5.1): after the permissions, is this the
 * person's own phone, or a child's? Two cards, no title above them.
 */
@Composable
fun ModeChoiceScreen(themeColor: Color, onChoose: (AppMode) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pastelBackground(themeColor))
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        RotatingModel3DView(
            themeColor = themeColor,
            modifier = Modifier
                .size(DefaultModel3DSize)
                .align(Alignment.CenterHorizontally)
                .padding(top = 16.dp),
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IntroCard(
                title = stringResource(R.string.mode_normal_title),
                subtitle = stringResource(R.string.mode_normal_subtitle),
                icon = Icons.Filled.Person,
                themeColor = themeColor,
                onClick = { onChoose(AppMode.NORMAL) },
            )
            IntroCard(
                title = stringResource(R.string.mode_child_title),
                subtitle = stringResource(R.string.mode_child_subtitle),
                icon = Icons.Filled.EscalatorWarning,
                themeColor = themeColor,
                onClick = { onChoose(AppMode.CHILD) },
            )
        }
    }
}
