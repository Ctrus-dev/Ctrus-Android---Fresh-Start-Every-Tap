package mo.dev.ctrus.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import mo.dev.ctrus.theme.ThemeManager
import mo.dev.ctrus.theme.pastelBackground

// This screen has a light pastel background regardless of the app-wide dark
// theme used elsewhere (Settings, Active Session), so its text needs fixed
// dark colors instead of the (white) theme defaults meant for dark surfaces.
private val HomeOnPastel = Color(0xFF1C1C1E)
private val HomeOnPastelVariant = Color(0xFF6B6B70)

/**
 * Placeholder for the profile list / "Fresh Start" entry point (iOS HomeView.swift).
 * Real profile management (create/edit/list BlockedProfiles) is a follow-up port.
 */
@Composable
fun HomeScreen(
    themeManager: ThemeManager,
    onOpenSettings: () -> Unit,
    onStartFocus: () -> Unit,
) {
    val themeColor = themeManager.selectedColorOption.color

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pastelBackground(themeColor))
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = HomeOnPastel)
            }
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Ctrus",
                style = MaterialTheme.typography.headlineLarge,
                color = HomeOnPastel
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Fresh Start, Every Tap",
                style = MaterialTheme.typography.bodyLarge,
                color = HomeOnPastelVariant
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onStartFocus,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = themeColor,
                    contentColor = Color.White
                )
            ) {
                Text("Start Focus Session")
            }
        }
    }
}
