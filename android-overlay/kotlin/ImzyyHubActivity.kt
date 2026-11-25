package id.imzyy.xl

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Premium monochrome home for IMZYY XL. Opens the upstream launcher with PLAY. */
class ImzyyHubActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContent { HubApp() }
    }
}

class HubState(val ctx: Context, val scope: CoroutineScope) {
    private val prefs = ctx.getSharedPreferences("imzyy_hub", Context.MODE_PRIVATE)

    var tab by mutableStateOf(0)
    var target by mutableStateOf(prefs.getString("target", "") ?: "")
    var gameVersion by mutableStateOf(prefs.getString("gv", "1.20.1") ?: "1.20.1")
    var loader by mutableStateOf(prefs.getString("loader", "fabric") ?: "fabric")
    var busy by mutableStateOf(false)
    var progress by mutableStateOf(0f)
    var query by mutableStateOf("")
    var updateText by mutableStateOf("")
    var mrInfo by mutableStateOf<MrInfo?>(null)
    var mrUri by mutableStateOf<Uri?>(null)
    var mouseSensitivity by mutableStateOf(0.5f)
    var mouseWheelSensitivity by mutableStateOf(1f)
    var invertMouseY by mutableStateOf(false)
    var rawMouseInput by mutableStateOf(true)
    var swapMouseButtons by mutableStateOf(false)
    var renderDistance by mutableStateOf(12f)
    var simulationDistance by mutableStateOf(8f)

    val logs = mutableStateListOf<String>()
    val instances = mutableStateListOf<File>()
    val versions = mutableStateListOf<String>()
    val hits = mutableStateListOf<Hit>()
    val modFiles = mutableStateListOf<ManagedMod>()
    val picked = mutableStateMapOf<String, Boolean>().also { m -> PerfPack.catalog.forEach { m[it.slug] = it.defaultOn } }

    fun say(s: String) {
        logs.add(s)
        while (logs.size > 80) logs.removeAt(0)
    }

    fun setTarget(p: String) {
        target = p
        prefs.edit().putString("target", p).apply()
    }

    fun loadTargetSettings() {
        if (target.isBlank()) return
        val options = FpsTuner.read(File(target))
        mouseSensitivity = options["mouseSensitivity"]?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.5f
        mouseWheelSensitivity = options["mouseWheelSensitivity"]?.toFloatOrNull()?.coerceIn(1f, 10f) ?: 1f
        invertMouseY = options["invertYMouse"]?.toBooleanStrictOrNull() ?: false
        rawMouseInput = options["rawMouseInput"]?.toBooleanStrictOrNull() ?: true
        swapMouseButtons = options["key_key.attack"] == "key.mouse.right"
        renderDistance = (options["renderDistance"]?.toFloatOrNull() ?: 12f).coerceIn(2f, 32f)
        simulationDistance = (options["simulationDistance"]?.toFloatOrNull() ?: 8f).coerceIn(2f, 12f)
    }
    fun setVersion(v: String) { gameVersion = v.trim(); prefs.edit().putString("gv", gameVersion).apply() }
    fun setLoaderTo(l: String) { loader = l; prefs.edit().putString("loader", l).apply() }

    private fun needTarget(): File {
        if (target.isBlank()) error("Pilih folder instance dulu")
        return File(target)
    }

