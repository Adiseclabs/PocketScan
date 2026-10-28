// PocketLab by Aditya Bhosale | +91 9324218321 | adityabhosale212223@gmail.com
package com.pocketlab

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.pocketlab.ui.PocketLabApp
import com.pocketlab.ui.PocketTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PocketLabApplication : Application()

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PocketTheme { Surface(Modifier.fillMaxSize()) { PocketLabApp() } } }
    }
}
