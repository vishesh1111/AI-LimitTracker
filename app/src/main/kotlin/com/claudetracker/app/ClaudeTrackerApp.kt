package com.claudetracker.app

import android.app.Application
import com.claudetracker.app.data.UsageRepository
import com.claudetracker.app.data.local.SecureStorage
import com.claudetracker.app.data.remote.UsageApiClient
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class ClaudeTrackerApp : Application() {
    lateinit var okHttpClient: OkHttpClient
        private set
    lateinit var secureStorage: SecureStorage
        private set
    lateinit var usageApiClient: UsageApiClient
        private set
    lateinit var usageRepository: UsageRepository
        private set

    companion object {
        lateinit var appInstance: ClaudeTrackerApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        appInstance = this

        okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        secureStorage = SecureStorage(this)
        usageApiClient = UsageApiClient(okHttpClient)
        usageRepository = UsageRepository(this, secureStorage, usageApiClient)
    }
}
