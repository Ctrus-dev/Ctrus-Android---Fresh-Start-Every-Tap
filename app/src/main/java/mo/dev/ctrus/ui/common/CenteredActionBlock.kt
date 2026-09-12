package mo.dev.ctrus.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import mo.dev.ctrus.theme.FixedLightPrimaryText
import mo.dev.ctrus.theme.FixedLightSecondaryText

/**
 * Title + subtitle + full-width filled button, centered — the shape both the empty Home screen
 * (Welcome.swift) and the required-permissions screen (PermissionsIntroScreen.swift) use below
 * the 3D mascot. Shared here (rather than duplicated per screen) so the two stay pixel-identical
 * in position, matching iOS's own intent for the two to land in "exactly the same spot".
 */
@Composable
fun CenteredActionBlock(
    title: String,
    subtitle: String,
    buttonText: String,
    themeColor: Color,
    onButtonClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonIcon: ImageVector? = null,
    aboveButtonContent: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = FixedLightPrimaryText)
        Spacer(Modifier.height(10.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = FixedLightSecondaryText,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        aboveButtonContent()
        Button(
            onClick = onButtonClick,
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = themeColor, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            if (buttonIcon != null) {
                Icon(buttonIcon, contentDescription = null)
                Spacer(Modifier.width(8.dp))
            }
            Text(buttonText)
        }
    }
}
