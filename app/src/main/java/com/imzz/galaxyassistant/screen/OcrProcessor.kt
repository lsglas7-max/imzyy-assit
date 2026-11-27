package com.imzz.galaxyassistant.screen

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Locale

object OcrProcessor {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    /**
     * [onResult] menerima (teks mentah, layout). Layout = satu baris per teks terbaca:
     * "x,y teks" dengan x,y = titik tengah ternormalisasi 0..1. Dipakai AI teks-saja untuk menentukan titik tap.
     */
    fun recognize(bitmap: Bitmap, onResult: (String, String) -> Unit) {
        val w = bitmap.width.toFloat().coerceAtLeast(1f)
        val h = bitmap.height.toFloat().coerceAtLeast(1f)
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                val layout = result.textBlocks
                    .flatMap { it.lines }
                    .mapNotNull { line ->
                        val box = line.boundingBox ?: return@mapNotNull null
                        String.format(Locale.US, "%.2f,%.2f %s", box.centerX() / w, box.centerY() / h, line.text.take(60))
                    }
                    .take(70)
                    .joinToString("\n")
                onResult(result.text.take(12_000), layout)
            }
            .addOnFailureListener { onResult("", "") }
    }
}
