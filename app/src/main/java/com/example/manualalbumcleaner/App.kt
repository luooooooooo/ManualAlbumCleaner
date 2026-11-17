package com.example.manualalbumcleaner

import android.app.Application
import com.google.android.material.color.DynamicColors

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Android 12+ 跟随系统壁纸动态取色，低版本回落到 colors.xml 里的兜底配色
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
