package com.openstablediffusion

import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


class ImageDisplay : Fragment() {
    private lateinit var view: View
    private lateinit var mainInterface: MainInterface
    public lateinit var imageData: ByteArray
    public lateinit var seedUsed: String
    public lateinit var request: Request
    public lateinit var prompt: String

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        view = inflater.inflate(R.layout.image_display, container, false)
        initialize()
        return view
    }


    private fun initialize() {
        mainInterface = activity as MainInterface

        val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
        view.findViewById<ImageView>(R.id.imageDisplay).setImageBitmap(bitmap)

        view.findViewById<TextView>(R.id.seedDisplay).text = "Seed used: " + seedUsed

        val regenerateElement = view.findViewById<Button>(R.id.regenerate)
        regenerateElement.setOnClickListener { mainInterface.startGenerating(request, prompt) }

        val generateElement = view.findViewById<Button>(R.id.generateNew)
        generateElement.setOnClickListener { mainInterface.showParameters() }

        val useAsInitElement = view.findViewById<Button>(R.id.useAsInit)
        useAsInitElement.setOnClickListener { mainInterface.setImage(bitmap) }

        val saveElement = view.findViewById<Button>(R.id.save)
        saveElement.setOnClickListener { saveImage() }
    }

    private fun saveImage() {
        val fileName = "stabledif$prompt$seedUsed.jpg"
        val imagesDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val imageFile = File(imagesDirectory, fileName)
        try {
            FileOutputStream(imageFile).use { outputStream ->
                outputStream.write(imageData)
                outputStream.flush()
                MediaScannerConnection.scanFile(context, arrayOf(imageFile.absolutePath), null, null)
                val savedElement = view.findViewById<TextView>(R.id.saved)
                savedElement.text = "Image Saved"
            }
        } catch (e: IOException) {
            mainInterface.displayError(e.toString())
        }
    }
}