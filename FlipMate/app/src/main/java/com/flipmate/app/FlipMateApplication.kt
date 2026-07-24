package com.flipmate.app
import android.app.Application
import com.flipmate.app.core.security.CredentialManager
class FlipMateApplication:Application(){ lateinit var credentials:CredentialManager; override fun onCreate(){super.onCreate(); credentials=CredentialManager(this)} }
