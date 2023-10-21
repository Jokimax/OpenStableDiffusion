package com.openstablediffusion

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException

class GenerationFragment : Fragment() {
    private val internet: NetworkManager = NetworkManager()
    private lateinit var view: View
    private lateinit var mainInterface: MainInterface
    private lateinit var timerElement: TextView
    public var id: String? = null
    private val apiUrl: String = "https://stablehorde.net/api/v2/"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        view = inflater.inflate(R.layout.generation, container, false)
        initialize()
        return view
    }

    private fun initialize() {
        mainInterface = activity as MainInterface

        timerElement = view.findViewById(R.id.timeLeft)

        val cancelElement = view.findViewById<Button>(R.id.cancel)
        cancelElement.setOnClickListener {CoroutineScope(IO).launch { cancelGeneration() }}
    }

    // Cancel the remote generation request
    private fun cancelGeneration() {
        try {
            if(!internet.isConnected(requireContext())){
                val act = activity ?: return
                act.runOnUiThread {
                    mainInterface.showParameters()
                    mainInterface.displayError("An error occurred with your internet connection!")
                }
                return
            }
            if(id != null) {
                val client = OkHttpClient()
                val request: Request = Request.Builder().url(apiUrl + "generate/status/" + id).delete().build()
                client.newCall(request).execute()
            }
            mainInterface.onCancelGeneration()
        } catch (e: IOException) {
            val act = activity ?: return
            act.runOnUiThread {
                mainInterface.displayError(e.toString())
            }
        }
    }

    public fun displayWaitingTime(timeLeft: String) {
        timerElement.text = "Estimated wait time left: "+timeLeft+" s"
    }
}