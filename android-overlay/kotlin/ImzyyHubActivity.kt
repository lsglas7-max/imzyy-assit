package id.imzyy.xl

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.ui.text.TextStyle
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
        ImzyyWindow.install(application)
        setContent { ProvideTextStyle(TextStyle(fontFamily = ImzyyFonts.family)) { HubApp() } }
    }
}

class HubState(val ctx: Context, val scope: CoroutineScope) {
    private val prefs = ctx.getSharedPreferences("imzyy_hub", Context.MODE_PRIVATE)

    var tab by mutableStateOf(0)
    var selectedId by mutableStateOf(prefs.getString("inst", "") ?: "")
    var ctlTick by mutableStateOf(0)
    var rtTick by mutableStateOf(0)
    var busy by mutableStateOf(false)
    var progress by mutableStateOf(0f)
    var query by mutableStateOf("")
    var mrInfo by mutableStateOf<MrInfo?>(null)
    var mrUri by mutableStateOf<Uri?>(null)
    var smooth by mutableStateOf(prefs.getBoolean("smooth", false))
    var texLevel by mutableStateOf(1)
    var antiLag by mutableStateOf(prefs.getBoolean("antilag", false))
    /** 0 = low, 1 = mid, 2 = flagship (by total RAM). */
    val tier: Int = run {
        val am = ctx.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val gb = mi.totalMem / 1073741824.0
        if (am.isLowRamDevice || gb <= 4.5) 0 else if (gb <= 8.5) 1 else 2
    }
    val tierName: String get() = listOf("LOW", "MID", "FLAGSHIP")[tier]

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

    /** One picker for everything: .mrpack, a single .jar, or a .zip that holds mods. */
    fun pickFile(uri: Uri) = runJob {
        val name = ModImport.displayName(ctx, uri)
        if (name.endsWith(".jar", ignoreCase = true)) {
            val n = ModImport.install(ctx, uri, File(needTarget(), "mods"), this::say)
            say("✓ $n mod dipasang ke ${meta?.name}")
            return@runJob
        }
        val asPack = try {
            Mrpack.readIndex(ctx, uri)
        } catch (e: Exception) {
            if (name.endsWith(".mrpack", ignoreCase = true)) throw e
            null
        }
        if (asPack != null) {
            mrUri = uri
            mrInfo = asPack
            say("Modpack: ${asPack.name} ${asPack.version} — ${asPack.files.size} file")
        } else {
            val n = ModImport.install(ctx, uri, File(needTarget(), "mods"), this::say)
            say("✓ $n mod dari zip dipasang ke ${meta?.name}")
        }
    }

