package com.kruzzi.pogocompanion

import android.net.Uri
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.provider.Settings
import android.os.Build
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.json.JSONArray
import org.json.JSONObject

data class PokemonEntry(val name: String, val cp: String, val hp: String, val moves: String)

class MainActivity : ComponentActivity() {
    private var selectedMode = "encounter"
    private val capturePermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val service = Intent(this, LiveCaptureService::class.java)
                .putExtra(LiveCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                .putExtra(LiveCaptureService.EXTRA_RESULT_DATA, result.data)
                .putExtra(LiveCaptureService.EXTRA_MODE, selectedMode)
            startForegroundService(service)
            Toast.makeText(this, "Live capture ενεργό · άνοιξε Pokémon GO", Toast.LENGTH_LONG).show()
        }
    }

    private fun startLiveCapture(mode: String) {
        selectedMode = mode
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Ενεργοποίησε την άδεια εμφάνισης πάνω από άλλες εφαρμογές και πάτησε ξανά", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        capturePermission.launch(manager.createScreenCaptureIntent())
    }

    private fun stopLiveCapture() {
        startService(Intent(this, LiveCaptureService::class.java).setAction(LiveCaptureService.ACTION_STOP))
    }

    private var onImageText: ((String) -> Unit)? = null
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) recognizeText(uri)
    }

    private fun recognizeText(uri: Uri) {
        try {
            val image = InputImage.fromFilePath(this, uri)
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image)
                .addOnSuccessListener { result -> onImageText?.invoke(result.text) }
                .addOnFailureListener { onImageText?.invoke("Δεν έγινε ανάγνωση εικόνας: ${it.localizedMessage}") }
        } catch (e: Exception) {
            onImageText?.invoke("Σφάλμα εικόνας: ${e.localizedMessage}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = androidx.compose.ui.graphics.Color(0xFF83D7A1),
                secondary = androidx.compose.ui.graphics.Color(0xFF9CC7FF),
                background = androidx.compose.ui.graphics.Color(0xFF101510),
                surface = androidx.compose.ui.graphics.Color(0xFF19221B)
            )) {
                CompanionApp(
                    onPickImage = { callback -> onImageText = callback; pickImage.launch("image/*") },
                    onStartLive = { mode -> startLiveCapture(mode) },
                    onStopLive = { stopLiveCapture() }
                )
            }
        }
    }
}

