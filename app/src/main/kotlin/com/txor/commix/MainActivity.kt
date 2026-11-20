package com.txor.commix

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.OrientationEventListener
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.txor.commix.ui.CameraScreen
import com.txor.commix.ui.CommixViewModel
import com.txor.commix.ui.theme.CommixTheme

class MainActivity : ComponentActivity() {
    private val viewModel: CommixViewModel by viewModels()
    private var orientationListener: OrientationEventListener? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (cameraGranted && audioGranted) {
            // Permissions granted, ready for preview
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        orientationListener = object : OrientationEventListener(this) {
            private var lastQuantized = -1
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val quantized = ((orientation + 45) / 90 * 90) % 360
                if (quantized != lastQuantized) {
                    lastQuantized = quantized
                    viewModel.onDeviceRotationChanged(quantized)
                }
            }
        }

        requestRequiredPermissions()

        setContent {
            CommixTheme {
                CameraScreen(viewModel = viewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        orientationListener?.takeIf { it.canDetectOrientation() }?.enable()
        viewModel.onAppForeground()
    }

    override fun onStop() {
        super.onStop()
        orientationListener?.disable()
        viewModel.onAppBackground()
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }
}
