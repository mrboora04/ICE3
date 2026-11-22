package com.mrboora.plate.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.window.DialogProperties
import com.mrboora.plate.data.Selfies
import com.mrboora.plate.ui.theme.Plate
import com.mrboora.plate.ui.theme.PosterType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Loads [day]'s selfie at [maxPx] off the main thread; reloads when [version] changes. */
@Composable
fun rememberSelfie(day: Long, maxPx: Int, version: Int): Bitmap? {
    val context = LocalContext.current
    return produceState<Bitmap?>(null, day, maxPx, version) {
        value = withContext(Dispatchers.IO) { Selfies.load(context, day, maxPx) }
    }.value
}

/** Keeps the screen out of screenshots and the recent-apps preview while a selfie is showing. */
@Composable
fun SecureWhile(active: Boolean) {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(active) {
        if (active) activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (active) activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

/**
 * Asks for the phone's screen lock if the selfie lock is on, then calls back.
 * Returns the function to call when the user wants to see their selfies.
 */
@Composable
fun rememberSelfieUnlock(locked: Boolean, onUnlocked: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) onUnlocked()
    }
    return {
        val intent = if (locked) Selfies.unlockIntent(context) else null
        if (intent == null) onUnlocked() else launcher.launch(intent)
    }
}

/** The camera app, asked to start with the front camera (most camera apps honour one of these). */
private class FrontCamera : ActivityResultContracts.TakePicture() {
    override fun createIntent(context: Context, input: Uri): Intent = super.createIntent(context, input)
        .putExtra("android.intent.extras.CAMERA_FACING", 1)
        .putExtra("android.intent.extras.LENS_FACING_FRONT", 1)
        .putExtra("android.intent.extra.USE_FRONT_CAMERA", true)
}

/** Opens the camera or the photo picker and hands back the result for [day]. */
class SelfieTakers(val takePhoto: () -> Unit, val pickPhoto: () -> Unit)

@Composable
fun rememberSelfieTakers(onCaptured: () -> Unit, onPicked: (Uri) -> Unit, onNoCamera: () -> Unit): SelfieTakers {
    val context = LocalContext.current
    val camera = rememberLauncherForActivityResult(FrontCamera()) { ok -> if (ok) onCaptured() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(onPicked) }
    return remember(camera, picker) {
        SelfieTakers(
            takePhoto = {
                try {
                    camera.launch(Selfies.captureUri(context))
                } catch (e: ActivityNotFoundException) {
                    onNoCamera()
                }
            },
            pickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        )
    }
}

private fun shortDay(day: Long): String {
    val today = LocalDate.now().toEpochDay()
    return when (day) {
        today -> "Today"
        today - 1 -> "Yesterday"
        else -> LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("MMM d"))
    }
}

/** A selfie that is blurred until [revealed]; tapping the cover asks to reveal. */
@Composable
fun SelfiePicture(day: Long, version: Int, revealed: Boolean, locked: Boolean, onReveal: () -> Unit, modifier: Modifier = Modifier) {
    val c = Plate.colors
    val context = LocalContext.current
    val cover = produceState<Bitmap?>(null, day, version) {
        value = withContext(Dispatchers.IO) { Selfies.cover(context, day) }
    }.value
    val full = if (revealed) rememberSelfie(day, Selfies.MAX_SIDE, version) else null
    Box(
        modifier.clip(RoundedCornerShape(22.dp)).background(c.track)
            .then(if (!revealed) Modifier.clickable(onClickLabel = "Show selfie", onClick = onReveal) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        val shown = full ?: cover
        if (shown != null) {
            Image(
                shown.asImageBitmap(), contentDescription = if (revealed) "Selfie, ${shortDay(day)}" else null,
                contentScale = ContentScale.Crop, filterQuality = FilterQuality.Medium, modifier = Modifier.fillMaxSize(),
            )
        }
        if (!revealed) {
            Row(
                Modifier.clip(CircleShape).background(c.card.copy(alpha = 0.85f)).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (locked) Icons.Rounded.Lock else Icons.Rounded.Visibility, null, Modifier.size(18.dp), tint = c.ink)
                Spacer(Modifier.width(8.dp))
                Text(if (locked) "Unlock to see" else "Tap to see", style = MaterialTheme.typography.labelLarge, color = c.ink)
            }
        }
    }
}

