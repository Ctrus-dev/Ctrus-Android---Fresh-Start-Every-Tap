package mo.dev.ctrus.alarm

import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mo.dev.ctrus.CtrusApp
import mo.dev.ctrus.MainActivity
import mo.dev.ctrus.R
import mo.dev.ctrus.theme.CtrusTheme
import mo.dev.ctrus.theme.ThemeManager
import java.time.LocalTime
import kotlin.math.roundToInt

/**
 * The only screen Alarm mode adds (spec D5.2), shown over the lock screen by [AlarmService]'s
 * full-screen notification, laid out like the Pixel Clock's alarm and, like it, always dark: the
 * time, the profile's name, and a pill dragged left to snooze (when the profile allows it) or right
 * to open Ctrus. Opening asks to unlock the phone first if it's locked, then opens the app on the
 * profile, where reading the Ctrus starts by itself; the sound carries on until then. Leaving this screen any other way doesn't silence it, and [AlarmService]
 * brings it back a few seconds later.
 */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Always dark, so the bars always get light icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val app = CtrusApp.from(this)
        val themeManager = ThemeManager.getInstance(applicationContext)
        setContent {
            CtrusTheme(themeManager) {
                val records by WakeAlarmManager.records.collectAsState()
                val snoozedUntil by WakeAlarmManager.snoozedUntil.collectAsState()
                val now by produceState(System.currentTimeMillis()) {
                    while (true) {
                        delay(1_000)
                        value = System.currentTimeMillis()
                    }
                }
                val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
                    ?: records.entries.filter { it.value.alarmDate <= now }.maxByOrNull { it.value.alarmDate }?.key
                val record = profileId?.let { records[it] }
                val isSnoozed = (snoozedUntil ?: 0) > now

                // Nothing left to show once the alarm is over (the Ctrus was read in the app, or
                // it was snoozed from the notification).
                LaunchedEffect(record == null, isSnoozed) {
                    if (record == null || isSnoozed) finish()
                }

                AlarmScreen(
                    themeColor = themeManager.selectedColorOption.color,
                    profileName = record?.profileName.orEmpty(),
                    showsSnooze = record?.allowsSnooze == true,
                    onSnooze = {
                        profileId?.let { id ->
                            lifecycleScope.launch {
                                WakeAlarmManager.snooze(app, id)
                                finish()
                            }
                        }
                    },
                    onSlid = { openApp(profileId) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        isVisible = true
    }

    override fun onStop() {
        isVisible = false
        super.onStop()
    }

    private fun openApp(profileId: String?) {
        // Unlocking and opening Ctrus takes a moment, so the screen isn't brought back meanwhile.
        openingAppUntil = System.currentTimeMillis() + OPENING_APP_GRACE_MILLIS
        val open = {
            // From here MainActivity being on screen is what holds the alarm screen back.
            openingAppUntil = 0L
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(MainActivity.EXTRA_ALARM_PROFILE_ID, profileId),
            )
            finish()
        }
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard.isKeyguardLocked) {
            keyguard.requestDismissKeyguard(
                this,
                object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() = open()
                    // Unlocking was given up: the screen can come back.
                    override fun onDismissCancelled() { openingAppUntil = 0L }
                    override fun onDismissError() { openingAppUntil = 0L }
                },
            )
        } else {
            open()
        }
    }

    companion object {
        const val EXTRA_PROFILE_ID = "profile_id"
        private const val OPENING_APP_GRACE_MILLIS = 30_000L

        /** On screen; [AlarmService] brings it back when it isn't (and Ctrus isn't either). */
        @Volatile
        var isVisible = false
            private set

        /** Until when the slide is still opening Ctrus (unlocking first), so it isn't brought back. */
        @Volatile
        var openingAppUntil = 0L
            private set
    }
}

// The Pixel Clock's alarm screen (7.5), always dark.
private val AlarmBackground = Color(0xFF1B1C20)
private val AlarmTrack = Color(0xFF2A2D36)
private val AlarmText = Color(0xFFE3E2E6)

@Composable
private fun AlarmScreen(
    themeColor: Color,
    profileName: String,
    showsSnooze: Boolean,
    onSnooze: () -> Unit,
    onSlid: () -> Unit,
) {
    val context = LocalContext.current
    val time by produceState(LocalTime.now()) {
        while (true) {
            delay(1_000)
            value = LocalTime.now()
        }
    }
    // Like the Pixel Clock: "03:08" with no AM/PM in 12-hour mode, "15:08" in 24-hour mode.
    val timeText = time.format(
        java.time.format.DateTimeFormatter.ofPattern(if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "hh:mm"),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AlarmBackground)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1.25f))
        Text(
            timeText,
            color = AlarmText,
            fontSize = 120.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            profileName,
            color = AlarmText,
            fontSize = 24.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(0.9f))
        AlarmSlider(themeColor = themeColor, showsSnooze = showsSnooze, onSnooze = onSnooze, onOpen = onSlid)
        Spacer(Modifier.weight(0.35f))
    }
}

/**
 * The Pixel Clock's alarm control: a pill with the alarm icon in the middle of a track, dragged
 * left to snooze or right to open Ctrus (where reading the Ctrus stops it). Let go early and it
 * springs back. Without snooze, only the right side does anything.
 */
@Composable
private fun AlarmSlider(themeColor: Color, showsSnooze: Boolean, onSnooze: () -> Unit, onOpen: () -> Unit) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val density = LocalDensity.current
    val trackHeight = 92.dp
    val pillWidth = 112.dp
    val inset = 8.dp

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(trackHeight)
            .clip(CircleShape)
            .background(AlarmTrack),
        contentAlignment = Alignment.Center,
    ) {
        val maxOffset = with(density) { ((maxWidth - pillWidth) / 2 - inset).toPx() }.coerceAtLeast(0f)
        val minOffset = if (showsSnooze) -maxOffset else 0f
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (showsSnooze) SliderLabel(stringResource(R.string.alarm_snooze))
            }
            Spacer(Modifier.width(pillWidth))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                SliderLabel(stringResource(R.string.alarm_open_ctrus), textAlign = TextAlign.End)
            }
        }
        Box(
            modifier = Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .size(width = pillWidth, height = trackHeight - inset * 2)
                .clip(CircleShape)
                .background(themeColor)
                .pointerInput(maxOffset, minOffset) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                when {
                                    offset.value >= maxOffset * 0.85f -> {
                                        offset.animateTo(maxOffset)
                                        onOpen()
                                        offset.snapTo(0f)
                                    }
                                    showsSnooze && offset.value <= -maxOffset * 0.85f -> {
                                        offset.animateTo(-maxOffset)
                                        onSnooze()
                                    }
                                    else -> offset.animateTo(0f, spring())
                                }
                            }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f, spring()) } },
                    ) { change, amount ->
                        change.consume()
                        scope.launch { offset.snapTo((offset.value + amount).coerceIn(minOffset, maxOffset)) }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Alarm, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
private fun SliderLabel(text: String, textAlign: TextAlign = TextAlign.Start) {
    Text(
        text,
        color = AlarmText,
        fontSize = 20.sp,
        lineHeight = 22.sp,
        textAlign = textAlign,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}
