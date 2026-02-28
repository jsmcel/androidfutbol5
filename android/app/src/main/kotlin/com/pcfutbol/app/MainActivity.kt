package com.pcfutbol.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.pcfutbol.ui.PcfNavHost
import com.pcfutbol.ui.theme.PcfTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var appInitializer: AppInitializer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Seed de datos en background (solo primera vez)
        lifecycleScope.launch {
            appInitializer.ensureSeeded()
        }

        setContent {
            PcfTheme {
                PcfNavHost()
            }
        }
    }
}