/**
 * Today's card. Empty: an invitation to take one. Taken: the blurred photo, with
 * hide, replace, compare and delete once it's showing.
 */
@Composable
fun SelfieCard(
    day: Long,
    has: Boolean,
    version: Int,
    revealed: Boolean,
    locked: Boolean,
    canCompare: Boolean,
    takers: SelfieTakers,
    onReveal: () -> Unit,
    onHide: () -> Unit,
    onCompare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Plate.colors
    val isToday = day == LocalDate.now().toEpochDay()
    var confirmDelete by remember { mutableStateOf(false) }
    SecureWhile(has && revealed)
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this selfie?") },
            text = { Text("It's only on this phone, so it can't be brought back.") },
            confirmButton = { TextButton({ confirmDelete = false; onDelete() }) { Text("Delete") } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Keep") } },
        )
    }
    PlateCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("SELFIE OF THE DAY", style = PosterType.section)
                Text(
                    if (has) shortDay(day) else if (isToday) "Just for you. It stays on this phone." else "No selfie on ${shortDay(day)}",
                    style = MaterialTheme.typography.bodySmall, color = c.muted,
                )
            }
            if (has && revealed) IconButton(onHide) { Icon(Icons.Rounded.VisibilityOff, "Hide selfie") }
        }
        Spacer(Modifier.height(12.dp))
        if (has) {
            SelfiePicture(day, version, revealed, locked && !revealed, onReveal, Modifier.fillMaxWidth().aspectRatio(4f / 5f))
            if (revealed) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (canCompare) {
                        Button(onCompare, contentPadding = PaddingValues(horizontal = 16.dp)) {
                            Icon(Icons.Rounded.Compare, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Compare")
                        }
                    }
                    OutlinedButton(if (isToday) takers.takePhoto else takers.pickPhoto, contentPadding = PaddingValues(horizontal = 16.dp)) {
                        Text(if (isToday) "Retake" else "Replace")
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton({ confirmDelete = true }) { Icon(Icons.Rounded.Delete, "Delete selfie", tint = c.muted) }
                }
            }
        } else {
            if (isToday) {
                Text(
                    "Take one photo a day and watch the change over weeks. It's blurred until you tap it, " +
                        "never uploaded and not included in backups.",
                    style = MaterialTheme.typography.bodyMedium, color = c.muted,
                )
                Spacer(Modifier.height(12.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isToday) {
                    Button(takers.takePhoto, Modifier.weight(1f)) {
                        Icon(Icons.Rounded.CameraAlt, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Take selfie")
                    }
                }
                OutlinedButton(takers.pickPhoto, if (isToday) Modifier.weight(1f) else Modifier) {
                    Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("From photos")
                }
            }
        }
    }
}

/**
 * Before and after, one over the other: drag the handle to wipe between them.
 * Pick any two days from the strips below.
 */
@Composable
fun SelfieCompare(days: List<Long>, version: Int, onDismiss: () -> Unit) {
    if (days.size < 2) return
    val c = Plate.colors
    var before by remember { mutableStateOf(days.first()) }
    var after by remember { mutableStateOf(days.last()) }
    var split by remember { mutableFloatStateOf(0.5f) }
    SecureWhile(true)
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // The dialog has its own window; keep it out of screenshots too.
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(dialogWindow) {
            dialogWindow?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            onDispose { }
        }
        Column(Modifier.fillMaxSize().background(c.ground).statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("BEFORE / AFTER", style = PosterType.title)
                    val gap = (after - before).toInt()
                    Text(
                        if (gap == 0) "Same day" else "${kotlin.math.abs(gap)} day${if (kotlin.math.abs(gap) == 1) "" else "s"} apart · drag to compare",
                        style = MaterialTheme.typography.bodySmall, color = c.muted,
                    )
                }
                IconButton(onDismiss) { Icon(Icons.Rounded.Close, "Close") }
            }
            Spacer(Modifier.height(12.dp))
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.track)
                    .pointerInput(Unit) {
                        detectTapGestures { split = (it.x / size.width).coerceIn(0f, 1f) }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ -> split = (change.position.x / size.width).coerceIn(0f, 1f) }
                    }
                    .semantics { contentDescription = "Before ${shortDay(before)} and after ${shortDay(after)}, drag sideways to compare" },
            ) {
                val afterPic = rememberSelfie(after, Selfies.MAX_SIDE, version)
                val beforePic = rememberSelfie(before, Selfies.MAX_SIDE, version)
                afterPic?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                beforePic?.let {
                    Image(
                        it.asImageBitmap(), null,
                        Modifier.fillMaxSize().drawWithContent {
                            clipRect(right = size.width * split) { this@drawWithContent.drawContent() }
                        },
                        contentScale = ContentScale.Crop,
                    )
                }
                val x = maxWidth * split
                Box(Modifier.offset(x = x - 1.5.dp).width(3.dp).fillMaxHeight().background(c.card))
                Box(
                    Modifier.align(Alignment.CenterStart).offset(x = x - 22.dp).size(44.dp).clip(CircleShape).background(c.card),
                    contentAlignment = Alignment.Center,
                ) { Text("‹ ›", style = PosterType.section.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize), color = c.ink) }
                Tag(shortDay(before), Modifier.align(Alignment.TopStart).padding(12.dp))
                Tag(shortDay(after), Modifier.align(Alignment.TopEnd).padding(12.dp))
            }
            Spacer(Modifier.height(12.dp))
            DayStrip("Before", days, before) { before = it }
            Spacer(Modifier.height(6.dp))
            DayStrip("After", days, after) { after = it }
        }
    }
}

