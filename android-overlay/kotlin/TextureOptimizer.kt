package id.imzyy.xl

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class TextureOptimizationResult(
    val scanned: Int,
    val optimized: Int,
    val savedBytes: Long,
    val skipped: Int,
)

/** Recompresses PNG IDAT streams without decoding pixels or changing ancillary chunks. */
object TextureOptimizer {
    private const val MAX_PNG_BYTES = 32L * 1024L * 1024L
    private const val MAX_INFLATED_PNG_BYTES = 128L * 1024L * 1024L
    private const val MAX_PACK_ZIP_BYTES = 512L * 1024L * 1024L
    private val pngSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
    private val ascii: Charset = Charsets.US_ASCII

    private data class Chunk(val type: String, val start: Int, val length: Int, val dataStart: Int, val dataLength: Int)

    fun optimize(instance: File, say: (String) -> Unit): TextureOptimizationResult {
        val packs = File(instance, "resourcepacks")
        require(packs.isDirectory) { "Folder resourcepacks belum ditemukan" }
        val backupRoot = File(instance, ".imzyy-backups/textures")
        var scanned = 0
        var optimized = 0
        var skipped = 0
        var saved = 0L

        val children = packs.listFiles() ?: emptyArray()
        for (pack in children) {
            if (pack.isDirectory) {
                val rootPath = pack.canonicalPath + File.separator
                for (png in pack.walkTopDown().filter { it.isFile && it.extension.equals("png", true) }) {
                    if (++scanned > 20_000) {
                        skipped++
                        say("Batas 20.000 tekstur tercapai; sisanya dilewati")
                        break
                    }
                    if (!png.canonicalPath.startsWith(rootPath)) { skipped++; continue }
                    val sourceBytes = png.length()
                    if (sourceBytes <= 0L || sourceBytes > MAX_PNG_BYTES) { skipped++; continue }
                    val raw = runCatching { png.readBytes() }.getOrNull()
                    if (raw == null) { skipped++; continue }
                    val encoded = recompressPng(raw)
                    if (encoded == null || encoded.size >= raw.size) { skipped++; continue }

                    val backup = File(File(backupRoot, pack.name), png.relativeTo(pack).path)
                    backup.parentFile?.mkdirs()
                    if (!backup.exists()) png.copyTo(backup, overwrite = false)
                    val temp = File(png.parentFile, png.name + ".imzyy-tmp")
                    try {
                        temp.writeBytes(encoded)
                        if (temp.length() < png.length() && temp.renameTo(png)) {
                            optimized++
                            saved += (raw.size - encoded.size)
                        } else {
                            temp.delete()
                        }
                    } catch (_: Exception) {
                        temp.delete()
                        skipped++
                    }
                }
            } else if (pack.isFile && pack.extension.lowercase() in setOf("zip", "mcpack")) {
                scanned++
                val result = optimizeZip(pack, backupRoot)
                if (result != null) {
                    optimized += result.first
                    saved += result.second
                    if (result.first == 0) skipped++
                } else {
                    skipped++
                }
            }
        }
        return TextureOptimizationResult(scanned, optimized, saved, skipped)
    }

    /** Restore all saved originals to their resource-pack locations. Backups are retained. */
    fun restore(instance: File): Int {
        val backupRoot = File(instance, ".imzyy-backups/textures")
        if (!backupRoot.isDirectory) return 0
        val packs = File(instance, "resourcepacks")
        var restored = 0
        val archiveBackups = File(backupRoot, "archives")
        if (archiveBackups.isDirectory) {
            (archiveBackups.listFiles() ?: emptyArray()).filter { it.isFile }.forEach { backup ->
                val destination = File(packs, backup.name)
                destination.parentFile?.mkdirs()
                backup.copyTo(destination, overwrite = true)
                restored++
            }
        }
        (backupRoot.listFiles() ?: emptyArray()).filter { it.isDirectory && it.name != "archives" }.forEach { packBackup ->
            val destinationRoot = File(packs, packBackup.name)
            val base = packBackup.canonicalPath + File.separator
            for (backup in packBackup.walkTopDown().filter { it.isFile }) {
                if (!backup.canonicalPath.startsWith(base)) continue
                val destination = File(destinationRoot, backup.relativeTo(packBackup).path)
                destination.parentFile?.mkdirs()
                backup.copyTo(destination, overwrite = true)
                restored++
            }
        }
        return restored
    }

