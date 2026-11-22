package com.txor.commix

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.OrientationEventListener
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.txor.commix.ui.CameraScreen
import com.txor.commix.ui.CommixHaptics
import com.txor.commix.ui.CommixViewModel
import com.txor.commix.ui.components.CommixPrimaryButton
import com.txor.commix.ui.theme.*

class MainActivity : ComponentActivity() {
    private val viewModel: CommixViewModel by viewModels()
    private var orientationListener: OrientationEventListener? = null
    private var hasCameraPermission by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        hasCameraPermission = cameraGranted
        if (cameraGranted) {
            viewModel.onAppForeground()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
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

        hasCameraPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        requestRequiredPermissions()

        setContent {
            CommixTheme {
                if (hasCameraPermission) {
                    CameraScreen(viewModel = viewModel)
                } else {
                    CameraPermissionGate(
                        onRequestPermission = { requestRequiredPermissions() },
                        onOpenSettings = {
                            try {
                                val intent = Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", packageName, null)
                                )
                                startActivity(intent)
                            } catch (_: Throwable) {}
                        }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        hasCameraPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        orientationListener?.takeIf { it.canDetectOrientation() }?.enable()
        if (hasCameraPermission) {
            viewModel.onAppForeground()
        }
    }

    override fun onStop() {
        super.onStop()
        orientationListener?.disable()
        viewModel.onAppBackground()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (hasCameraPermission &&
            (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
        ) {
            if (event?.repeatCount == 0) {
                CommixHaptics.heavy(this, viewModel.uiState.value.hapticsEnabled)
                viewModel.onShutterTapped()
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
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
        } else {
            hasCameraPermission = true
        }
    }
}

@Composable
private fun CameraPermissionGate(
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(CinemaBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(CinemaSheetBg)
                .border(1.dp, CinemaGlassBorder, RoundedCornerShape(22.dp))
                .padding(24.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(AppleYellow.copy(alpha = 0.16f))
                    .border(1.2.dp, AppleYellow.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Camera Permission",
                    tint = AppleYellow,
                    modifier = Modifier.size(30.dp)
                )
            }

            Text(
                text = "Camera & Audio Access Required",
                color = CinemaTextLight,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Commix needs camera hardware access to open physical lenses, capture 14-bit RAW/HDR+ photos, and record cinema video with live audio metering.",
                color = CinemaTextDim,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            CommixPrimaryButton(
                text = "GRANT CAMERA ACCESS",
                icon = Icons.Default.CameraAlt,
                onClick = onRequestPermission,
                testTagName = "grant_camera_permission_button"
            )

            OutlinedButton(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CinemaGlassBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CinemaTextLight),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("open_app_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Open App Settings",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
