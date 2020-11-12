package com.example.cameraxapp

import android.Manifest
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONException
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {
    private var imageCapture: ImageCapture? = null
    private var preview: Preview? = null

    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ActivityCompat.requestPermissions(
            this, REQUIRED_PERMISSION, REQUEST_CODE_PERMISSION)

        camera_capture_button.setOnClickListener {
            takePhoto()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.createSurfaceProvider())
                }

            imageCapture = ImageCapture.Builder()
                .build()


            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()

                cameraProvider.bindToLifecycle(this, cameraSelector,
                    preview, imageCapture)
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return
        val dialog: Dialog = LoadingDialog(this)

        CoroutineScope(Dispatchers.Main).launch {
            dialog.show()
            imageCapture.takePicture(
                ContextCompat.getMainExecutor(this@MainActivity), object : ImageCapture.OnImageCapturedCallback(){
                    override fun onError(exception: ImageCaptureException) {
                        Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                    }

                    override fun onCaptureSuccess(image: ImageProxy) {

                        val date = Calendar.getInstance().time
                        val formatter = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.getDefault())
                        val dateString = formatter.format(date)
                        val fileName = "$dateString.jpg"

                        val buffer = image.planes[0].buffer
                        val size = buffer.remaining()
                        val byteArray = ByteArray(size)
                        buffer.get(byteArray, 0, size)
                        val bitmapImage = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)//.rotate(90.0.toFloat())
                        val byteStream = ByteArrayOutputStream()
                        bitmapImage.compress(Bitmap.CompressFormat.JPEG, 100, byteStream)
                        byteStream.close()
                        val rotatedByteArray = byteStream.toByteArray()

                        val b64encoded = Base64.encodeToString(rotatedByteArray, Base64.DEFAULT);
                        val url = "http://117.16.123.11:5000/parrotClassifier/predict/"
                        val stringRequest: StringRequest = object : StringRequest(Method.POST, url,
                            Response.Listener { response ->
                                try {
                                    val fileStream = this@MainActivity.openFileOutput(fileName, Context.MODE_PRIVATE)
                                    bitmapImage.compress(Bitmap.CompressFormat.JPEG, 100, fileStream)
                                    fileStream.close()
                                    bitmapImage.recycle()
                                    val intent = Intent(this@MainActivity, ResultActivity::class.java).apply {
                                        putExtra("image", fileName)
                                        putExtra("result", response)
                                    }
                                    dialog.dismiss()
                                    startActivity(intent)
                                } catch (e: JSONException) {
                                    e.printStackTrace()
                                }
                            },
                            Response.ErrorListener { error ->
                                Toast.makeText(this@MainActivity, error.toString(), Toast.LENGTH_LONG).show()
                            }) {
                            override fun getParams(): Map<String, String> {
                                val params = HashMap<String, String>()
                                params.put("b64", b64encoded)
                                return params.toMap()
                            }
                        }
                        val requestQueue = Volley.newRequestQueue(this@MainActivity)
                        requestQueue.add(stringRequest)

                        image.close()
                    }
                }
            )
        }
    }

    private fun Bitmap.rotate(degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }



    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSION){
            if (allPermissionsGranted()){
                startCamera()
            } else {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun allPermissionsGranted(): Boolean = REQUIRED_PERMISSION.all{
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXBasic"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSION = 10
        private val REQUIRED_PERMISSION = arrayOf(Manifest.permission.CAMERA, Manifest.permission.INTERNET)
    }
}