    /** PNG IDAT data is recompressed; pixel scanlines, palette, alpha and metadata chunks remain unchanged. */
    private fun recompressPng(raw: ByteArray): ByteArray? {
        if (raw.size < pngSignature.size || !raw.copyOfRange(0, 8).contentEquals(pngSignature)) return null
        val chunks = ArrayList<Chunk>()
        val idat = ByteArrayOutputStream()
        var offset = 8
        var sawIhdr = false
        var sawIend = false
        while (offset + 12 <= raw.size) {
            val dataLengthLong = readUInt32(raw, offset)
            if (dataLengthLong > Int.MAX_VALUE) return null
            val dataLength = dataLengthLong.toInt()
            val fullLength = 12L + dataLength
            if (offset.toLong() + fullLength > raw.size.toLong()) return null
            val type = String(raw, offset + 4, 4, ascii)
            val dataStart = offset + 8
            chunks.add(Chunk(type, offset, fullLength.toInt(), dataStart, dataLength))
            when (type) {
                "IHDR" -> sawIhdr = true
                "IDAT" -> idat.write(raw, dataStart, dataLength)
                "IEND" -> { sawIend = true; offset += fullLength.toInt(); break }
            }
            offset += fullLength.toInt()
        }
        if (!sawIhdr || !sawIend || idat.size() == 0) return null
        if (offset != raw.size) return null

        val inflated = ByteArrayOutputStream()
        try {
            InflaterInputStream(ByteArrayInputStream(idat.toByteArray())).use { input ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > MAX_INFLATED_PNG_BYTES) return null
                    inflated.write(buffer, 0, n)
                }
            }
        } catch (_: Exception) {
            return null
        }

        val recompressed = ByteArrayOutputStream()
        try {
            DeflaterOutputStream(recompressed, Deflater(Deflater.BEST_COMPRESSION)).use { it.write(inflated.toByteArray()) }
        } catch (_: Exception) {
            return null
        }
        val idatBytes = recompressed.toByteArray()
        if (idatBytes.size >= idat.size()) return null

        val output = ByteArrayOutputStream(raw.size - idat.size() + idatBytes.size + 12)
        output.write(pngSignature)
        var inserted = false
        for (chunk in chunks) {
            if (chunk.type == "IDAT") {
                if (!inserted) {
                    writeChunk(output, "IDAT", idatBytes)
                    inserted = true
                }
            } else {
                output.write(raw, chunk.start, chunk.length)
            }
        }
        return output.toByteArray()
    }

    private fun readUInt32(bytes: ByteArray, offset: Int): Long {
        return ((bytes[offset].toLong() and 0xff) shl 24) or
            ((bytes[offset + 1].toLong() and 0xff) shl 16) or
            ((bytes[offset + 2].toLong() and 0xff) shl 8) or
            (bytes[offset + 3].toLong() and 0xff)
    }

    private fun writeChunk(output: ByteArrayOutputStream, type: String, data: ByteArray) {
        writeUInt32(output, data.size.toLong())
        val typeBytes = type.toByteArray(ascii)
        output.write(typeBytes)
        output.write(data)
        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(data)
        writeUInt32(output, crc.value)
    }

    private fun writeUInt32(output: ByteArrayOutputStream, value: Long) {
        output.write(((value ushr 24) and 0xff).toInt())
        output.write(((value ushr 16) and 0xff).toInt())
        output.write(((value ushr 8) and 0xff).toInt())
        output.write((value and 0xff).toInt())
    }

    /** Repackages ZIP packs only when the resulting archive is smaller; originals are backed up. */
    private fun optimizeZip(pack: File, backupRoot: File): Pair<Int, Long>? {
        if (pack.length() <= 0L || pack.length() > MAX_PACK_ZIP_BYTES) return null
        val temp = File(pack.parentFile, pack.name + ".imzyy-tmp")
        var changed = 0
        return try {
            ZipFile(pack).use { input ->
                ZipOutputStream(FileOutputStream(temp)).use { output ->
                    output.setLevel(Deflater.BEST_COMPRESSION)
                    val entries = input.entries()
                    while (entries.hasMoreElements()) {
                        val original = entries.nextElement()
                        val entry = ZipEntry(original.name).apply {
                            time = original.time
                            comment = original.comment
                        }
                        output.putNextEntry(entry)
                        input.getInputStream(original).use { stream ->
                            if (!original.isDirectory && original.name.endsWith(".png", true) && original.size > 0L && original.size <= MAX_PNG_BYTES) {
                                val raw = stream.readBytes()
                                val encoded = recompressPng(raw)
                                if (encoded != null && encoded.size < raw.size) {
                                    output.write(encoded)
                                    changed++
                                } else {
                                    output.write(raw)
                                }
                            } else {
                                stream.copyTo(output)
                            }
                        }
                        output.closeEntry()
                    }
                }
            }
            val newSize = temp.length()
            if (changed > 0 && newSize > 0L && newSize < pack.length()) {
                val backupDir = File(backupRoot, "archives")
                backupDir.mkdirs()
                val backup = File(backupDir, pack.name)
                if (!backup.exists()) pack.copyTo(backup, overwrite = false)
                if (!temp.renameTo(pack)) {
                    temp.copyTo(pack, overwrite = true)
                    temp.delete()
                }
                changed to (backup.length() - pack.length())
            } else {
                temp.delete()
                0 to 0L
            }
        } catch (_: Exception) {
            temp.delete()
            null
        }
    }
}
