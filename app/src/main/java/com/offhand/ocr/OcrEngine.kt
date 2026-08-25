package com.offhand.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/** On-device text recognition (ML Kit bundled Latin model — no network). */
object OcrEngine {

    suspend fun recognizeFile(context: Context, path: String): String? =
        suspendCancellableCoroutine { cont ->
            try {
                val image = InputImage.fromFilePath(context, Uri.fromFile(File(path)))
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    .process(image)
                    .addOnSuccessListener { result ->
                        cont.resume(result.text.trim().takeIf { it.isNotBlank() })
                    }
                    .addOnFailureListener { cont.resume(null) }
            } catch (e: Exception) {
                cont.resume(null)
            }
        }
}
