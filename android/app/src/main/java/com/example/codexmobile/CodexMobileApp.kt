package com.example.codexmobile

import android.app.Application
import com.example.codexmobile.domain.usecase.InitializeSessionRuntimeUseCase
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class CodexMobileApp : Application() {
    @Inject
    lateinit var initializeSessionRuntimeUseCase: InitializeSessionRuntimeUseCase

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            initializeSessionRuntimeUseCase(DEMO_SESSION_ID)
        }
    }

    private companion object {
        const val DEMO_SESSION_ID = "demo-session"
    }
}
