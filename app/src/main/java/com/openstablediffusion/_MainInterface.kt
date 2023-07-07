package com.openstablediffusion

import android.graphics.Bitmap
import okhttp3.Request

//  Interface for communicating with MainActivity.kt
interface MainInterface {
    fun startGenerating(request: Request, prompt: String)
    fun displayError(error: String)
    fun onCancelGeneration()
    fun showParameters()
    fun uploadImage()
    fun getImage(): String?
    fun setImage(newImage: Bitmap)
}