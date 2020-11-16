package com.example.cameraxappnative

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.collections.ArrayList

class TFLiteClassifier(private val context: Context){
    private lateinit var interpreter: Interpreter
    private var isInitialized = false

    var labels = ArrayList<String>()

    private val executorService: ExecutorService = Executors.newCachedThreadPool()

    private var inputImageWidth: Int = 0
    private var inputImageHeight: Int = 0
    private var modelInputSize: Int = 0

    fun initialize(): Task<Void> {
        return Tasks.call(
            executorService,
            Callable<Void> {
                initializeInterpreter()
                null
            }
        )
    }

    @Throws(IOException::class)
    private fun initializeInterpreter() {
        val assetManager = context.assets
        val model = loadModelFile(assetManager, "trained_model.tflite")

        labels = loadLines(context, "parrot_classifier_labels.txt")
        val options = Interpreter.Options()
        val interpreter = Interpreter(model, options)

        val inputShape = interpreter.getInputTensor(0).shape()
        inputImageWidth = inputShape[1]
        inputImageHeight = inputShape[2]
        modelInputSize = FLOAT_TYPE_SIZE * inputImageWidth * inputImageHeight * CHANNEL_SIZE

        this.interpreter = interpreter

        isInitialized = true

    }

    @Throws(IOException::class)
    private fun loadModelFile(assetManager: AssetManager, filename: String): ByteBuffer{
        val fileDescriptor = assetManager.openFd(filename)
        val fileInputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = fileInputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    @Throws(IOException::class)
    fun loadLines(context: Context, filename: String): ArrayList<String>{
        val scanner = Scanner(InputStreamReader(context.assets.open(filename)))
        val labels = ArrayList<String>()
        while (scanner.hasNextLine()){
            labels.add(scanner.nextLine())
        }
        scanner.close()
        return labels
    }

    private fun getMaxResult(result: FloatArray): Int{
        val maxIndex = result.indexOf(result.max()!!)
        return if (maxIndex == -1) 0 else maxIndex
    }

    fun classify(bitmapImaage: Bitmap): JSONObject{
        check(isInitialized) {"The interpreter mush be initialized first."}
        val byteBuffer =
            Bitmap.createScaledBitmap(bitmapImaage, inputImageWidth, inputImageHeight, true)
                .convertBitmapToByteBuffer()

        val output = Array(1){FloatArray(labels.size)}
        val startTime = SystemClock.uptimeMillis()
        interpreter?.run(byteBuffer, output)
        val endTime = SystemClock.uptimeMillis()

        var inferenceTime = endTime - startTime
        var index = getMaxResult(output[0])
        var result = JSONObject()

        try{
            result.put("time", inferenceTime)
            result.put("index", index)

            val jsonArray: JSONArray = JSONArray()
            for(idx in labels.indices){
                val obj: JSONObject = JSONObject()
                obj.put(labels[idx], output[0][idx])
                jsonArray.put(obj)
            }

            result.put("results", jsonArray)

        } catch (exception: JSONException){
            Log.e(TAG, "Exception occurred during constructing a JSON object -> ", exception)
        }

        return result
    }

    private fun Bitmap.convertBitmapToByteBuffer(): ByteBuffer{
        val byteBuffer = ByteBuffer.allocateDirect(modelInputSize)
        byteBuffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(inputImageWidth * inputImageHeight)
        this.getPixels(pixels, 0, width, 0, 0, width, height)
        var pixel = 0
        for(i in 0 until inputImageWidth){
            for(j in 0 until inputImageHeight){
                val pixelValue = pixels[pixel++]

                byteBuffer.putFloat(((pixelValue shr 16 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                byteBuffer.putFloat(((pixelValue shr 8 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                byteBuffer.putFloat(((pixelValue and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
            }
        }

        this.recycle()

        return byteBuffer
    }

    companion object {
        private const val TAG ="Parrot_TfLiteClassifier"
        private const val FLOAT_TYPE_SIZE = 4
        private const val CHANNEL_SIZE = 3
        private const val IMAGE_MEAN = 127.5f
        private const val IMAGE_STD = 127.5f
    }
}