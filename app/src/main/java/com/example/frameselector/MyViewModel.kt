package com.example.frameselector

import androidx.lifecycle.ViewModel

class MyViewModel:ViewModel() {
    var progress = 0f

    fun incrementProgress(incrementSize:Float){
        progress+=incrementSize
    }
}