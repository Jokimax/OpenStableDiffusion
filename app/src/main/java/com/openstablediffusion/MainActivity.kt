package com.openstablediffusion

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.ViewTreeObserver
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.lang.Integer.max
import java.net.URL
import kotlin.math.floor


class MainActivity : AppCompatActivity(), MainInterface,  ViewTreeObserver.OnWindowFocusChangeListener {
    private val parameters: ParametersFragment = ParametersFragment()
    private val internet: NetworkManager = NetworkManager()
    private lateinit var errorElement: TextView
    private lateinit var generationCoroutine: Job
    private var pickedImage: Bitmap? = null
    private var hasFocus: Boolean = false
    private var safeToChangeFragment: Boolean = true
    private val apiUrl: String = "https://stablehorde.net/api/v2/"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initialize()
    }

    private fun initialize() {
        // Request the necessary permissions
        val permissions = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.POST_NOTIFICATIONS)
        for (permission in permissions) {
            requestPermission(permission)
        }
        showParameters()
        errorElement = findViewById(R.id.error)
    }

    private fun requestPermission(permission: String) {
        val temp = ContextCompat.checkSelfPermission(this, permission)
        if(temp == PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), 0)
        }
    }

    override fun showParameters() {
        if(safeToChangeFragment) {
            val fragmentTransaction = supportFragmentManager.beginTransaction()
            fragmentTransaction.replace(R.id.container, parameters)
            fragmentTransaction.commit()
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            while(!safeToChangeFragment) { delay(10) }
            runOnUiThread { showParameters() }}
    }

    // Display generation info
    private fun showGeneration(generation: GenerationFragment) {
        if(safeToChangeFragment) {
            val fragmentTransaction = supportFragmentManager.beginTransaction()
            fragmentTransaction.replace(R.id.container, generation)
            fragmentTransaction.commit()
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            while(!safeToChangeFragment) { delay(10) }
            runOnUiThread { showGeneration(generation) }}
    }

    // Display an image once it's generated
    private fun showImage(imageData: ByteArray, seedUsed: String,
                          request: Request, prompt: String){
        if(safeToChangeFragment) {
            val imageDisplay = ImageDisplayFragment()
            imageDisplay.imageData = imageData
            imageDisplay.seedUsed = seedUsed
            imageDisplay.request = request
            imageDisplay.prompt = prompt
            val fragmentTransaction = supportFragmentManager.beginTransaction()
            fragmentTransaction.replace(R.id.container, imageDisplay)
            fragmentTransaction.commit()
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            while(!safeToChangeFragment) { delay(10) }
            runOnUiThread { showImage(imageData, seedUsed, request, prompt) }
        }
    }

    override fun onPause() {
        super.onPause()
        safeToChangeFragment = false

    }override fun onResume() {
        super.onResume()
        safeToChangeFragment = true
    }

    // The function that calls a post a request to AI horde
    override suspend fun generateImage(request: Request, prompt: String) {
        // Show generation status
        val generation = GenerationFragment()
        runOnUiThread {
            showGeneration(generation)
        }
        if(!internet.isConnected(this)){
            runOnUiThread {
                showParameters()
                displayError("An error occurred with your internet connection!")
            }
            return
        }
        val client = OkHttpClient()
        var response: Any?
        try {
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
                return
            }

            val id = response.get("id").toString()
            generation.id  = id
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
                if(!internet.isConnected(this)){
                    delay(500)
                    continue
                }
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

            // Send a notification and wait until app goes back to focus.
            if(!hasFocus) {
                val pendingIntent: PendingIntent = PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val name = "Channel Name"
                    val descriptionText = "Channel Description"
                    val importance = NotificationManager.IMPORTANCE_DEFAULT
                    val channel = NotificationChannel("Open Stable Diffusion", name, importance).apply {
                        description = descriptionText
                    }
                    val notificationManager: NotificationManager =
                        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.createNotificationChannel(channel)
                }
                var notification = NotificationCompat.Builder(applicationContext, "Open Stable Diffusion")
                    .setSmallIcon(R.drawable.baseline_image_24)
                    .setContentTitle("Generation finished")
                    .setContentText("$prompt has finished generating")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .build()

                val notificationManager =
                    getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(0, notification)
                while(!hasFocus) {
                    delay(500)
                }
            }
            runOnUiThread {
                showImage(imageData, seedUsed, request, prompt)
            }
        } catch (e: IOException) {
            delay(10)
            runOnUiThread {
                displayError(e.toString())
                showParameters()
            }
        }
    }

    override fun onCancelGeneration() {
        generationCoroutine.cancel()
        showParameters()
    }

    override fun displayError(error: String) {
        errorElement.text = error
    }

    override fun setGenerationCoroutine(generationCoroutine: Job) {
        this.generationCoroutine = generationCoroutine
    }

    override fun setImage(newImage: Bitmap) {
        pickedImage = newImage
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
                pickedImage = resizeImage(pickedImage!!)
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    // Resizes the image to be within 3072 by 3072 pixels
    private fun resizeImage(image: Bitmap) : Bitmap{
        var temp = image
        var width = image.width
        var height = image.height
        if(width > 3072){
            val aspectRatio = 3072.0/width
            width = 3072
            height = max(1, floor(height * aspectRatio).toInt())
            temp = Bitmap.createScaledBitmap(temp, width, height, false)
        }
        if(height > 3072){
            val aspectRatio = 3072.0/height
            height = 3072
            width = max(1, floor(width * aspectRatio).toInt())
            temp = Bitmap.createScaledBitmap(temp, width, height, false)
        }
        return temp
    }

    //  Returns the currently selected image as Base64 encoded string
    override fun getImage(): String? {
        val temp = pickedImage ?: return null
        val stream = ByteArrayOutputStream()
        temp.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        val byteArray = stream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT).replace("\n","")
    }

    override fun onWindowFocusChanged(focused: Boolean) {
        hasFocus = focused
    }
}

