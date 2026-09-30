package kz.soskomek.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import kz.soskomek.app.ui.AppRoot
import kz.soskomek.app.ui.theme.SosTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SosTheme {
                AppRoot()
            }
        }
    }
}
