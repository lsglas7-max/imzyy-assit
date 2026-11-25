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
import kr.co.donghyun.flamelauncher.data.instance.InstanceManager
import kr.co.donghyun.flamelauncher.data.instance.InstanceMeta
import kr.co.donghyun.flamelauncher.presentation.KeyboardLayoutEditorActivity
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
    var selectedId by mutableStateOf(prefs.getString("inst", "") ?: "")
    var ctlTick by mutableStateOf(0)
    var busy by mutableStateOf(false)
    var progress by mutableStateOf(0f)
    var query by mutableStateOf("")
    var updateText by mutableStateOf("")
    var mrInfo by mutableStateOf<MrInfo?>(null)
    var mrUri by mutableStateOf<Uri?>(null)

    val logs = mutableStateListOf<String>()
    val instances = mutableStateListOf<InstanceMeta>()
    val hits = mutableStateListOf<Hit>()
    val picked = mutableStateMapOf<String, Boolean>().also { m -> PerfPack.catalog.forEach { m[it.slug] = it.defaultOn } }

    fun say(s: String) {
        logs.add(s)
        while (logs.size > 80) logs.removeAt(0)
    }

    val meta: InstanceMeta? get() = instances.firstOrNull { it.id == selectedId }
    val gameVersion: String get() = meta?.mcVersion ?: ""
    val loader: String get() = meta?.loaderType ?: ""

    fun select(id: String) { selectedId = id; prefs.edit().putString("inst", id).apply() }

    private fun needTarget(): File {
        val m = meta ?: error("Pilih instance dulu (buat instance di launcher jika belum ada)")
        return InstanceManager.instanceDir(ctx, m.id)
    }

    private fun needLoader(): String {
        needTarget()
        if (loader.isEmpty()) error("Instance vanilla tidak bisa pakai mod. Buat instance Fabric/Forge/NeoForge/Quilt dulu.")
        return loader
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
            val found = withContext(Dispatchers.IO) { InstanceManager.listInstances(ctx) }
            instances.clear(); instances.addAll(found)
            if (found.none { it.id == selectedId }) select(found.firstOrNull()?.id ?: "")
        }
    }

    fun applyProfile(p: FpsTuner.Profile) = runJob {
        val n = FpsTuner.apply(needTarget(), p)
        say("✓ Profil ${p.label}: $n opsi diubah (cadangan: options.txt.imzyy.bak)")
    }

    fun restoreOptions() = runJob {
        say(if (FpsTuner.restore(needTarget())) "✓ options.txt dikembalikan" else "Tidak ada cadangan options.txt")
    }

    fun installPack() = runJob {
        val l = needLoader()
        val mods = File(needTarget(), "mods")
        val slugs = PerfPack.catalog.filter { picked[it.slug] == true }.map { it.slug }
        if (slugs.isEmpty()) error("Pilih minimal satu mod")
        say("Memasang paket FPS untuk $gameVersion / $l…")
        val n = PerfPack.install(mods, gameVersion, l, slugs, this::say)
        say("✓ $n file baru terpasang di instance ${meta?.name}.")
    }

    fun removePack() = runJob {
        val n = PerfPack.uninstall(File(needTarget(), "mods"))
        say("✓ $n file paket FPS dihapus")
    }

    fun search() = runJob {
        val l = needLoader()
        val q = query.trim()
        if (q.isEmpty()) error("Ketik nama mod")
        val r = Modrinth.search(q, gameVersion, l)
        hits.clear(); hits.addAll(r)
        say("${r.size} hasil untuk \"$q\" ($gameVersion/$l)")
    }

    fun installHit(h: Hit) = runJob {
        val l = needLoader()
        val mods = File(needTarget(), "mods")
        say("Memasang ${h.title}…")
        val n = PerfPack.install(mods, gameVersion, l, listOf(h.id), this::say)
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
        val m = meta ?: error("Pilih instance dulu")
        val packMc = info.deps["minecraft"]
        if (packMc != null && packMc != m.mcVersion) error("Versi tidak cocok: modpack butuh Minecraft $packMc, instance ${m.mcVersion}")
        val packLoader = listOf("fabric-loader" to "fabric", "quilt-loader" to "quilt", "neoforge" to "neoforge", "forge" to "forge")
            .firstOrNull { info.deps.containsKey(it.first) }?.second
        if (packLoader != null && packLoader != m.loaderType) error("Loader tidak cocok: modpack butuh $packLoader, instance ${m.loaderType ?: "vanilla"}")
        Mrpack.install(ctx, uri, info, needTarget(), this::say) { progress = it }
    }

    fun controlsChanged() {
        ImzyyControls.save(ctx)
        ctlTick++
    }

    fun openKeyEditor() {
        KeyboardLayoutEditorActivity.start(ctx)
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
    LaunchedEffect(Unit) { s.refreshInstances(); ImzyyControls.refresh(ctx, force = true) }

    Box(Modifier.fillMaxSize().background(Ink.Black)) {
        AnimatedBackdrop()
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Box(Modifier.weight(1f)) {
                Crossfade(targetState = s.tab, animationSpec = tween(280), label = "tab") { t ->
                    when (t) {
                        0 -> HomeTab(s)
                        1 -> BoostTab(s)
                        2 -> ModsTab(s)
                        3 -> ImportTab(s)
                        else -> ControlsTab(s)
                    }
                }
            }
            NavBar(s)
        }
    }
}