    fun runJob(block: () -> Unit) {
        if (busy) return
        busy = true
        progress = 0f
        scope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
            } catch (e: Exception) {
                say("✗ ${e.message ?: e.javaClass.simpleName}")
            } finally {
                busy = false
            }
        }
    }

    fun refreshInstances() {
        scope.launch {
            val found = withContext(Dispatchers.IO) { InstanceLocator.find(ctx) }
            instances.clear(); instances.addAll(found)
            if (target.isBlank() && found.isNotEmpty()) setTarget(found.first().path)
            if (found.isEmpty()) say("Belum ada folder game ditemukan. Jalankan Minecraft sekali dari launcher, lalu muat ulang.")
        }
    }

    fun loadVersions() {
        if (versions.isNotEmpty()) return
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { Modrinth.releaseVersions().take(10) } }
                .onSuccess { versions.clear(); versions.addAll(it) }
        }
    }

    fun applyProfile(p: FpsTuner.Profile) = runJob {
        val n = FpsTuner.apply(needTarget(), p)
        say("✓ Profil ${p.label}: $n opsi diubah (cadangan: options.txt.imzyy.bak)")
    }

    fun restoreOptions() = runJob {
        say(if (FpsTuner.restore(needTarget())) "✓ options.txt dikembalikan" else "Tidak ada cadangan options.txt")
    }

    fun applyMouseControls() = runJob {
        val n = FpsTuner.applyMouse(needTarget(), mouseSensitivity, mouseWheelSensitivity, invertMouseY, rawMouseInput, swapMouseButtons)
        say("✓ Kontrol mouse diterapkan: $n opsi (sensitivitas ${(mouseSensitivity * 100).toInt()}%)")
    }

    fun applyRenderControls() = runJob {
        val n = FpsTuner.applyRenderDistance(needTarget(), renderDistance.toInt(), simulationDistance.toInt())
        say("✓ Jarak render vanilla ${renderDistance.toInt()} chunk; simulasi ${simulationDistance.toInt()} chunk. Voxy LOD diatur dari menu mod saat kompatibel.")
    }

    fun optimizeTextures() = runJob {
        val result = TextureOptimizer.optimize(needTarget(), this::say)
        val mib = result.savedBytes / 1024.0 / 1024.0
        say("✓ Tekstur: ${result.optimized} dioptimalkan, ${result.scanned} item dipindai, hemat ${"%.2f".format(mib)} MiB, ${result.skipped} dilewati.")
        say("Backup asli: ${File(needTarget(), ".imzyy-backups/textures").path}")
    }

    fun restoreTextures() = runJob {
        val count = TextureOptimizer.restore(needTarget())
        say(if (count > 0) "✓ $count file tekstur dipulihkan dari backup" else "Belum ada backup tekstur untuk dipulihkan")
    }

    fun refreshMods() {
        if (target.isBlank()) { modFiles.clear(); return }
        scope.launch {
            val found = withContext(Dispatchers.IO) { ModManager.list(File(target, "mods")) }
            modFiles.clear()
            modFiles.addAll(found)
        }
    }

    fun importMod(uri: Uri) = runJob {
        val imported = ModManager.importJar(ctx, uri, File(needTarget(), "mods"))
        say("✓ Mod diimpor: ${imported.name}. Tutup game sebelum mengaktifkan perubahan.")
        refreshMods()
    }

    fun toggleMod(entry: ManagedMod) = runJob {
        val changed = ModManager.toggle(entry)
        say("✓ ${changed.name} ${if (changed.name.endsWith(".jar")) "diaktifkan" else "dinonaktifkan"}")
        refreshMods()
    }

    fun deleteMod(entry: ManagedMod) = runJob {
        if (!ModManager.remove(entry)) error("Gagal menghapus ${entry.file.name}")
        say("✓ ${entry.file.name} dihapus")
        refreshMods()
    }

    fun installPack() = runJob {
        val mods = File(needTarget(), "mods")
        val slugs = PerfPack.catalog.filter { picked[it.slug] == true }.map { it.slug }
        if (slugs.isEmpty()) error("Pilih minimal satu mod")
        say("Memasang paket FPS untuk $gameVersion / $loader…")
        val n = PerfPack.install(mods, gameVersion, loader, slugs, this::say)
        say("✓ $n file baru terpasang. Pastikan instance memakai loader $loader.")
    }

    fun removePack() = runJob {
        val n = PerfPack.uninstall(File(needTarget(), "mods"))
        say("✓ $n file paket FPS dihapus")
    }

    fun search() = runJob {
        needTarget()
        val q = query.trim()
        if (q.isEmpty()) error("Ketik nama mod")
        val r = Modrinth.search(q, gameVersion, loader)
        hits.clear(); hits.addAll(r)
        say("${r.size} hasil untuk \"$q\" ($gameVersion/$loader)")
    }

    fun installHit(h: Hit) = runJob {
        val mods = File(needTarget(), "mods")
        say("Memasang ${h.title}…")
        val n = PerfPack.install(mods, gameVersion, loader, listOf(h.id), this::say)
        say("✓ ${h.title}: $n file baru")
    }

    fun loadMrpack(uri: Uri) = runJob {
        val info = Mrpack.readIndex(ctx, uri)
        mrUri = uri
        mrInfo = info
        say("Modpack: ${info.name} ${info.version} — ${info.files.size} file")
    }

    fun importMrpack() = runJob {
        val uri = mrUri ?: error("Pilih file .mrpack dulu")
        val info = mrInfo ?: error("Pilih file .mrpack dulu")
        Mrpack.install(ctx, uri, info, needTarget(), this::say) { progress = it }
    }

    fun checkUpdate() {
        if (ImzyyConfig.GITHUB_REPO.isEmpty()) return
        scope.launch {
            updateText = "Memeriksa…"
            updateText = try {
                withContext(Dispatchers.IO) {
                    val o = JSONObject(Net.getText("https://api.github.com/repos/${ImzyyConfig.GITHUB_REPO}/releases/latest"))
                    val tag = o.optString("tag_name")
                    if (tag.removePrefix("v") == ImzyyConfig.VERSION) "Sudah versi terbaru (${ImzyyConfig.VERSION})" else "Versi baru: $tag"
                }
            } catch (e: Exception) {
                "Belum ada rilis / gagal memeriksa"
            }
        }
    }

    fun openUrl(url: String) {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun play() {
        ctx.startActivity(Intent().setClassName(ctx.packageName, ImzyyConfig.MAIN_ACTIVITY))
    }
}