@Composable
private fun Tag(text: String, modifier: Modifier) {
    Text(
        text.uppercase(), style = PosterType.index, color = Plate.colors.onDay,
        modifier = modifier.clip(CircleShape).background(Plate.colors.dayOver).padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun DayStrip(label: String, days: List<Long>, selected: Long, onPick: (Long) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Eyebrow(label, Modifier.width(56.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(days.reversed(), key = { it }) { d ->
                FilterChip(d == selected, { onPick(d) }, label = { Text(shortDay(d)) })
            }
        }
    }
}

/** Selfie settings on the Profile screen. */
@Composable
fun SelfieSettings(
    count: Int,
    locked: Boolean,
    canLock: Boolean,
    reminder: Boolean,
    reminderHour: Int,
    onLock: (Boolean) -> Unit,
    onReminder: (Boolean, Int) -> Unit,
    onDeleteAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Plate.colors
    var confirm by remember { mutableStateOf(false) }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete all $count selfies?") },
            text = { Text("They're only on this phone, so they can't be brought back.") },
            confirmButton = { TextButton({ confirm = false; onDeleteAll() }) { Text("Delete all") } },
            dismissButton = { TextButton({ confirm = false }) { Text("Keep") } },
        )
    }
    PlateCard(modifier) {
        Eyebrow("Selfie of the day")
        Text(
            if (count == 0) "No selfies yet" else "$count selfie${if (count == 1) "" else "s"}, only on this phone",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        SettingRow(
            "Lock with screen lock",
            if (canLock) "Ask for your PIN, pattern or fingerprint before showing them" else "Set a screen lock on your phone to use this",
            locked, canLock, onLock,
        )
        SettingRow("Daily reminder", "Only if you haven't taken today's", reminder, true) { onReminder(it, reminderHour) }
        if (reminder) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(7, 9, 12, 19).forEach { h ->
                    FilterChip(h == reminderHour, { onReminder(true, h) }, label = {
                        Text(if (h < 12) "$h AM" else if (h == 12) "12 PM" else "${h - 12} PM")
                    })
                }
            }
        }
        if (count > 0) {
            TextButton({ confirm = true }, contentPadding = PaddingValues(0.dp)) { Text("Delete all selfies", color = c.over) }
        }
    }
}

@Composable
private fun SettingRow(title: String, detail: String, on: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = Plate.colors.muted)
        }
        Switch(on, onChange, enabled = enabled)
    }
}

/** Progress card: how many selfies, and the way into Compare. */
@Composable
fun SelfieProgressCard(days: List<Long>, onCompare: () -> Unit, modifier: Modifier = Modifier) {
    if (days.size < 2) return
    val c = Plate.colors
    PlateCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("SELFIES", style = PosterType.section)
                val span = (days.last() - days.first()).toInt()
                Text(
                    "${days.size} photos over ${span + 1} days, since ${shortDay(days.first())}",
                    style = MaterialTheme.typography.bodySmall, color = c.muted,
                )
            }
            Button(onCompare) {
                Icon(Icons.Rounded.Compare, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Compare")
            }
        }
    }
}
