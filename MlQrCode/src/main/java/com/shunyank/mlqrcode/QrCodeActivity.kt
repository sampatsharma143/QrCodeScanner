package com.shunyank.mlqrcode

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.widget.TextView
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class QrCodeActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSION_CAMERA_REQUEST = 1
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0
    }


    private lateinit var cameraLightListener: SensorEventListener
    lateinit var pvScan: androidx.camera.view.PreviewView
    lateinit var scanResultTextView: TextView
    lateinit var camera: Camera
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraSelector: CameraSelector? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var previewUseCase: Preview? = null
    private var analysisUseCase: ImageAnalysis? = null
    private val screenAspectRatio: Int
        get() {
            // Get screen metrics used to setup camera for full screen resolution
            val metrics = DisplayMetrics().also { pvScan.display?.getRealMetrics(it) }
            return aspectRatio(metrics.widthPixels, metrics.heightPixels)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_code)
        scanResultTextView = findViewById(R.id.scanResultTextView)
        pvScan = findViewById(R.id.scanPreview)

         cameraLightListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val ambientLight = event.values[0]
                Log.e("bar","-> "+ambientLight)

                if (ambientLight < 28) { // Check if the ambient light is low
                    // Enable the camera flash
//                            cameraControl.enableTorch(true)
                    camera.cameraControl.enableTorch(true)

                    Log.e("bar","low")
                } else {
                    Log.e("bar","high")
                    camera.cameraControl.enableTorch(false)

                    // Disable the camera flash
//                            cameraControl.enableTorch(false)
                }
                val factory = pvScan.meteringPointFactory
                val point = factory.createPoint(pvScan.width / 2f, pvScan.height / 2f)
                val action = FocusMeteringAction.Builder(point).build()
                camera?.cameraControl?.startFocusAndMetering(action)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // Do nothing
            }
        }
        setupCamera()
    }
    private fun setupCamera() {
        cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(
            {
                try {
                    cameraProvider = cameraProviderFuture.get()
                    if (isCameraPermissionGranted()) {
                        bindCameraUseCases()
                    } else {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            requestPermissions(
                                arrayOf(Manifest.permission.CAMERA),
                                PERMISSION_CAMERA_REQUEST
                            )
                        }
                    }
                } catch (e: ExecutionException) {
                    // Handle any errors (including cancellation) here.
                    Log.e("QrScanViewModel", "Unhandled exception", e)
                } catch (e: InterruptedException) {
                    Log.e("QrScanViewModel", "Unhandled exception", e)
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    private fun bindCameraUseCases() {
        bindPreviewUseCase()
        bindAnalyseUseCase()
    }

    private fun bindPreviewUseCase() {
        if (cameraProvider == null) {
            return
        }
        if (previewUseCase != null) {
            cameraProvider?.unbind(previewUseCase)
        }

        previewUseCase = Preview.Builder()
            .setTargetAspectRatio(screenAspectRatio)
            .setTargetRotation(pvScan.display.rotation)
            .build()

        previewUseCase?.setSurfaceProvider(pvScan.surfaceProvider)

        try {
            cameraSelector?.let {
                camera = cameraProvider?.bindToLifecycle(this, it, previewUseCase)!!
                val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
                val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)


// Listen for the light sensor changes
                sensorManager.registerListener(cameraLightListener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
            }
        } catch (illegalStateException: IllegalStateException) {
        } catch (illegalArgumentException: IllegalArgumentException) {
        }
    }

    private fun bindAnalyseUseCase() {
        val barcodeScanner: BarcodeScanner = BarcodeScanning.getClient()

        if (cameraProvider == null) {
            return
        }
        if (analysisUseCase != null) {
            cameraProvider?.unbind(analysisUseCase)
        }

        analysisUseCase = ImageAnalysis.Builder()
            .setTargetAspectRatio(screenAspectRatio)
            .setTargetRotation(pvScan.display.rotation)
            .build()

        // Initialize our background executor
        val cameraExecutor = Executors.newSingleThreadExecutor()

        analysisUseCase?.setAnalyzer(cameraExecutor, { imageProxy ->
            processImageProxy(barcodeScanner, imageProxy)
        })

        try {
            cameraSelector?.let {
                cameraProvider?.bindToLifecycle(/* lifecycleOwner= */this,
                    it, analysisUseCase
                )
            }
        } catch (illegalStateException: IllegalStateException) {
        } catch (illegalArgumentException: IllegalArgumentException) {
        }
    }

    @SuppressLint("UnsafeExperimentalUsageError", "UnsafeOptInUsageError")
    private fun processImageProxy(
        barcodeScanner: BarcodeScanner,
        imageProxy: ImageProxy
    ) {
        if (imageProxy.image == null) return
        val inputImage =
            InputImage.fromMediaImage(imageProxy.image!!, imageProxy.imageInfo.rotationDegrees)

        barcodeScanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                val barcode = barcodes.getOrNull(0)
                barcode?.rawValue?.let { code ->

                    if(camera!=null){
                        camera.cameraControl.enableTorch(false)
                    }

                    scanResultTextView.text = code

                    if(cameraLightListener!=null) {
                        val serive = getSystemService(Context.SENSOR_SERVICE) as SensorManager
                        serive.unregisterListener(cameraLightListener)
                    }
                    Log.e("qrdata",code)
                    finish()

                }
            }
            .addOnFailureListener {

            }.addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == PERMISSION_CAMERA_REQUEST) {
            if (isCameraPermissionGranted()) {
                setupCamera()
            } else {

            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun isCameraPermissionGranted(): Boolean = this.let {
        ContextCompat.checkSelfPermission(it, Manifest.permission.CAMERA)
    } == PackageManager.PERMISSION_GRANTED
}