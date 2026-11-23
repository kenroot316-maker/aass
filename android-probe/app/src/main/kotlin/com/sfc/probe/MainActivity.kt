package com.sfc.probe

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this)
        tv.text = "SFC capability probe - APK built by z.ai sandbox"
        setContentView(tv)
    }
}
