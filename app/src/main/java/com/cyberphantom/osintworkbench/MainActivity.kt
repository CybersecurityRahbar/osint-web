package com.cyberphantom.osintworkbench

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.cyberphantom.osintworkbench.ui.WorkbenchApp
import com.cyberphantom.osintworkbench.ui.WorkbenchTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WorkbenchTheme {
                WorkbenchApp()
            }
        }
    }
}
