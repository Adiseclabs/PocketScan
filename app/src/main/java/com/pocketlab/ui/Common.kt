// PocketLab by Aditya Bhosale | +91 9324218321 | adityabhosale212223@gmail.com
package com.pocketlab.ui

import android.Manifest
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat

fun riskColor(l: String) = when (l) { "Critical" -> Color(0xFFD32F2F); "High" -> Color(0xFFF57C00); "Medium" -> Color(0xFFFBC02D); "Low" -> Color(0xFF1976D2); else -> Color(0xFF388E3C) }

@Composable fun Pill(text: String, color: Color) {
    Surface(color = color, shape = RoundedCornerShape(50)) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), color = if (color == riskColor("Medium")) Color.Black else Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

fun blePerms(): Array<String> = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
fun Context.hasAll(p: Array<String>) = p.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

@Composable fun Radar(sweep: Float, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val r = size.minDimension / 2
        listOf(1f, .66f, .33f).forEach { drawCircle(c.copy(alpha = .35f), r * it, style = Stroke(2f)) }
        rotate(sweep) { drawCircle(Brush.sweepGradient(listOf(Color.Transparent, c.copy(alpha = .6f))), r) }
        drawCircle(c, 6f)
    }
}

@Composable fun Contact() {
    val ui = LocalUriHandler.current
    val s = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
    val c = MaterialTheme.colorScheme.primary
    Column {
        Text("+91 9324218321", style = s, color = c, modifier = Modifier.clickable { ui.openUri("tel:+919324218321") })
        Text("adityabhosale212223@gmail.com", style = s, color = c, modifier = Modifier.clickable { ui.openUri("mailto:adityabhosale212223@gmail.com") })
        Text("github.com/Adiseclabs/PocketScan", style = s, color = c, modifier = Modifier.clickable { ui.openUri("https://github.com/Adiseclabs/PocketScan") })
    }
}

@Composable fun AttributionCard() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("PocketLab", style = MaterialTheme.typography.titleSmall)
            Text("Aditya Bhosale", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), fontWeight = FontWeight.Medium)
            Contact()
        }
    }
}

const val FOX_HELP = "1. Hold the phone steady and walk slowly.\n2. Bigger circle, fuller bar and longer buzzes mean you are closer.\n3. If the signal drops, turn around or try another spot - the strongest reading wins.\n4. Check bags, jacket pockets, under seats, car bumpers and wheel arches.\n5. It stops by itself after 10 minutes, or when you are within arm's reach (stronger than -45 dBm).\n\nTip: keep Bluetooth on. Trackers change address now and then, so restart the hunt if the signal vanishes."

fun ts(t: Long): String = DateFormat.format("MMM d, HH:mm", t).toString()

@Composable fun KV(k: String, v: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(k, Modifier.weight(.4f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Text(v, Modifier.weight(.6f), style = MaterialTheme.typography.bodyMedium)
    }
}

// Saves CSV text through the system file picker (no storage permission needed).
@Composable fun rememberExporter(): (String, String) -> Unit {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf("") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } }
            .onSuccess { Toast.makeText(ctx, "Exported", Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(ctx, "Export failed", Toast.LENGTH_SHORT).show() }
    }
    return { name, t -> text = t; launcher.launch(name) }
}

@Composable fun FoxInfoDialog(onDismiss: () -> Unit, onStart: (() -> Unit)?) {
    val ctx = LocalContext.current; val sp = remember { ctx.getSharedPreferences("pl", 0) }; var never by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("How to use Fox Hunt") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(FOX_HELP, style = MaterialTheme.typography.bodyMedium)
                if (onStart != null) Row(Modifier.clickable { never = !never }, verticalAlignment = Alignment.CenterVertically) { Checkbox(never, { never = it }); Text("Don't show this before each hunt", style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = { if (never) sp.edit().putBoolean("foxinfo", true).apply(); onDismiss(); onStart?.invoke() }) { Text(if (onStart != null) "Start hunt" else "Got it") } },
        dismissButton = if (onStart != null) ({ TextButton(onClick = onDismiss) { Text("Cancel") } }) else null)
}
