package com.example.cameraxapp

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.AttributeSet
import android.util.Base64
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import kotlinx.android.synthetic.main.activity_result.*
import org.json.JSONException
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.text.DecimalFormat
import java.util.HashMap


class ResultActivity : AppCompatActivity() {

    private lateinit var fileName: String
    private lateinit var parrotClass: String
    private lateinit var imageBitmap: Bitmap
    private lateinit var splittedLabels: List<String>
    private var maxIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        val filename = intent.getStringExtra("image")
        val result = intent.getStringExtra("result")
        initialize(filename, result)

        saveButton.setOnClickListener {
            upLoadFromMemory(imageBitmap)
        }

        backButton.setOnClickListener {
            finish()
        }
    }

    private fun initialize(mfilename: String, mresult: String){
        val filename: String = mfilename
        this.fileName = filename
        val result: String = mresult

        val open: FileInputStream  = this.openFileInput(filename)
        val bmp: Bitmap = BitmapFactory.decodeStream(open)
        imageBitmap = bmp
        open.close()

        imageView.setImageBitmap(bmp)

        val params: LinearLayout.LayoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            200
        )
        val viewParams: LinearLayout.LayoutParams = LinearLayout.LayoutParams(
            550,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        list_item!!.removeAllViews()
        if (result == "Not parrot"){
            val noParrot = TextView(this)
            noParrot.setBackgroundColor(Color.parseColor("#2198F6"))
            noParrot.text = result
            noParrot.textSize = 20.0.toFloat()
            noParrot.setTypeface(null, Typeface.BOLD)
            noParrot.setTextColor(Color.WHITE)
            noParrot.gravity = Gravity.CENTER
            noParrot.layoutParams = params
            list_item.addView(noParrot)
        } else {
            val subString = result!!.substring(1, result!!.length-1)
            val probabilitiesString = subString.split(",")
            val probabilities = mutableListOf<Float>()
            probabilitiesString.forEach {
                probabilities.add(DecimalFormat("0.0000").format(it.toFloat()).toFloat())
            }
            maxIndex = probabilities.indexOf(probabilities.max())
            val labels= application.assets.open("labels.txt").bufferedReader().use{
                it.readText().trim()
            }
            splittedLabels = labels.split("\n")

            for (idx in splittedLabels.indices){
                if (idx == maxIndex){
                    val linearLayout = LinearLayout(this)
                    linearLayout.orientation = LinearLayout.HORIZONTAL
                    linearLayout.layoutParams = params
                    linearLayout.setBackgroundColor(Color.parseColor("#2198F6"))

                    val className = CustomTextView(this)
                    className.setString(splittedLabels[idx])
                    parrotClass = splittedLabels[idx]
                    className.text = splittedLabels[idx]
                    className.gravity = Gravity.CENTER
                    className.layoutParams = viewParams
                    className.textSize = 20.0.toFloat()
                    className.setTypeface(null, Typeface.BOLD)
                    className.setTextColor(Color.WHITE)

                    val probability = TextView(this)
                    probability.text = "${probabilities[idx]}"
                    probability.setTextColor(Color.WHITE)
                    probability.gravity = Gravity.CENTER
                    probability.textSize = 20.0.toFloat()
                    probability.setTypeface(null, Typeface.BOLD)
                    probability.layoutParams = viewParams

                    linearLayout.addView(className)
                    linearLayout.addView(probability)
                    list_item.addView(linearLayout)
                } else {
                    val linearLayout = LinearLayout(this)
                    linearLayout.orientation = LinearLayout.HORIZONTAL
                    linearLayout.layoutParams = params
                    linearLayout.setBackgroundColor(Color.parseColor("#2198F6"))

                    val className = TextView(this)
                    className.text = splittedLabels[idx]
                    className.setTypeface(null, Typeface.BOLD)
                    className.setTextColor(Color.parseColor("#EEE1972A"))
                    className.gravity = Gravity.CENTER
                    className.layoutParams = viewParams

                    val probability = TextView(this)
                    probability.text = "${probabilities[idx]}"
                    probability.setTypeface(null, Typeface.BOLD)
                    probability.setTextColor(Color.parseColor("#EEE1972A"))
                    probability.gravity = Gravity.CENTER
                    probability.layoutParams = viewParams

                    linearLayout.addView(className)
                    linearLayout.addView(probability)
                    list_item.addView(linearLayout)
                }
            }
            saveButton.visibility = View.VISIBLE
        }
    }

    private fun upLoadFromMemory(bitmap: Bitmap){
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG,100,byteArrayOutputStream)
        val data=byteArrayOutputStream.toByteArray()

        val b64encoded = Base64.encodeToString(data, Base64.DEFAULT);
        val url = "http://117.16.123.11:5000/parrot-classifier/save"
        val stringRequest: StringRequest = object : StringRequest(
            Method.POST, url,
            Response.Listener { response ->
                try {
                   Toast.makeText(this@ResultActivity, response, Toast.LENGTH_LONG).show()
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            },
            Response.ErrorListener { error ->
                Toast.makeText(this@ResultActivity, error.toString(), Toast.LENGTH_LONG).show()
            }) {
            override fun getParams(): Map<String, String> {
                val params = HashMap<String, String>()
                params.put("b64", b64encoded)
                params.put("index", "$maxIndex")
                return params.toMap()
            }
        }
        val requestQueue = Volley.newRequestQueue(this@ResultActivity)
        requestQueue.add(stringRequest)
    }

}

class CustomTextView : AppCompatTextView {
    private var str: String? = null

    constructor(context: Context) : super(context) {}
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {}

    override fun onTouchEvent(event: MotionEvent): Boolean {
        super.onTouchEvent(event)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                performClick()
                return true
            }
        }
        return false
    }

    override fun performClick(): Boolean {
        super.performClick()
        showInformation()
        return true
    }

    fun setString(string: String){
        str = string
    }

    private fun showInformation() {
        val intent = Intent(context, InformationActivity::class.java).apply {
            putExtra("name", str)
        }
        context.startActivity(intent)
    }
}