    fun downloadRuntime(major: Int) = runJob {
        say("Mengunduh Java $major (~${ImzyyRuntimes.sizeMb(major)} MB)…")
        ImzyyRuntimes.ensure(ctx, major) { progress = it }
        rtTick++
        say("✓ Java $major siap")
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


    /** Lite: first launch of each instance gets the lightweight options.txt profile once
     *  (original is backed up; later changes by the user are never overwritten). */
    private fun liteAutoTune() {
        if (ImzyyConfig.VARIANT != "lite") return
        val m = meta ?: return
        val key = "lite_tuned_" + m.id
        if (prefs.getBoolean(key, false)) return
        try {
            FpsTuner.apply(InstanceManager.instanceDir(ctx, m.id), FpsTuner.Profile.SAVER)
            prefs.edit().putBoolean(key, true).apply()
            say("✓ Lite: profil HEMAT FPS dipasang otomatis untuk instance ini")
        } catch (e: Exception) {
            say("Lite auto-tune dilewati: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    fun switchSmooth(on: Boolean) {
        smooth = on
        prefs.edit().putBoolean("smooth", on).apply()
        if (on) runJob {
            val n = FpsTuner.applyValues(needTarget(), FpsTuner.SMOOTH)
            say("✓ Anti drop FPS & stutter aktif ($n opsi). Dipasang ulang otomatis tiap PLAY.")
        } else say("Anti drop FPS & stutter dimatikan. Pakai KEMBALIKAN OPTIONS ASLI jika mau membatalkan opsi yang sudah ditulis.")
    }

    fun switchAntiLag(on: Boolean) {
        antiLag = on
        prefs.edit().putBoolean("antilag", on).apply()
        if (on) runJob {
            val n = FpsTuner.applyValues(needTarget(), FpsTuner.lagValues(tier))
            say("✓ Anti lag gerak aktif untuk HP $tierName ($n opsi). Dipasang ulang otomatis tiap PLAY.")
        } else say("Anti lag gerak dimatikan.")
    }

    fun installVoxy() = runJob {
        val l = needLoader()
        if (l != "fabric" && l != "quilt") error("Voxy hanya untuk instance Fabric/Quilt")
        val mods = File(needTarget(), "mods")
        say("Memasang preset Voxy (Fabric API, Sodium, Voxy, Mod Menu)…")
        val n = PerfPack.install(mods, gameVersion, l, PerfPack.VOXY_PRESET, this::say)
        say("✓ $n file dipasang. Buka Mod Menu di dalam game untuk mengatur Voxy. Voxy butuh renderer OpenGL 4.x (MobileGlues/Zink), tidak jalan di GL4ES.")
    }

    fun compressTextures() = runJob {
        val dir = needTarget()
        val minSide = TextureCompress.MIN_SIDE[texLevel]
        val packs = File(dir, "resourcepacks")
        packs.mkdirs()
        val srcs = packs.listFiles(java.io.FileFilter { it.isFile && it.name.endsWith(".zip") && !it.name.endsWith(TextureCompress.SUFFIX) })?.toList().orEmpty()
        if (srcs.isNotEmpty()) {
            for (src in srcs) {
                say("Mengompres ${src.name}…")
                val dest = File(packs, src.name.removeSuffix(".zip") + TextureCompress.SUFFIX)
                val r = TextureCompress.compressPack(src, dest, minSide) { progress = it }
                say("✓ ${dest.name}: ${Net.fmtBytes(r.before)} → ${Net.fmtBytes(r.after)} (${r.changed} tekstur)")
            }
            say("Aktifkan versi -imzyy-lite di Options > Resource Packs.")
        } else {
            val jar = TextureCompress.findClientJar(ctx, dir, gameVersion) ?: error("Jar Minecraft $gameVersion tidak ditemukan; jalankan game sekali dulu atau taruh resource pack di folder resourcepacks")
            say("Membuat pack tekstur ringan dari ${jar.name}…")
            val dest = File(packs, TextureCompress.VANILLA_PACK)
            val r = TextureCompress.buildVanillaPack(jar, dest, minSide) { progress = it }
            TextureCompress.enablePack(dir, dest.name)
            say("✓ ${r.changed} tekstur diperkecil, pack ${Net.fmtBytes(r.after)} diaktifkan otomatis")
        }
    }

    fun cleanTextures() = runJob {
        say("✓ ${TextureCompress.clean(File(needTarget(), "resourcepacks"))} file hasil kompres dihapus")
    }

    private fun openLauncher() {
        ctx.startActivity(Intent().setClassName(ctx.packageName, ImzyyConfig.MAIN_ACTIVITY))
    }

    /** Blocking (worker thread). Verifies the selected instance's asset objects and re-downloads what is missing. */
    private fun assetJob(): AssetRepair.Report? {
        val m = meta ?: return null
        say("Memeriksa asset Minecraft ${m.mcVersion}…")
        val r = AssetRepair.repair(ctx, InstanceManager.instanceDir(ctx, m.id), m.assetIndexId) { progress = it }
        when {
            r.note != null -> say("! ${r.note}")
            r.bad == 0 -> say("✓ Asset lengkap (${r.total} file)")
            r.failed == 0 -> say("✓ ${r.fixed} asset yang hilang/rusak diunduh ulang")
            else -> say("✗ ${r.failed} dari ${r.bad} asset gagal diunduh: ${r.error ?: "tidak diketahui"}. Cek internet lalu tekan lagi.")
        }
        return r
    }

    fun repairAssets() = runJob { assetJob() }

    fun play() {
        if (smooth) runCatching { meta?.let { FpsTuner.applyValues(InstanceManager.instanceDir(ctx, it.id), FpsTuner.SMOOTH) } }
        if (antiLag) runCatching { meta?.let { FpsTuner.applyValues(InstanceManager.instanceDir(ctx, it.id), FpsTuner.lagValues(tier)) } }
        liteAutoTune()
        if (meta == null || busy) { openLauncher(); return }
        busy = true
        progress = 0f
        scope.launch {
            var block = false
            try {
                val r = withContext(Dispatchers.IO) { assetJob() }
                block = r != null && r.failed > 0
            } catch (e: Exception) {
                say("Cek asset dilewati: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                busy = false
            }
            if (!block) openLauncher()
        }
    }
}

@Composable
fun HubApp() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s = remember { HubState(ctx, scope) }
    LaunchedEffect(Unit) { s.refreshInstances(); withContext(Dispatchers.IO) { ImzyyControls.refresh(ctx, force = true); ImzyyPad.load(ctx) } }

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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Reveal(0) { Spacer(Modifier.height(24.dp)) } }
        item {
            Reveal(1) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ShimmerTitle("IMZYY XL")
                    Text(
                        if (ImzyyConfig.VARIANT == "lite") "LITE  •  Minecraft Java  •  ARM64" else "Minecraft Java  •  ARM64",
                        color = Ink.Sub, fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp,
                    )
                }
            }
        }
        item { Reveal(2) { GlowButton("▶  PLAY", { s.play() }) } }
        item {
            Reveal(2) {
                Panel {
                    Text("Perbaiki asset", color = Ink.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Dicek otomatis tiap PLAY. Pakai tombol ini kalau game crash NoSuchFileException di assets/objects.", color = Ink.Sub, fontSize = 12.sp)
                    InkButton("PERIKSA & PERBAIKI ASSET", { s.repairAssets() }, filled = false, enabled = !s.busy)
                }
            }
        }
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
        item { Reveal(4) { RuntimeCard(s) } }
        item { LogCard(s) }
    }
}

