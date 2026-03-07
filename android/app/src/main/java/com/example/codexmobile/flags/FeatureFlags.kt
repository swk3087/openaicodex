package com.example.codexmobile.flags

import com.example.codexmobile.BuildConfig

object FeatureFlags {
    val runtimePermissionExpansionEnabled: Boolean =
        BuildConfig.FEATURE_RUNTIME_PERMISSION_EXPANSION
}
