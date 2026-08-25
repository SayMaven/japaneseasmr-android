package com.saymaven.downloader.japaneseasmr.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.widget.Toast
import com.saymaven.downloader.japaneseasmr.service.usb.UsbAudioEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UsbDacInfo(
    val isConnected: Boolean = false,
    val dacName: String = "",
    val isExclusiveActive: Boolean = false,
    val audioDeviceInfo: AudioDeviceInfo? = null,
    val usbDevice: UsbDevice? = null
)

object UsbDacManager {

    private const val ACTION_USB_PERMISSION = "com.saymaven.downloader.japaneseasmr.USB_PERMISSION"

    private val _dacState = MutableStateFlow(UsbDacInfo())
    val dacState = _dacState.asStateFlow()

    val hardwareVolume: StateFlow<Float> = UsbAudioEngine.hardwareVolume
    val isHardwareVolumeSupported: StateFlow<Boolean> = UsbAudioEngine.isHardwareVolumeSupported
    val showVolumeHud: StateFlow<Boolean> = UsbAudioEngine.showVolumeHud

    fun triggerVolumeHud() = UsbAudioEngine.triggerVolumeHud()
    fun dismissVolumeHud() = UsbAudioEngine.dismissVolumeHud()

    private var isInitialized = false
    private var isExclusiveEnabledSetting = false

    // Permission debouncing flags to prevent duplicate popups
    private var hasRequestedPermission = false
    private var userDeniedPermission = false
    private var lastDeviceIdentifier: String? = null

    fun init(context: Context, exclusiveSetting: Boolean) {
        isExclusiveEnabledSetting = exclusiveSetting
        if (isInitialized) {
            checkCurrentDevices(context)
            return
        }
        isInitialized = true

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            audioManager.registerAudioDeviceCallback(object : android.media.AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                    checkCurrentDevices(context)
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                    checkCurrentDevices(context)
                }
            }, null)
        }

        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_USB_PERMISSION)
            addAction(Intent.ACTION_HEADSET_PLUG)
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent?) {
                val action = intent?.action
                if (action == ACTION_USB_PERMISSION) {
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    val permissionGranted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (permissionGranted && device != null) {
                        userDeniedPermission = false
                        claimUsbDevice(c, device)
                    } else {
                        userDeniedPermission = true
                    }
                } else if (action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                    hasRequestedPermission = false
                    userDeniedPermission = false
                    lastDeviceIdentifier = null
                    UsbAudioEngine.release()
                }
                checkCurrentDevices(c)
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
        } catch (e: Exception) {
            try {
                context.registerReceiver(receiver, filter)
            } catch (ignored: Exception) {}
        }

        checkCurrentDevices(context)
    }

    fun setExclusiveSetting(context: Context, enabled: Boolean) {
        isExclusiveEnabledSetting = enabled
        if (!enabled) {
            UsbAudioEngine.release()
        }
        checkCurrentDevices(context)
    }

    fun setHardwareVolume(percent: Float) {
        UsbAudioEngine.setHardwareVolume(percent)
    }

    fun stepHardwareVolume(up: Boolean) {
        UsbAudioEngine.stepHardwareVolume(up)
    }

    fun isExclusiveActivelyRunning(): Boolean {
        return _dacState.value.isExclusiveActive && UsbAudioEngine.isClaimedAndActive.value
    }

    private fun claimUsbDevice(context: Context, device: UsbDevice) {
        if (!isExclusiveEnabledSetting) return
        UsbAudioEngine.claimAndInitialize(context, device)
    }

    fun checkCurrentDevices(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

        var foundDacName = ""
        var targetAudioDeviceInfo: AudioDeviceInfo? = null
        var targetUsbDevice: UsbDevice? = null

        // 1. Scan via UsbManager
        try {
            val deviceList = usbManager.deviceList
            for (dev in deviceList.values) {
                var isAudio = dev.deviceClass == UsbConstants.USB_CLASS_AUDIO
                if (!isAudio) {
                    for (i in 0 until dev.interfaceCount) {
                        val intf = dev.getInterface(i)
                        if (intf.interfaceClass == UsbConstants.USB_CLASS_AUDIO) {
                            isAudio = true
                            break
                        }
                    }
                }
                if (isAudio) {
                    targetUsbDevice = dev
                    foundDacName = dev.productName ?: dev.deviceName ?: "USB DAC"
                    break
                }
            }
        } catch (e: Exception) {
        }

        // 2. Scan via AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            for (dev in devices) {
                if (dev.type == AudioDeviceInfo.TYPE_USB_DEVICE || 
                    dev.type == AudioDeviceInfo.TYPE_USB_HEADSET || 
                    dev.type == AudioDeviceInfo.TYPE_USB_ACCESSORY) {
                    targetAudioDeviceInfo = dev
                    if (foundDacName.isBlank()) {
                        foundDacName = dev.productName?.toString() ?: "USB Audio DAC"
                    }
                    break
                }
            }
        }

        val wasConnected = _dacState.value.isConnected
        val wasExclusiveActive = _dacState.value.isExclusiveActive
        val isConnectedNow = foundDacName.isNotBlank() || targetAudioDeviceInfo != null
        val currentDevId = targetUsbDevice?.deviceName ?: foundDacName

        if (currentDevId != lastDeviceIdentifier) {
            lastDeviceIdentifier = currentDevId
            hasRequestedPermission = false
            userDeniedPermission = false
        }

        // Single trigger for Android OS USB Permission Prompt
        if (isConnectedNow && isExclusiveEnabledSetting && targetUsbDevice != null) {
            if (usbManager.hasPermission(targetUsbDevice)) {
                claimUsbDevice(context, targetUsbDevice)
            } else if (!hasRequestedPermission && !userDeniedPermission) {
                hasRequestedPermission = true
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val permissionIntent = PendingIntent.getBroadcast(
                    context,
                    0,
                    Intent(ACTION_USB_PERMISSION).setPackage(context.packageName),
                    flags
                )
                usbManager.requestPermission(targetUsbDevice, permissionIntent)
            }
        } else if (!isConnectedNow || !isExclusiveEnabledSetting) {
            UsbAudioEngine.release()
        }

        val isActuallyClaimed = UsbAudioEngine.isClaimedAndActive.value
        val exclusiveActive = isConnectedNow && isExclusiveEnabledSetting && isActuallyClaimed

        val newState = UsbDacInfo(
            isConnected = isConnectedNow,
            dacName = if (foundDacName.isBlank()) "USB Audio DAC" else foundDacName,
            isExclusiveActive = exclusiveActive,
            audioDeviceInfo = targetAudioDeviceInfo,
            usbDevice = targetUsbDevice
        )

        _dacState.value = newState

        if (!wasExclusiveActive && exclusiveActive) {
            Toast.makeText(
                context,
                "Mode Eksklusif Aktif: Terhubung ke ${newState.dacName}",
                Toast.LENGTH_LONG
            ).show()
        } else if (wasConnected && !isConnectedNow) {
            UsbAudioEngine.release()
            hasRequestedPermission = false
            userDeniedPermission = false
            Toast.makeText(
                context,
                "USB DAC Terputus. Mode Eksklusif dinonaktifkan.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}

