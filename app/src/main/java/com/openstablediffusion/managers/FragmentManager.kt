package com.openstablediffusion.managers

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FragmentManager: AppCompatActivity() {
    private var safeToChangeFragment: Boolean = true
    public var changedFragment: Boolean = true

    override fun onPause() {
        super.onPause()
        safeToChangeFragment = false

    }
    override fun onResume() {
        super.onResume()
        safeToChangeFragment = true
    }

    public fun changeFragment(fragmentTransaction: FragmentTransaction) {
        changedFragment = false
        if(safeToChangeFragment) {
            fragmentTransaction.commit()
            changedFragment = true
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            while (!safeToChangeFragment) { delay(10) }
            changeFragment(fragmentTransaction)
        }
    }
}