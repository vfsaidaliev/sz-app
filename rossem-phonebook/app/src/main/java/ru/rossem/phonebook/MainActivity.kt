package ru.rossem.phonebook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import ru.rossem.phonebook.ui.PhonebookApp
import ru.rossem.phonebook.ui.PhonebookTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PhonebookTheme {
                PhonebookApp()
            }
        }
    }
}
