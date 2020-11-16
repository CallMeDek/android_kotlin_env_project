package com.example.cameraxappnative

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import java.util.concurrent.Executors
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.android.synthetic.main.activity_main.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.DecimalFormat
import java.util.concurrent.ExecutorService

typealias RealtimeFrameListener = (bitmapImage: Bitmap) -> Unit

class MainActivity : AppCompatActivity() {

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private lateinit var cameraProvider: ProcessCameraProvider

    private val tfLiteClassifier: TFLiteClassifier = TFLiteClassifier(this)

    private class RealtimeFrameAnalyzer(private val listener: RealtimeFrameListener): ImageAnalysis.Analyzer{
        private fun Bitmap.rotate(degrees: Float): Bitmap{
            val matrix = Matrix().apply { postRotate(degrees) }
            return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
        }

        override fun analyze(image: ImageProxy) {
            val yBuffer = image.planes[0].buffer // Y
            val uBuffer = image.planes[1].buffer // U
            val vBuffer = image.planes[2].buffer // V

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)

            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 50, out)
            val imageBytes = out.toByteArray()
            val bitmapImage = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size).rotate(90.0.toFloat())
            listener(bitmapImage)
            image.close()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if(allPermissionsGranted()){
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        tfLiteClassifier
            .initialize()

    }

    private fun startCamera() = viewFinder.post {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.createSurfaceProvider())
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            val imageAnalyzer = ImageAnalysis.Builder()
                .build()
                .also{
                    it.setAnalyzer(cameraExecutor, RealtimeFrameAnalyzer{ bitmapImage ->
                            val resultJsonObject = tfLiteClassifier.classify(bitmapImage)
                            val time = resultJsonObject.getLong("time")
                            val maxIndex = resultJsonObject.getInt("index")
                            val jsonResults: JSONArray = resultJsonObject.getJSONArray("results")

                            val labels = tfLiteClassifier.labels

                            inferenceTime.text = "Inference Time: ${time}ms"

                            val childCount = list_item.childCount
                            for(idx in 0 until childCount){
                                val resultJsonObj: JSONObject = jsonResults.getJSONObject(idx)
                                val probString = resultJsonObj.getDouble(labels[idx]).toString()
                                val probability = DecimalFormat("0.0000").format(probString.toFloat()).toFloat()
                                val child: TextView = list_item.getChildAt(idx) as TextView

                                if(idx == maxIndex){
                                    child.text = "${labels[idx]}: $probability"
                                    child.setTextColor(Color.parseColor("#FF9800"))
                                } else {
                                    child.text = "${labels[idx]}: $probability"
                                    child.setTextColor(Color.WHITE)
                                }
                            }

                    })
                }

            try{
                cameraProvider.unbindAll()

                cameraProvider.bindToLifecycle(this as LifecycleOwner, cameraSelector,
                                                preview, imageAnalyzer)

                preview.setSurfaceProvider(viewFinder.createSurfaceProvider())

            } catch (exception: Exception){
                Log.e(TAG, "Use binding failed", exception)
            }

        }, ContextCompat.getMainExecutor(this))


    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all{
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if(requestCode == REQUEST_CODE_PERMISSIONS){
            if (allPermissionsGranted()){
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }


    companion object {
        private const val TAG = "CameraXAppNative"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}