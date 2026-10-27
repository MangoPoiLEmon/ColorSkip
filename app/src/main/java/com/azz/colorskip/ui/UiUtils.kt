package com.azz.colorskip.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun Context.toast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
