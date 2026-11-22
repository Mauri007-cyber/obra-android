package es.obra.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import es.obra.app.ui.AppObra
import es.obra.app.ui.TemaObra

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TemaObra {
                AppObra()
            }
        }
    }
}
