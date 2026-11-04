package com.tm.meow.sudoku.puzzle.data.platform

import android.os.Build
import com.tm.meow.sudoku.puzzle.domain.startup.SdkLevelProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidSdkLevelProvider @Inject constructor() : SdkLevelProvider {
    override fun sdkInt(): Int = Build.VERSION.SDK_INT
}
