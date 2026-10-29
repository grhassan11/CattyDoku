package com.threetech.catpuzzle.data.platform

import android.os.Build
import com.threetech.catpuzzle.domain.startup.SdkLevelProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidSdkLevelProvider @Inject constructor() : SdkLevelProvider {
    override fun sdkInt(): Int = Build.VERSION.SDK_INT
}
