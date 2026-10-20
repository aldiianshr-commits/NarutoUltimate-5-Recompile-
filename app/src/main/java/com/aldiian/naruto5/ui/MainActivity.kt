package com.aldiian.naruto5.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Text("NarutoUltimate 5 — core: " + kr.co.iefriends.pcsx2.NativeApp.loadedLibrary) }
    }
}