@Composable
fun CompanionApp(onPickImage: ((String) -> Unit) -> Unit, onStartLive: (String) -> Unit, onStopLive: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("pogo_companion", 0) }
    var tab by remember { mutableStateOf(0) }
    var pokemon by remember { mutableStateOf(prefs.getString("pokemon", "") ?: "") }
    var cp by remember { mutableStateOf("") }
    var hp by remember { mutableStateOf("") }
    var moves by remember { mutableStateOf("") }
    var scanText by remember { mutableStateOf(prefs.getString("last_scan", "") ?: "") }
    var nickname by remember { mutableStateOf("") }

    fun savePokemon() {
        if (nickname.isBlank()) {
            Toast.makeText(context, "Βάλε τουλάχιστον το όνομα Pokémon", Toast.LENGTH_SHORT).show()
            return
        }
        val item = JSONObject().put("name", nickname.trim()).put("cp", cp.trim())
            .put("hp", hp.trim()).put("moves", moves.trim())
        val array = try { JSONArray(pokemon) } catch (_: Exception) { JSONArray() }
        array.put(item)
        pokemon = array.toString()
        prefs.edit().putString("pokemon", pokemon).apply()
        nickname = ""; cp = ""; hp = ""; moves = ""
        Toast.makeText(context, "Αποθηκεύτηκε στη συλλογή", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Text("PoGO COMPANION", style = MaterialTheme.typography.headlineSmall)
                Text("Το προσωπικό σου εργαλείο για Pokémon GO", color = MaterialTheme.colorScheme.secondary)
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Text("⌂") }, label = { Text("Αρχική") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Text("◉") }, label = { Text("Συλλογή") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Text("▣") }, label = { Text("Scanner") })
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            when (tab) {
                0 -> {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Καλώς ήρθες!", style = MaterialTheme.typography.titleLarge)
                            Text("Η πρώτη έκδοση του Companion είναι η βάση για τη συλλογή, την ανάλυση Pokémon και τα PvP εργαλεία.")
                            Text("Έκδοση 0.3.0 · IV + PvP beta", color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    Button(onClick = { onStartLive("encounter") }, modifier = Modifier.fillMaxWidth()) { Text("🔎 Encounter IV Mode") }
                    Button(onClick = { onStartLive("pvp") }, modifier = Modifier.fillMaxWidth()) { Text("⚔ PvP Coach Mode") }
                    OutlinedButton(onClick = onStopLive, modifier = Modifier.fillMaxWidth()) { Text("■ Διακοπή Live Capture") }
                    Text("Το Android θα ζητήσει άδεια καταγραφής οθόνης. Το OCR γίνεται στη συσκευή. Η αναγνώριση PvP στρατηγικής δεν έχει υλοποιηθεί ακόμη.")
                    Text("Τι θα βρεις εδώ", style = MaterialTheme.typography.titleMedium)
                    FeatureLine("Συλλογή Pokémon", "Καταχώριση CP, HP και κινήσεων με αποθήκευση στη συσκευή.")
                    FeatureLine("Screenshot scanner", "OCR σε εικόνες Pokémon GO για να διαβάζουμε τα στοιχεία που εμφανίζονται.")
                    FeatureLine("PvP εργαλεία", "Επόμενη επέκταση: IV ranking, ομάδες και προτάσεις μάχης.")
                    OutlinedButton(onClick = { tab = 2 }, modifier = Modifier.fillMaxWidth()) { Text("Άνοιγμα Scanner") }
                }
                1 -> {
                    Text("Η συλλογή μου", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(nickname, { nickname = it }, label = { Text("Pokémon") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(cp, { cp = it }, label = { Text("CP") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(hp, { hp = it }, label = { Text("HP") }, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(moves, { moves = it }, label = { Text("Κινήσεις (προαιρετικά)") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { savePokemon() }, modifier = Modifier.fillMaxWidth()) { Text("Αποθήκευση Pokémon") }
                    val entries = remember(pokemon) {
                        try {
                            val arr = JSONArray(pokemon)
                            (0 until arr.length()).map { i ->
                                val o = arr.getJSONObject(i)
                                PokemonEntry(o.optString("name"), o.optString("cp"), o.optString("hp"), o.optString("moves"))
                            }.reversed()
                        } catch (_: Exception) { emptyList() }
                    }
                    Text("Αποθηκευμένα: ${entries.size}", style = MaterialTheme.typography.titleMedium)
                    entries.forEach { entry ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(entry.name, style = MaterialTheme.typography.titleMedium)
                                Text("CP ${entry.cp.ifBlank { "—" }}  •  HP ${entry.hp.ifBlank { "—" }}")
                                if (entry.moves.isNotBlank()) Text(entry.moves)
                            }
                        }
                    }
                    if (entries.isEmpty()) Text("Δεν έχεις προσθέσει Pokémon ακόμη.")
                }
                2 -> {
                    Text("Live Scanner", style = MaterialTheme.typography.titleLarge)
                    Button(onClick = { onStartLive("encounter") }, modifier = Modifier.fillMaxWidth()) { Text("▶ Encounter IV Analyzer") }
                    Button(onClick = { onStartLive("pvp") }, modifier = Modifier.fillMaxWidth()) { Text("▶ PvP Coach") }
                    OutlinedButton(onClick = onStopLive, modifier = Modifier.fillMaxWidth()) { Text("■ Stop") }
                    Text("Διάλεξε screenshot από το Pokémon GO. Η αναγνώριση κειμένου γίνεται στη συσκευή.")
                    Button(onClick = {
                        onPickImage { text ->
                            scanText = text.ifBlank { "Δεν εντοπίστηκε κείμενο στην εικόνα." }
                            prefs.edit().putString("last_scan", scanText).apply()
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Επιλογή screenshot") }
                    if (scanText.isNotBlank()) {
                        Text("Αναγνωρισμένο κείμενο", style = MaterialTheme.typography.titleMedium)
                        Card(Modifier.fillMaxWidth()) {
                            Text(scanText, Modifier.padding(14.dp))
                        }
                        OutlinedButton(onClick = {
                            val clean = scanText.take(4000)
                            Toast.makeText(context, "Το αποτέλεσμα παραμένει αποθηκευμένο στη συσκευή", Toast.LENGTH_SHORT).show()
                        }) { Text("Τοπική αποθήκευση αποτελέσματος") }
                    }
                    Text("Encounter mode: υπολογισμός πιθανών IV από CP για υποστηριζόμενα είδη. PvP mode: βασικές συμβουλές από ορατά μηνύματα. Για αλλαγή mode πάτα Stop και ξεκίνα ξανά.")
                }
            }
        }
    }
}

@Composable
private fun FeatureLine(title: String, detail: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
