package com.flipmate.app
import android.app.Application
import com.flipmate.app.core.security.CredentialManager
import com.flipmate.app.core.network.NetworkModule
import com.flipmate.app.core.network.WebSocketManager

class FlipMateApplication : Application() {
    lateinit var credentials: CredentialManager
    lateinit var network: NetworkModule
    lateinit var wsManager: WebSocketManager
    
    override fun onCreate() {
        super.onCreate()
        credentials = CredentialManager(this)
        network = NetworkModule(credentials)
        wsManager = WebSocketManager()
    }
}
