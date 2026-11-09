package com.algoarch.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.algoarch.app.ui.AlgoArchNav
import com.algoarch.app.ui.theme.AlgoArchTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as AlgoArchApp
        setContent {
            AlgoArchTheme {
                AlgoArchNav(app)
            }
        }
    }
}