@Composable
private fun NavBar(s: HubState) {
    val names = listOf("HOME", "BOOST", "MODS", "IMPORT", "KONTROL")
    Row(
        Modifier.fillMaxWidth().background(Ink.Black).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        names.forEachIndexed { i, n -> Chip(n, s.tab == i, compact = true) { s.tab = i } }
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
                        InkButton("CARI MOD", { s.tab = 2 }, Modifier.weight(1f), filled = false)
                        InkButton("IMPORT .MRPACK", { s.tab = 3 }, Modifier.weight(1f), filled = false)
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
private fun TargetCard(s: HubState) {
    Panel {
        Text("Instance", color = Ink.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        if (s.instances.isEmpty()) {
            Text("Belum ada instance. Buat satu di launcher (PLAY → instance baru), lalu muat ulang.", color = Ink.Sub, fontSize = 12.sp)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            s.instances.forEach { m -> Chip(m.name, s.selectedId == m.id) { s.select(m.id) } }
            Chip("↻ MUAT ULANG", false) { s.refreshInstances() }
        }
        val m = s.meta
        if (m != null) {
            Text(
                "Minecraft ${m.mcVersion}  •  ${(m.loaderType ?: "vanilla").uppercase()}" + (m.loaderVersion?.let { " $it" } ?: ""),
                color = Ink.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            )
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
        item { Reveal(1) { TargetCard(s) } }
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
private fun ModsTab(s: HubState) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Reveal(0) { Title("MODS", "Cari & pasang dari Modrinth") } }
        item { Reveal(1) { TargetCard(s) } }
        item {
            Reveal(2) {
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
        item { Reveal(1) { TargetCard(s) } }
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

@Composable
private fun SliderRow(title: String, valueText: String, value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Ink.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(valueText, color = Ink.Sub, fontSize = 13.sp)
        }
        InkSlider(value, min, max, onChange)
    }
}

@Composable
private fun ControlsTab(s: HubState) {
    val tick = s.ctlTick // recompose on change
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Reveal(0) { Title("KONTROL", "Kamera, mouse virtual & tombol") } }
        item {
            Reveal(1) {
                Panel {
                    Text("Kamera & sentuhan", color = Ink.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    SliderRow("Sensitivitas kamera", "%.2f".format(ImzyyControls.look) + if (tick >= 0) "x" else "", ImzyyControls.look, 0.3f, 3.5f) {
                        ImzyyControls.look = it; s.controlsChanged()
                    }
                    SliderRow("Ambang geser (anti salah tap)", "${ImzyyControls.dragSlop.toInt()} px", ImzyyControls.dragSlop, 6f, 50f) {
                        ImzyyControls.dragSlop = it; s.controlsChanged()
                    }
                    SliderRow("Tahan lama (hancurkan / klik kanan)", "${ImzyyControls.longPressMs} ms", ImzyyControls.longPressMs.toFloat(), 250f, 900f) {
                        ImzyyControls.longPressMs = it.toLong(); s.controlsChanged()
                    }
                }
            }
        }
        item {
            Reveal(2) {
                Panel {
                    Text("Mouse virtual (touchpad)", color = Ink.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Di menu & inventori: geser jari untuk memindahkan kursor, ketuk = klik kiri, tahan atau ketuk 2 jari = klik kanan. Saat bermain tetap kontrol kamera biasa.", color = Ink.Sub, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("AKTIF", ImzyyControls.touchpad) { ImzyyControls.touchpad = true; s.controlsChanged() }
                        Chip("MATI (sentuh langsung)", !ImzyyControls.touchpad) { ImzyyControls.touchpad = false; s.controlsChanged() }
                    }
                    SliderRow("Kecepatan kursor", "%.2f".format(ImzyyControls.cursorSpeed) + "x", ImzyyControls.cursorSpeed, 0.4f, 3.5f) {
                        ImzyyControls.cursorSpeed = it; s.controlsChanged()
                    }
                    SliderRow("Ukuran kursor", "%.2f".format(ImzyyControls.cursorSize) + "x", ImzyyControls.cursorSize, 0.6f, 2.5f) {
                        ImzyyControls.cursorSize = it; s.controlsChanged()
                    }
                }
            }
        }
        item {
            Reveal(3) {
                Panel {
                    Text("Tombol layar", color = Ink.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Atur posisi, ukuran, dan tombol di editor bawaan. Perubahan kontrol di atas berlaku saat game dimulai/dibuka lagi.", color = Ink.Sub, fontSize = 12.sp)
                    InkButton("BUKA EDITOR TOMBOL", { s.openKeyEditor() })
                    InkButton("RESET KONTROL KE DEFAULT", { ImzyyControls.reset(s.ctx); s.ctlTick++ }, filled = false)
                }
            }
        }
    }
}
