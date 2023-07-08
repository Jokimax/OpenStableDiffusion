package com.openstablediffusion

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL

class MainActivity : AppCompatActivity(), MainInterface {
    private val parameters: Parameters = Parameters()
    private lateinit var errorElement: TextView
    private lateinit var id: String
    private lateinit var generationCoroutine: Job
    private var pickedImage: Bitmap? = null
    private val apiUrl: String = "https://stablehorde.net/api/v2/"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initialize()
        showParameters()
    }

    private fun initialize() {
        errorElement = findViewById(R.id.error)
    }

    // Display the main menu for setting parameters
    override fun showParameters() {
        val fragmentTransaction = supportFragmentManager.beginTransaction()
        fragmentTransaction.replace(R.id.container, parameters)
        fragmentTransaction.commit()
    }

    // Display generation info
    private fun showGeneration(generation: Generation) {
        val fragmentTransaction = supportFragmentManager.beginTransaction()
        fragmentTransaction.replace(R.id.container, generation)
        fragmentTransaction.commit()
    }

    // The function that calls a post a request to AI horde
    override fun startGenerating(request: Request, prompt: String) {
    // HTTP requests need to be run on a different thread then the main one
    generationCoroutine = CoroutineScope(IO).launch {
        val client = OkHttpClient()
        var response: Any?
        try {
            // Show generation status
            val generation = Generation()
            runOnUiThread {
                showGeneration(generation)
            }

            // Begin generating the image
            response = client.newCall(request).execute()
            response = response.body?.string()
            response = JSONObject(response)

            // If the response has a message that means that an error occurred
            if (response.has("message")) {
                val temp = response.get("message").toString()
                runOnUiThread {
                    displayError(temp)
                    showParameters()
                }
                return@launch
            }

            id = response.get("id").toString()
            generation.id  = id
            Log.d("id", id)
            val headers = mapOf(
                "accept" to "application/json"
            )
            var newRequest = Request.Builder()
                .url(apiUrl + "generate/check/" + id)
                .headers(headers.toHeaders())
                .build()
            var done = false

            // Wait for generation to finish
            while (!done) {
                response = client.newCall(newRequest).execute()
                response = response.body?.string()
                response = JSONObject(response)
                done = response.get("done").toString().toBoolean()
                val temp = response.get("wait_time").toString()
                runOnUiThread {
                    generation.displayWaitingTime(temp)
                }
                delay(500)
            }

            // Get the image and parse it's data
            newRequest = Request.Builder()
                .url(apiUrl + "generate/status/" + id)
                .headers(headers.toHeaders())
                .build()
            response = client.newCall(newRequest).execute()
            response = response.body?.string()
            response = JSONObject(response)
            val imgUrl =
                response.getJSONArray("generations").
                getJSONObject(0).get("img").toString()
            val seedUsed =
                response.getJSONArray("generations").
                getJSONObject(0).get("seed").toString()
            val img = URL(imgUrl)
            var imageData = img.readBytes()
            runOnUiThread {
                showImage(imageData, seedUsed, request, prompt)
            }
            } catch (e: IOException) {
                runOnUiThread {
                    displayError(e.toString())
                    showParameters()
                }
            }
        }
    }

    // Displays an image once it's generated
    private fun showImage(imageData: ByteArray, seedUsed: String,
                          request: Request, prompt: String){
        val imageDisplay = ImageDisplay()
        imageDisplay.imageData = imageData
        imageDisplay.seedUsed = seedUsed
        imageDisplay.request = request
        imageDisplay.prompt = prompt
        val fragmentTransaction = supportFragmentManager.beginTransaction()
        fragmentTransaction.replace(R.id.container, imageDisplay)
        fragmentTransaction.commit()
    }

    override fun displayError(error: String) {
        errorElement.text = error
    }

    override fun onCancelGeneration() {
        generationCoroutine.cancel()
        showParameters()
    }

    // Allows the user to upload an image
    override fun uploadImage() {
        val intext = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intext,0)
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 0 && resultCode == Activity.RESULT_OK && data != null) {
            val pickedPhoto = data.data
            if (pickedPhoto != null) {
                pickedImage = MediaStore.Images.Media.getBitmap(this.contentResolver,pickedPhoto)
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun setImage(newImage: Bitmap) {
        pickedImage = newImage
        showParameters()
    }

    //  Returns the currently selected image as Base64 encoded string
    override fun getImage(): String? {
        val temp = pickedImage ?: return null
        val stream = ByteArrayOutputStream()
        temp.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        val byteArray = stream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT).replace("\n","")
    }
}
