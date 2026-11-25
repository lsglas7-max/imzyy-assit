package com.imzyy.xllauncher

/** Reads exactly buffer.size bytes; Kotlin's InputStream has no built-in readFully. */
internal fun java.io.InputStream.readFully(buffer: ByteArray) {
    var offset = 0
    while (offset < buffer.size) {
        val n = read(buffer, offset, buffer.size - offset)
        if (n < 0) throw java.io.EOFException("File backup terpotong atau rusak.")
        offset += n
    }
}
