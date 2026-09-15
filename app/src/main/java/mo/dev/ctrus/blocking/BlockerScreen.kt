package mo.dev.ctrus.blocking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mo.dev.ctrus.R

/** FNV-1a 64-bit over UTF-16 code units, mirroring stableSeed(for:) in Swift. */
private fun stableSeed(name: String): Long {
    var hash = -3750763034362895579L // 14695981039346656037 as signed Long
    for (unit in name) {
        hash = hash xor unit.code.toLong()
        hash *= 1099511628211L
    }
    return hash
}

fun blockerScreenVariantIndex(name: String): Int {
    val today = java.time.LocalDate.now()
    val dayKey = today.year * 10_000 + today.monthValue * 100 + today.dayOfMonth
    val seed = stableSeed(name) xor dayKey.toLong()
    return (kotlin.math.abs(seed) % 8).toInt()
}

/**
 * Full-screen block surface shown in place of a blocked app, mirroring
 * ShieldConfigurationExtension.swift: solid theme-color background, a 🔒 icon, and one of 8
 * citrus-themed messages chosen deterministically per app per day (same FNV-1a-hash-of-name
 * XOR day-key scheme as [getFunBlockMessage] on iOS) — title/subtitle/button travel together.
 *
 * Hosted by [BlockerActivity].
 */
@Composable
fun BlockerScreen(appLabel: String, variantIndex: Int, onDismiss: () -> Unit) {
    val titles = stringArrayResource(R.array.shield_titles)
    val subtitles = stringArrayResource(R.array.shield_subtitles)
    val buttons = stringArrayResource(R.array.shield_buttons)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🔒", fontSize = 64.sp)
        Spacer(Modifier.height(20.dp))
        Text(
            titles[variantIndex],
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            subtitles[variantIndex].format(appLabel),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.88f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onDismiss,
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(buttons[variantIndex])
        }
    }
}
