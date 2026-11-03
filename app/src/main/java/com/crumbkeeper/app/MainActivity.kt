package com.crumbkeeper.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.crumbkeeper.app.ui.CrumbkeeperApp

class MainActivity : ComponentActivity() {
    private var incomingRecipe by mutableStateOf<IncomingRecipe?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingRecipe = IncomingRecipe.from(intent)
        setContent {
            CrumbkeeperApp(this, incomingRecipe) { requestId ->
                if (incomingRecipe?.id == requestId) {
                    incomingRecipe = null
                    // A consumed attachment must not reopen after Activity recreation.
                    setIntent(Intent(this, MainActivity::class.java).setAction(Intent.ACTION_MAIN))
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingRecipe = IncomingRecipe.from(intent)
    }
}