@Composable
private fun RuntimeCard(s: HubState) {
    val tick = s.rtTick
    Panel {
        Text("Runtime Java", color = Ink.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(
            "Versi Minecraft memilih Java otomatis. Yang belum terpasang diunduh sekali saja (tanpa perlu uninstall).",
            color = Ink.Sub, fontSize = 12.sp,
        )
        ImzyyRuntimes.majors.forEach { m ->
            val ready = tick >= 0 && ImzyyRuntimes.installed(s.ctx, m)
            val can = ImzyyRuntimes.downloadable(m)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Java $m", color = Ink.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                if (ready) {
                    Chip("TERPASANG", true, compact = true) {}
                } else if (can) {
                    Chip("UNDUH ${ImzyyRuntimes.sizeMb(m)} MB", false, compact = true) { if (!s.busy) s.downloadRuntime(m) }
                } else {
                    Text("tidak tersedia", color = Ink.Sub, fontSize = 12.sp)
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
        item {
            Reveal(4) {
                Panel {
                    Text("Anti drop FPS & stutter", color = Ink.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Kunci FPS stabil, matikan vsync, ringankan update chunk & biome blend. Dipasang ulang otomatis setiap kamu tekan PLAY.", color = Ink.Sub, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("AKTIF", s.smooth) { s.switchSmooth(true) }
                        Chip("MATI", !s.smooth) { s.switchSmooth(false) }
                    }
                }
            }
        }
        item {
            Reveal(4) {
                Panel {
                    Text("Cegah gerak sedikit lag", color = Ink.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("HP kamu terdeteksi ${s.tierName}. " + (if (s.tier == 2) "Flagship tidak wajib, tapi boleh diaktifkan agar lebih mulus." else "Disarankan AKTIF: render distance, bayangan, AO dan awan diringankan supaya gerak tidak patah-patah."), color = Ink.Sub, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("AKTIF", s.antiLag) { s.switchAntiLag(true) }
                        Chip("MATI", !s.antiLag) { s.switchAntiLag(false) }
                    }
                }
            }
        }
        item {
            Reveal(5) {
                Panel {
                    Text("Voxy (LOD jarak jauh)", color = Ink.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Pasang Fabric API + Sodium + Voxy + Mod Menu sekali tekan. Atur Voxy langsung di dalam game lewat Mod Menu. Voxy butuh renderer OpenGL 4.x (MobileGlues/Zink); di OpenGL ES murni atau GL4ES tidak bisa jalan.", color = Ink.Sub, fontSize = 12.sp)
                    InkButton("PASANG PRESET VOXY", { s.installVoxy() }, enabled = !s.busy)
                }
            }
        }
        item {
            Reveal(6) {
                Panel {
                    Text("Kompres tekstur", color = Ink.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Memperkecil PNG jadi setengah sisi (memori tekstur sekitar 4x lebih kecil). Pack di folder resourcepacks dikompres; kalau kosong dibuat pack ringan dari Minecraft vanilla. Animasi tidak disentuh.", color = Ink.Sub, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextureCompress.LEVEL_NAMES.forEachIndexed { i, n -> Chip(n, s.texLevel == i) { s.texLevel = i } }
                    }
                    InkButton("KOMPRES TEKSTUR", { s.compressTextures() }, enabled = !s.busy)
                    InkButton("HAPUS HASIL KOMPRES", { s.cleanTextures() }, filled = false, enabled = !s.busy)
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
        if (uri != null) s.pickFile(uri)
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Reveal(0) { Title("IMPORT", "Modpack .mrpack, mod .jar, atau zip berisi mods") } }
        item { Reveal(1) { TargetCard(s) } }
        item {
            Reveal(2) {
                Panel {
                    InkButton("PILIH FILE (.mrpack / .jar / .zip)", { picker.launch(arrayOf("*/*")) }, enabled = !s.busy)
                    val info = s.mrInfo
                    if (info != null) {
                        Text(info.name + "  " + info.version, color = Ink.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        if (info.summary.isNotEmpty()) Text(info.summary, color = Ink.Sub, fontSize = 12.sp, maxLines = 3)
                        Text(info.deps.entries.joinToString("  •  ") { it.key + " " + it.value }, color = Ink.Text, fontSize = 12.sp)
                        Text("${info.files.size} file (${Net.fmtBytes(info.files.sumOf { it.size })}) akan diunduh dan diverifikasi hash-nya.", color = Ink.Sub, fontSize = 12.sp)
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
        item {
            Reveal(4) {
                Panel {
                    Text("Tombol IMZYY (di dalam game)", color = Ink.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("WASD, klik kiri/kanan, dan tombol lain muncul di game, termasuk sebelum membuat world. Di dalam game ketuk tombol PAD (atas) lalu geser tombol untuk mengatur posisi, ketuk SIMPAN.", color = Ink.Sub, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("AKTIF", ImzyyPad.enabled) { ImzyyPad.enabled = true; ImzyyPad.save(s.ctx) }
                        Chip("MATI", !ImzyyPad.enabled) { ImzyyPad.enabled = false; ImzyyPad.save(s.ctx) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("SELALU TAMPIL", ImzyyPad.alwaysShow) { ImzyyPad.alwaysShow = true; ImzyyPad.save(s.ctx) }
                        Chip("SAAT MENU SAJA", !ImzyyPad.alwaysShow) { ImzyyPad.alwaysShow = false; ImzyyPad.save(s.ctx) }
                    }
                    SliderRow("Ukuran tombol", "%.2f".format(ImzyyPad.size) + "x", ImzyyPad.size, 0.7f, 1.6f) { ImzyyPad.size = it; ImzyyPad.save(s.ctx) }
                    SliderRow("Kepekatan tombol", "%.0f".format(ImzyyPad.opacity * 100) + "%", ImzyyPad.opacity, 0.3f, 1f) { ImzyyPad.opacity = it; ImzyyPad.save(s.ctx) }
                }
            }
        }
        item {
            Reveal(5) {
                Panel {
                    Text("Daftar tombol", color = Ink.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Ketuk mode untuk mengganti: TEKAN (aktif selagi ditekan), TAHAN (ketuk sekali = terkunci, ketuk lagi = lepas), KLIK / TAHAN LAMA (ketuk = klik, tahan lama = terkunci).", color = Ink.Sub, fontSize = 12.sp)
                    for (b in ImzyyPad.buttons.toList()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(b.key, color = Ink.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Chip(ImzyyPad.modeName(b.mode), true, compact = true) { ImzyyPad.cycleMode(s.ctx, b.id) }
                            Chip("HAPUS", false, compact = true) { ImzyyPad.remove(s.ctx, b.id) }
                        }
                    }
                }
            }
        }
        item {
            Reveal(6) {
                Panel {
                    Text("Tambah tombol", color = Ink.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        for (k in PadKeys.order) Chip(k, false, compact = true) { ImzyyPad.add(s.ctx, k) }
                    }
                    InkButton("RESET TOMBOL IMZYY", { ImzyyPad.reset(s.ctx) }, filled = false)
                }
            }
        }
    }
}
