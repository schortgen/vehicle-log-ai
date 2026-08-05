package com.schortgen.vehiclelogai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.schortgen.vehiclelogai.ui.theme.VehicleLogTheme
import com.schortgen.vehiclelogai.navigation.NavGraph
import com.schortgen.vehiclelogai.ui.components.BottomNavBar
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VehicleLogApp()
        }
    }
}

@Composable
fun VehicleLogApp() {
    VehicleLogTheme {
        val navController = rememberNavController()
        Scaffold(
            bottomBar = { BottomNavBar(navController) }
        ) { innerPadding ->
            Surface(modifier = Modifier.padding(innerPadding)) {
                NavGraph(navController)
            }
        }
    }
}
