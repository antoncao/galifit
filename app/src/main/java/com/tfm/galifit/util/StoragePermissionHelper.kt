package com.tfm.galifit.util

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

fun ComponentActivity.registerAndRequestStoragePermissionsOnStart() {
    val launcher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {  }

    val toRequest = buildList {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(
                    this@registerAndRequestStoragePermissionsOnStart,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                ContextCompat.checkSelfPermission(
                    this@registerAndRequestStoragePermissionsOnStart,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    if (toRequest.isNotEmpty()) {
        launcher.launch(toRequest.toTypedArray())
    }
}