@Composable
fun HubApp() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = remember { HubState(ctx, scope) }
    LaunchedEffect(Unit) { s.refreshInstances(); s.loadVersions() }
    LaunchedEffect(s.target) { if (s.target.isNotBlank()) s.loadTargetSettings() }

    Box(Modifier.fillMaxSize().background(Ink.Black)) {
        AnimatedBackdrop()
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Box(Modifier.weight(1f)) {
                Crossfade(targetState = s.tab, animationSpec = tween(280), label = "tab") { t ->
                    when (t) {
                        0 -> HomeTab(s)
                        1 -> BoostTab(s)
                        2 -> MouseTab(s)
                        3 -> ModsTab(s)
                        else -> ImportTab(s)
                    }
                }
            }
            NavBar(s)
        }
    }
}

@Composable
private fun NavBar(s: HubState) {
    val names = listOf("HOME", "BOOST", "MOUSE", "MODS", "IMPORT")
    Row(
        Modifier.fillMaxWidth().background(Ink.Black).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        names.forEachIndexed { i, n -> Chip(n, s.tab == i) { s.tab = i } }
    }
}

@Composable
private fun Title(text: String, sub: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text, color = Ink.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
        Text(sub, color = Ink.Sub, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun HomeTab(s: HubState) {
    LaunchedEffect(Unit) { s.checkUpdate() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Reveal(0) { Spacer(Modifier.height(24.dp)) } }
        item { Reveal(1) { Title("IMZYY XL", "Minecraft Java • Android ARM64") } }
        item { Reveal(2) { InkButton("▶  PLAY", { s.play() }) } }
        item {
            Reveal(3) {
                Panel {
                    Text("Boost FPS", color = Ink.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Profil options.txt + paket mod performa (Sodium, Lithium, FerriteCore, ModernFix…).", color = Ink.Sub, fontSize = 13.sp)
                    InkButton("BUKA BOOST", { s.tab = 1 }, filled = false)
                }
            }
        }
        item {
            Reveal(4) {
                Panel {
                    Text("Mod & Modpack", color = Ink.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Cari mod dari Modrinth sesuai versi + loader, atau import file .mrpack.", color = Ink.Sub, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        InkButton("CARI MOD", { s.tab = 3 }, Modifier.weight(1f), filled = false)
                        InkButton("IMPORT .MRPACK", { s.tab = 4 }, Modifier.weight(1f), filled = false)
                    }
                }
            }
        }
        if (ImzyyConfig.GITHUB_REPO.isNotEmpty()) {
            item {
                Reveal(5) {
                    Panel {
                        Text("GitHub", color = Ink.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(ImzyyConfig.GITHUB_REPO + "  •  v" + ImzyyConfig.VERSION, color = Ink.Sub, fontSize = 13.sp)
                        if (s.updateText.isNotEmpty()) Text(s.updateText, color = Ink.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            InkButton("REPO", { s.openUrl("https://github.com/" + ImzyyConfig.GITHUB_REPO) }, Modifier.weight(1f), filled = false)
                            InkButton("RILIS", { s.openUrl("https://github.com/" + ImzyyConfig.GITHUB_REPO + "/releases") }, Modifier.weight(1f), filled = false)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TargetCard(s: HubState, withVersion: Boolean) {
    Panel {
        Text("Folder instance", color = Ink.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        if (s.instances.isEmpty()) {
            Text("Belum terdeteksi. Jalankan game sekali, lalu muat ulang, atau isi path manual.", color = Ink.Sub, fontSize = 12.sp)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            s.instances.take(8).forEach { f -> Chip(InstanceLocator.shortName(f), s.target == f.path) { s.setTarget(f.path) } }
            Chip("↻ MUAT ULANG", false) { s.refreshInstances() }
        }
        InkField(s.target, { s.setTarget(it) }, "/path/ke/folder/instance")
        if (withVersion) {
            Text("Versi Minecraft & loader", color = Ink.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            InkField(s.gameVersion, { s.setVersion(it) }, "mis. 1.20.1")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                s.versions.take(6).forEach { v -> Chip(v, s.gameVersion == v) { s.setVersion(v) } }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("fabric", "quilt", "forge", "neoforge").forEach { l -> Chip(l.uppercase(), s.loader == l) { s.setLoaderTo(l) } }
            }
        }
    }
}

@Composable
private fun LogCard(s: HubState) {
    if (s.logs.isEmpty() && !s.busy) return
    Panel {
        if (s.busy) InkProgress(s.progress)
        s.logs.takeLast(8).forEach { Text(it, color = Ink.Sub, fontSize = 12.sp) }
    }
}

@Composable
private fun BoostTab(s: HubState) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Reveal(0) { Title("BOOST", "Optimasi FPS langsung dari launcher") } }
        item { Reveal(1) { TargetCard(s, withVersion = true) } }
        item {
            Reveal(2) {
                Panel {
                    Text("Profil grafis (options.txt)", color = Ink.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Render distance, partikel, bayangan, AO, awan. Cadangan disimpan otomatis.", color = Ink.Sub, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FpsTuner.Profile.values().forEach { p ->
                            InkButton(p.label, { s.applyProfile(p) }, Modifier.weight(1f), filled = p == FpsTuner.Profile.SAVER, enabled = !s.busy)
                        }
                    }
                    InkButton("KEMBALIKAN OPTIONS ASLI", { s.restoreOptions() }, filled = false, enabled = !s.busy)
                }
            }
        }
        item {
            Reveal(3) {
                Panel {
                    Text("RENDER DISTANCE", color = Ink.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Vanilla: ${s.renderDistance.toInt()} chunk", color = Ink.Sub, fontSize = 12.sp)
                    Slider(value = s.renderDistance, onValueChange = { s.renderDistance = it }, valueRange = 2f..32f, steps = 29)
                    Text("Simulation: ${s.simulationDistance.toInt()} chunk", color = Ink.Sub, fontSize = 12.sp)
                    Slider(value = s.simulationDistance, onValueChange = { s.simulationDistance = it }, valueRange = 2f..12f, steps = 9)
                    Text("Jarak sangat jauh membutuhkan mod LOD seperti Voxy dan tetap dibatasi GPU/RAM. Slider ini mengatur jarak vanilla, bukan membuat chunk tak terbatas.", color = Ink.Sub, fontSize = 12.sp)
                    InkButton("TERAPKAN JARAK RENDER", { s.applyRenderControls() }, enabled = !s.busy)
                }
            }
        }
        item {
            Reveal(4) {
                Panel {
                    Text("OPTIMASI TEKSTUR", color = Ink.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Re-encode PNG tanpa menurunkan resolusi/pixel, untuk folder dan arsip resource pack. File asli dicadangkan sebelum diganti. Penghematan tergantung isi pack; ini mengurangi ukuran storage, bukan VRAM GPU.", color = Ink.Sub, fontSize = 12.sp)
                    InkButton("OPTIMALKAN RESOURCE PACK", { s.optimizeTextures() }, enabled = !s.busy)
                    InkButton("PULIHKAN TEKSTUR ASLI", { s.restoreTextures() }, filled = false, enabled = !s.busy)
                }
            }
        }
        item {
            Reveal(5) {
                Panel {
                    Text("Paket mod performa", color = Ink.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    PerfPack.catalog.forEach { m ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Chip(if (s.picked[m.slug] == true) "ON" else "OFF", s.picked[m.slug] == true) { s.picked[m.slug] = s.picked[m.slug] != true }
                            Column(Modifier.weight(1f)) {
                                Text(m.label, color = Ink.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(m.note, color = Ink.Sub, fontSize = 12.sp)
                            }
                        }
                    }
                    InkButton("PASANG PAKET FPS", { s.installPack() }, enabled = !s.busy)
                    InkButton("COPOT PAKET FPS", { s.removePack() }, filled = false, enabled = !s.busy)
                }
            }
        }
        item { LogCard(s) }
    }
}

@Composable
private fun MouseTab(s: HubState) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Reveal(0) { Title("CONTROL MOUSE", "Konfigurasi mouse untuk instance Minecraft") } }
        item { Reveal(1) { TargetCard(s, withVersion = false) } }
        item {
            Reveal(2) {
                Panel {
                    Text("Sensitivitas mouse: ${(s.mouseSensitivity * 100).toInt()}%", color = Ink.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Slider(value = s.mouseSensitivity, onValueChange = { s.mouseSensitivity = it }, valueRange = 0f..1f, steps = 19)
                    Text("Sensitivitas scroll: ${"%.1f".format(s.mouseWheelSensitivity)}", color = Ink.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Slider(value = s.mouseWheelSensitivity, onValueChange = { s.mouseWheelSensitivity = it }, valueRange = 1f..10f, steps = 8)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip(if (s.invertMouseY) "Y TERBALIK: ON" else "Y TERBALIK: OFF", s.invertMouseY) { s.invertMouseY = !s.invertMouseY }
                        Chip(if (s.rawMouseInput) "RAW INPUT: ON" else "RAW INPUT: OFF", s.rawMouseInput) { s.rawMouseInput = !s.rawMouseInput }
                    }
                    Chip(if (s.swapMouseButtons) "TUKAR KLIK: ON" else "TUKAR KLIK: OFF", s.swapMouseButtons) { s.swapMouseButtons = !s.swapMouseButtons }
                    InkButton("TERAPKAN KONTROL MOUSE", { s.applyMouseControls() }, enabled = !s.busy)
                    Text("Mendukung konfigurasi untuk mouse USB/OTG dan Bluetooth yang dikenali Android/engine. Perubahan options.txt berlaku saat Minecraft dimulai ulang; kontrol pointer di dalam game tetap mengikuti input engine.", color = Ink.Sub, fontSize = 12.sp)
                }
            }
        }
        item { LogCard(s) }
    }
}

@Composable
private fun ModsTab(s: HubState) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) s.importMod(uri)
    }
    LaunchedEffect(s.target) { s.refreshMods() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Reveal(0) { Title("MODS", "Cari & pasang dari Modrinth") } }
        item { Reveal(1) { TargetCard(s, withVersion = true) } }
        item {
            Reveal(2) {
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("MOD TERPASANG", color = Ink.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("${s.modFiles.size} file JAR di folder instance", color = Ink.Sub, fontSize = 12.sp)
                        }
                        InkButton("MUAT ULANG", { s.refreshMods() }, filled = false, enabled = !s.busy)
                    }
                    InkButton("IMPORT FILE MOD .JAR", { picker.launch(arrayOf("application/java-archive", "application/octet-stream", "*/*")) }, enabled = !s.busy)
                    Text("Mod yang OFF diganti nama menjadi .jar.disabled. Perubahan aktif setelah game ditutup dan dibuka kembali. Gunakan hanya file dari sumber tepercaya.", color = Ink.Sub, fontSize = 12.sp)
                    if (s.modFiles.isEmpty()) Text("Belum ada file mod JAR yang terdeteksi.", color = Ink.Sub, fontSize = 12.sp)
                }
            }
        }
        items(s.modFiles.toList(), key = { it.file.absolutePath }) { entry ->
            Panel {
                Text(entry.file.name, color = Ink.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { InkButton(if (entry.enabled) "NONAKTIFKAN" else "AKTIFKAN", { s.toggleMod(entry) }, filled = entry.enabled.not(), enabled = !s.busy) }
                    Box(Modifier.weight(1f)) { InkButton("HAPUS", { s.deleteMod(entry) }, filled = false, enabled = !s.busy) }
                }
            }
        }
        item {
            Reveal(3) {
                Panel {
                    InkField(s.query, { s.query = it }, "Cari mod (mis. sodium, iris, ferrite)")
                    InkButton("CARI", { s.search() }, enabled = !s.busy)
                }
            }
        }
        items(s.hits.toList()) { h ->
            Panel {
                Text(h.title, color = Ink.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(h.desc, color = Ink.Sub, fontSize = 12.sp, maxLines = 3)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${h.downloads} unduhan", color = Ink.Sub, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    Box(Modifier.width(150.dp)) { InkButton("PASANG", { s.installHit(h) }, enabled = !s.busy) }
                }
            }
        }
        item { LogCard(s) }
    }
}

@Composable
private fun ImportTab(s: HubState) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) s.loadMrpack(uri)
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Reveal(0) { Title("IMPORT", "Modpack .mrpack (Modrinth)") } }
        item { Reveal(1) { TargetCard(s, withVersion = false) } }
        item {
            Reveal(2) {
                Panel {
                    InkButton("PILIH FILE .MRPACK", { picker.launch(arrayOf("*/*")) }, enabled = !s.busy)
                    val info = s.mrInfo
                    if (info != null) {
                        Text(info.name + "  " + info.version, color = Ink.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        if (info.summary.isNotEmpty()) Text(info.summary, color = Ink.Sub, fontSize = 12.sp, maxLines = 3)
                        Text(info.deps.entries.joinToString("  •  ") { it.key + " " + it.value }, color = Ink.Text, fontSize = 12.sp)
                        Text("${info.files.size} file akan diunduh dan diverifikasi hash-nya.", color = Ink.Sub, fontSize = 12.sp)
                        Text("Buat dulu instance dengan versi & loader di atas lewat launcher, lalu pilih foldernya sebagai target.", color = Ink.Sub, fontSize = 12.sp)
                        InkButton("IMPORT KE FOLDER INSTANCE", { s.importMrpack() }, enabled = !s.busy)
                    }
                }
            }
        }
        item { LogCard(s) }
    }
}
