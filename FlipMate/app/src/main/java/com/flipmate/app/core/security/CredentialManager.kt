package com.flipmate.app.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class CredentialManager(context: Context) {
    private val prefs=EncryptedSharedPreferences.create(context,"credentials",MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    fun save(key:String,secret:String)=prefs.edit().putString("key",key.trim()).putString("secret",secret.trim()).apply()
    fun apiKey():String?=prefs.getString("key",null)
    fun apiSecret():String?=prefs.getString("secret",null)
    fun has()=!apiKey().isNullOrBlank()&&!apiSecret().isNullOrBlank()
    fun clear()=prefs.edit().clear().apply()
}
