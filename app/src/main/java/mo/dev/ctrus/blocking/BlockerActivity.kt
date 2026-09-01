package mo.dev.ctrus.blocking

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import mo.dev.ctrus.theme.CtrusTheme
import mo.dev.ctrus.theme.ThemeManager

/**
 * Full-screen block surface shown in place of a blocked app, mirroring
 * ShieldConfigurationExtension.swift: solid theme-color background, a 🔒 icon, and one of 6
 * citrus-themed messages chosen deterministically per app per day (same FNV-1a-hash-of-name
 * XOR day-key scheme as [getFunBlockMessage] on iOS) — title/subtitle/button travel together.
 */
class BlockerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        val appLabel = packageName
            ?.let { runCatching { packageManager.getApplicationInfo(it, 0) }.getOrNull() }
            ?.let { packageManager.getApplicationLabel(it).toString() }
        val themeManager = ThemeManager.getInstance(applicationContext)

        // Matches iOS's shield: its dismiss button doesn't reveal the app underneath — it
        // returns to the home screen. Android can't intercept the blocked app before it opens
        // (unlike iOS's ManagedSettings), so its task is still alive behind this activity;
        // going home on dismiss (instead of just finishing back into it) keeps that invisible.
        val goHome = {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })

        setContent {
            CtrusTheme(themeManager) {
                BlockerScreen(
                    appLabel = appLabel ?: stringResource(R.string.blocker_app_fallback),
                    variantIndex = variantIndex(appLabel ?: packageName.orEmpty()),
                    onDismiss = goHome,
                )
            }
        }
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_PROFILE_NAME = "profile_name"

        /** FNV-1a 64-bit over UTF-16 code units, mirroring stableSeed(for:) in Swift. */
        private fun stableSeed(name: String): Long {
            var hash = -3750763034362895579L // 14695981039346656037 as signed Long
            for (unit in name) {
                hash = hash xor unit.code.toLong()
                hash *= 1099511628211L
            }
            return hash
        }

        private fun variantIndex(name: String): Int {
            val today = java.time.LocalDate.now()
            val dayKey = today.year * 10_000 + today.monthValue * 100 + today.dayOfMonth
            val seed = stableSeed(name) xor dayKey.toLong()
            return (kotlin.math.abs(seed) % 6).toInt()
        }
    }
}

@Composable
private fun BlockerScreen(appLabel: String, variantIndex: Int, onDismiss: () -> Unit) {
    val titles = stringArrayResource(R.array.shield_titles)
    val subtitles = stringArrayResource(R.array.shield_subtitles)
    val buttons = stringArrayResource(R.array.shield_buttons)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary)
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
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(buttons[variantIndex])
        }
    }
}
