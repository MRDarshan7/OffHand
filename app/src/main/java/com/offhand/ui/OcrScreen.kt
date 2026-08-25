package com.offhand.ui

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File

/**
 * Photo -> recognised text. The result lands as a prefilled note draft on the
 * confirmation card (M5b).
 */
@Composable
fun OcrScreen(
    onCaptured: (photoPath: String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember { ImageCapture.Builder().build() }
    var busy by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { view ->
                    val future = ProcessCameraProvider.getInstance(ctx)
                    future.addListener({
                        try {
                            val provider = future.get()
                            val preview = Preview.Builder().build()
                                .also { it.surfaceProvider = view.surfaceProvider }
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageCapture,
                            )
                        } catch (_: Exception) {
                            // No camera (some emulators): the debug hook still
                            // exercises recognition end-to-end.
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        OutlinedButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
        ) { Text("Back") }

        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp),
            )
        }

        Button(
            onClick = {
                if (busy) return@Button
                busy = true
                val photo = File.createTempFile("ocr", ".jpg", context.cacheDir)
                imageCapture.takePicture(
                    ImageCapture.OutputFileOptions.Builder(photo).build(),
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            busy = false
                            onCaptured(photo.absolutePath)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            busy = false
                        }
                    },
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp),
        ) { Text("Capture text") }
    }
}
