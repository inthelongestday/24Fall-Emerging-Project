package com.example.pj4combined.cameraView

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.pj4combined.cameraInference.DetectionResult
import com.example.pj4combined.cameraInference.DetectorListener
import com.example.pj4combined.cameraInference.PersonClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


private lateinit var bitmapBuffer: Bitmap



@Composable
fun CameraScreen() {
    // Obtain the current context and lifecycle owner
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val detectionResults = remember { mutableStateOf<DetectionResult?>(null) }
    val errorMessage = remember { mutableStateOf<String?>(null) }

    val listener = remember {
        DetectorListener(
            onDetectionResult = { results -> detectionResults.value = results },
            onDetectionError = { error -> errorMessage.value = error }
        )
    }

    // Initialize our background executor
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    val resolutionSelector =  ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build()
    val preview = remember { Preview.Builder().setResolutionSelector(resolutionSelector).build() }
    val imageAnalyzer = remember {ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build() }


    val previewView = remember { PreviewView(context) }

    LaunchedEffect(Unit) {

        val personClassifierGPU = PersonClassifier()
        withContext(Dispatchers.IO) {
            personClassifierGPU.initialize(context, useGPU = true)
        }
        personClassifierGPU.setDetectorListener(listener)

        preview.surfaceProvider = previewView.surfaceProvider

        // The analyzer can then be assigned to the instance
        imageAnalyzer.setAnalyzer(cameraExecutor) { image ->
            detectObjects(image, personClassifierGPU)
            // Close the image proxy
            image.close()
        }

        previewView.setScaleType(PreviewView.ScaleType.FIT_CENTER)

        val cameraProvider = context.getCameraProvider()
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT).build(),
            preview,
            imageAnalyzer
        )
        Log.d("CS330", "Camera bound")
    }

    if (detectionResults.value != null) {
        // TODO:
        //  Choose your inference time threshold
        val inferenceTimeThreshold = 300 // Adjusted after experiments, average without LLM running : ~100ms

        if (detectionResults.value!!.inferenceTime > inferenceTimeThreshold) {
            Log.d("CS330", "GPU too slow, switching to CPU start")
            // TODO:
            //  Create new classifier to be run on CPU with 2 threads

            val personClassifier2CPU = PersonClassifier()
            personClassifier2CPU.initialize(context, 2, useGPU = false)
            personClassifier2CPU.setDetectorListener(listener)

            // TODO:
            //  Set imageAnalyzer to use the new classifier

            val cpu2Executor = remember { Executors.newFixedThreadPool(2) }
            imageAnalyzer.setAnalyzer(cpu2Executor) { image ->
                detectObjects(image, personClassifier2CPU)
                // 위에 CPU 2 threads를 사용하는 새로운 classifier 사용
                image.close()
            }

            Log.d("CS330", "GPU too slow, switching to CPU done")
        }
    }


    // Display the Camera Preview
    ShowCameraPreview( previewView )
    Text(buildAnnotatedString {
            if (detectionResults.value != null) {
                val detectionResult = detectionResults.value!!
                withStyle(style = SpanStyle(color = Color.Blue, fontSize = 20.sp)) {
                    append("inference time ${detectionResult.inferenceTime}\n")
                }
                if (detectionResult.detections.find { it.categories[0].label == "person" } != null) {
                    withStyle(style = SpanStyle(color = Color.Red, fontSize = 40.sp)) {
                        append("human detected")
                    }
                }
            }
    })
}

@Composable
fun ShowCameraPreview(previewView: PreviewView) {
    AndroidView(
        { previewView },
        modifier = Modifier.fillMaxSize()
    )
}

fun detectObjects(image: ImageProxy, personClassifier: PersonClassifier) {
    if (!::bitmapBuffer.isInitialized) {
        // The image rotation and RGB image buffer are initialized only once
        // the analyzer has started running
        bitmapBuffer = Bitmap.createBitmap(
            image.width,
            image.height,
            Bitmap.Config.ARGB_8888
        )
    }
    // Copy out RGB bits to the shared bitmap buffer
    image.use { bitmapBuffer.copyPixelsFromBuffer(image.planes[0].buffer) }
    val imageRotation = image.imageInfo.rotationDegrees

    // Pass Bitmap and rotation to the object detector helper for processing and detection
    personClassifier.detect(bitmapBuffer, imageRotation)
}

suspend fun Context.getCameraProvider(): ProcessCameraProvider = suspendCoroutine { continuation ->
    ProcessCameraProvider.getInstance(this).also { cameraProvider ->
        cameraProvider.addListener({
            continuation.resume(cameraProvider.get())
        }, ContextCompat.getMainExecutor(this))
    }
}