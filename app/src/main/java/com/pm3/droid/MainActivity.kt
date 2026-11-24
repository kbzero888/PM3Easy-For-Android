package com.pm3.droid

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.pm3.droid.engine.Pm3Engine
import com.pm3.droid.model.ThemeMode
import com.pm3.droid.ui.Pm3App
import com.pm3.droid.ui.theme.PM3DroidTheme

class MainActivity : ComponentActivity() {

    private lateinit var pm3Engine: Pm3Engine

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }

            if (device != null && isPm3Device(device)) {
                when (intent.action) {
                    UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                        pm3Engine.onUsbAttachedOrConnect()
                    }
                    UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        pm3Engine.onUsbDetached()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pm3Engine = Pm3Engine(applicationContext).apply {
            onHapticFeedback = { triggerShortHaptic() }
        }

        requestStoragePermissionsIfNeeded()
        registerUsbReceiver()
        checkAlreadyConnectedUsb()

        setContent {
            var themeMode by remember { mutableStateOf(ThemeMode.DARK) }
            var keepScreenOn by remember { mutableStateOf(true) }

            LaunchedEffect(keepScreenOn) {
                if (keepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            PM3DroidTheme(themeMode = themeMode) {
                Pm3App(
                    engine = pm3Engine,
                    themeMode = themeMode,
                    onCycleTheme = {
                        themeMode = when (themeMode) {
                            ThemeMode.DARK -> ThemeMode.LIGHT
                            ThemeMode.LIGHT -> ThemeMode.SYSTEM
                            ThemeMode.SYSTEM -> ThemeMode.DARK
                        }
                    },
                    keepScreenOn = keepScreenOn,
                    onToggleKeepScreenOn = {
                        keepScreenOn = !keepScreenOn
                        Toast.makeText(
                            this,
                            if (keepScreenOn) "已开启屏幕常亮" else "已关闭屏幕常亮",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            pm3Engine.onUsbAttachedOrConnect()
        }
    }

    private fun registerUsbReceiver() {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(usbReceiver, filter)
        }
    }

    private fun checkAlreadyConnectedUsb() {
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val hasPm3 = usbManager.deviceList.values.any { isPm3Device(it) }
        if (hasPm3) {
            pm3Engine.onUsbAttachedOrConnect()
        }
    }

    private fun isPm3Device(device: UsbDevice): Boolean {
        val vid = device.vendorId
        val pid = device.productId
        return (vid == 0x9AC4 && pid == 0x4B8F) ||
            (vid == 0x2D2D && pid == 0x504D) ||
            (vid == 0x03EB && pid == 0x6124)
    }

    private fun requestStoragePermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (_: Exception) {
                }
            }
        } else {
            val perms = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            if (perms.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
                storagePermissionLauncher.launch(perms)
            }
        }
    }

    private fun triggerShortHaptic() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(usbReceiver)
        } catch (_: Exception) {
        }
        pm3Engine.destroy()
    }
}
