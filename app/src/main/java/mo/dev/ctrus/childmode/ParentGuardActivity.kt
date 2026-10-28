package mo.dev.ctrus.childmode

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import mo.dev.ctrus.R
import mo.dev.ctrus.nfc.NfcScanController
import mo.dev.ctrus.theme.CtrusTheme
import mo.dev.ctrus.theme.ThemeManager
import mo.dev.ctrus.ui.childmode.ParentConfirmationContent

/**
 * Child Mode, during a session (spec Part B, section 10, [Android]): opening Ctrus's own
 * Accessibility page or its App info page in Settings shows the parent confirmation over it.
 * Confirming leaves the page usable until Settings is left; closing it goes to the home screen.
 * Outside a session nothing is guarded, so the app can always be uninstalled then.
 */
class ParentGuardActivity : ComponentActivity() {
    private lateinit var nfcScanController: NfcScanController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ParentGuard.isShowing = true
        nfcScanController = NfcScanController(this, lifecycleScope)
        val themeManager = ThemeManager.getInstance(applicationContext)

        setContent {
            CtrusTheme(themeManager) {
                BackHandler(onBack = ::leave)
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .windowInsetsPadding(WindowInsets.statusBars),
                ) {
                    ParentConfirmationContent(
                        reason = stringResource(R.string.parent_confirm_reason_settings),
                        nfcScanController = nfcScanController,
                        themeColor = themeManager.selectedColorOption.color,
                        onClose = ::leave,
                        onConfirmed = {
                            ParentGuard.isPassed = true
                            finish()
                        },
                    )
                }
            }
        }
    }

    private fun leave() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    override fun onResume() {
        super.onResume()
        nfcScanController.attach()
    }

    override fun onPause() {
        nfcScanController.detach()
        super.onPause()
    }

    override fun onDestroy() {
        ParentGuard.isShowing = false
        super.onDestroy()
    }
}

/** Shared by [ParentGuardActivity] and the accessibility service, both in the app's process. */
object ParentGuard {
    @Volatile var isShowing = false
    /** The parents confirmed; Ctrus's Settings pages stay open until Settings is left. */
    @Volatile var isPassed = false
}
