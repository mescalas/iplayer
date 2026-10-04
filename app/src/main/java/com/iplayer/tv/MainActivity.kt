package com.iplayer.tv

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.iplayer.tv.ui.AppRoot
import com.iplayer.tv.ui.theme.IPlayerTheme

class MainActivity : ComponentActivity() {
    private val container get() = (application as App).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { IPlayerTheme { AppRoot() } }
        container.repository.refreshIfStale()
    }

    override fun onStart() {
        super.onStart()
        container.player.onForeground()
        container.updater.checkIfDue()
    }

    override fun onStop() {
        super.onStop()
        container.player.onBackground()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) container.player.stop()
    }
}
