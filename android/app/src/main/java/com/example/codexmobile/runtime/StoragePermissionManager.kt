package com.example.codexmobile.runtime

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import java.io.File

object StoragePermissionManager {
    enum class StorageMode {
        APP_PRIVATE,
        ALL_FILES
    }

    data class StorageModeStatus(
        val configuredMode: StorageMode,
        val effectiveMode: StorageMode,
        val hasAllFilesAccess: Boolean,
        val isFallbackActive: Boolean
    )

    fun hasAllFilesAccess(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
    }

    fun supportsAllFilesPermissionFlow(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    fun createManageAllFilesAccessIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun loadConfiguredStorageMode(context: Context): StorageMode {
        val raw = prefs(context).getString(KEY_STORAGE_MODE, StorageMode.APP_PRIVATE.name)
        return StorageMode.entries.firstOrNull { it.name == raw } ?: StorageMode.APP_PRIVATE
    }

    fun saveConfiguredStorageMode(context: Context, mode: StorageMode) {
        prefs(context).edit().putString(KEY_STORAGE_MODE, mode.name).apply()
    }

    fun currentStatus(context: Context): StorageModeStatus {
        val configured = loadConfiguredStorageMode(context)
        val permissionGranted = hasAllFilesAccess()
        val effective = if (configured == StorageMode.ALL_FILES && permissionGranted) {
            StorageMode.ALL_FILES
        } else {
            StorageMode.APP_PRIVATE
        }

        return StorageModeStatus(
            configuredMode = configured,
            effectiveMode = effective,
            hasAllFilesAccess = permissionGranted,
            isFallbackActive = configured == StorageMode.ALL_FILES && effective == StorageMode.APP_PRIVATE
        )
    }

    fun workspaceRoot(context: Context): File {
        val status = currentStatus(context)
        val targetRoot = when (status.effectiveMode) {
            StorageMode.APP_PRIVATE -> File(context.filesDir, APP_PRIVATE_WORKSPACE_DIR)
            StorageMode.ALL_FILES -> File(
                Environment.getExternalStorageDirectory(),
                SHARED_WORKSPACE_DIR
            )
        }
        if (!targetRoot.exists()) {
            targetRoot.mkdirs()
        }
        return targetRoot
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private const val PREF_NAME = "storage_mode"
    private const val KEY_STORAGE_MODE = "configured_mode"
    private const val APP_PRIVATE_WORKSPACE_DIR = "workspace"
    private const val SHARED_WORKSPACE_DIR = "CodexMobile/workspace"
}
