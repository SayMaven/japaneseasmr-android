package com.saymaven.downloader.japaneseasmr.service.usb

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * UsbAudioEngine:
 * 1. Opens USB Device connection for hardware-level DAC control.
 * 2. Parses UAC1 & UAC2 descriptors to detect Feature Units and Volume/Mute controls.
 * 3. Controls DAC hardware volume directly via USB Control Transfers (SET_CUR / GET_RANGE).
 * 4. Manages Floating Volume HUD trigger state for physical key presses.
 */
object UsbAudioEngine {

    private const val TAG = "UsbAudioEngine"

    // USB Audio Class constants
    private const val USB_SUBCLASS_AUDIOCONTROL = 1

    // Descriptor types & subtypes
    private const val CS_INTERFACE = 0x24
    private const val UAC_FEATURE_UNIT = 0x06

    // UAC Control selectors
    private const val FU_VOLUME_CONTROL = 0x02
    private const val FU_MUTE_CONTROL = 0x01

    // UAC1 Requests
    private const val UAC1_REQ_SET_CUR = 0x01
    private const val UAC1_REQ_GET_CUR = 0x81
    private const val UAC1_REQ_GET_MIN = 0x82
    private const val UAC1_REQ_GET_MAX = 0x83
    private const val UAC1_REQ_GET_RES = 0x84

    // UAC2 Requests
    private const val UAC2_REQ_CUR = 0x01
    private const val UAC2_REQ_RANGE = 0x02

    // USB standard requests
    private const val BM_REQ_CLASS_INTERFACE_SET = 0x21
    private const val BM_REQ_CLASS_INTERFACE_GET = 0xA1

    private val engineScope = CoroutineScope(Dispatchers.IO)

    // Hardware volume state: 0.0f (mute/min) to 1.0f (100% full scale)
    private val _hardwareVolume = MutableStateFlow(0.85f)
    val hardwareVolume = _hardwareVolume.asStateFlow()

    private val _isHardwareVolumeSupported = MutableStateFlow(false)
    val isHardwareVolumeSupported = _isHardwareVolumeSupported.asStateFlow()

    private val _isClaimedAndActive = MutableStateFlow(false)
    val isClaimedAndActive = _isClaimedAndActive.asStateFlow()

    // Floating Volume HUD visibility state
    private val _showVolumeHud = MutableStateFlow(false)
    val showVolumeHud = _showVolumeHud.asStateFlow()
    private var hudDismissJob: Job? = null

    // Active connection state
    private var usbConnection: UsbDeviceConnection? = null
    private var usbDevice: UsbDevice? = null
    private var audioControlInterfaceId: Int = 0

    // UAC Parsing state
    private var isUac2 = false
    private var featureUnitId: Int = -1
    private var volumeMinDb: Short = (-96 * 256).toShort() // default -96 dB in 1/256 dB units
    private var volumeMaxDb: Short = 0.toShort()          // default 0 dB
    private var volumeResDb: Short = 128.toShort()        // 0.5 dB
    private var hasFeatureUnitVolume = false

    /**
     * Initializes USB device connection for direct hardware volume and controls.
     */
    @Synchronized
    fun claimAndInitialize(context: Context, device: UsbDevice): Boolean {
        if (usbDevice?.deviceName == device.deviceName && usbConnection != null && _isClaimedAndActive.value) {
            return true
        }

        release()

        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        if (!usbManager.hasPermission(device)) {
            Log.w(TAG, "Cannot access USB DAC: No permission granted for ${device.deviceName}")
            return false
        }

        val connection = try {
            usbManager.openDevice(device)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open USB device connection", e)
            null
        } ?: return false

        this.usbConnection = connection
        this.usbDevice = device

        // Locate Audio Control interface ID
        var acId = 0
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass == UsbConstants.USB_CLASS_AUDIO) {
                if (intf.interfaceSubclass == USB_SUBCLASS_AUDIOCONTROL) {
                    acId = intf.id
                    break
                }
            }
        }
        this.audioControlInterfaceId = acId

        // Parse UAC descriptors for Feature Unit (Hardware Volume)
        parseUacDescriptors(connection, device, acId)

        // Read initial hardware volume
        initHardwareVolume()

        _isClaimedAndActive.value = true
        Log.i(TAG, "USB DAC Hardware Takeover Complete for ${device.productName ?: device.deviceName}")
        return true
    }

    private fun parseUacDescriptors(connection: UsbDeviceConnection, device: UsbDevice, acId: Int) {
        hasFeatureUnitVolume = false
        featureUnitId = -1

        val raw = try {
            connection.rawDescriptors
        } catch (e: Exception) {
            null
        }

        if (raw == null || raw.isEmpty()) {
            scanFeatureUnitFallback(connection, acId)
            return
        }

        var idx = 0
        while (idx < raw.size - 2) {
            val length = raw[idx].toInt() and 0xFF
            if (length < 2 || idx + length > raw.size) break

            val descriptorType = raw[idx + 1].toInt() and 0xFF
            if (descriptorType == CS_INTERFACE && length >= 5) {
                val subType = raw[idx + 2].toInt() and 0xFF
                if (subType == UAC_FEATURE_UNIT) {
                    val unitId = raw[idx + 3].toInt() and 0xFF
                    Log.i(TAG, "Found UAC Feature Unit with ID: $unitId")
                    featureUnitId = unitId
                    hasFeatureUnitVolume = true
                    break
                }
            }
            idx += length
        }

        if (!hasFeatureUnitVolume) {
            scanFeatureUnitFallback(connection, acId)
        }
    }

    private fun scanFeatureUnitFallback(connection: UsbDeviceConnection, interfaceNum: Int) {
        val buf = ByteArray(4)
        for (unitId in 1..16) {
            val wValue = (FU_VOLUME_CONTROL shl 8) or 0x00
            val wIndex = (unitId shl 8) or (interfaceNum and 0xFF)
            val res1 = connection.controlTransfer(
                BM_REQ_CLASS_INTERFACE_GET,
                UAC1_REQ_GET_CUR,
                wValue,
                wIndex,
                buf,
                2,
                200
            )
            if (res1 >= 2) {
                featureUnitId = unitId
                hasFeatureUnitVolume = true
                isUac2 = false
                Log.i(TAG, "Probed UAC1 Feature Unit ID: $unitId")
                return
            }

            val res2 = connection.controlTransfer(
                BM_REQ_CLASS_INTERFACE_GET,
                UAC2_REQ_CUR,
                wValue,
                wIndex,
                buf,
                2,
                200
            )
            if (res2 >= 2) {
                featureUnitId = unitId
                hasFeatureUnitVolume = true
                isUac2 = true
                Log.i(TAG, "Probed UAC2 Feature Unit ID: $unitId")
                return
            }
        }
    }

    private fun initHardwareVolume() {
        val conn = usbConnection ?: return
        val acId = audioControlInterfaceId

        if (hasFeatureUnitVolume && featureUnitId > 0) {
            val buf = ByteArray(8)
            val wIndex = (featureUnitId shl 8) or (acId and 0xFF)
            val wValue = (FU_VOLUME_CONTROL shl 8) or 0x00

            try {
                if (!isUac2) {
                    val minRes = conn.controlTransfer(BM_REQ_CLASS_INTERFACE_GET, UAC1_REQ_GET_MIN, wValue, wIndex, buf, 2, 200)
                    if (minRes >= 2) volumeMinDb = ((buf[1].toInt() shl 8) or (buf[0].toInt() and 0xFF)).toShort()

                    val maxRes = conn.controlTransfer(BM_REQ_CLASS_INTERFACE_GET, UAC1_REQ_GET_MAX, wValue, wIndex, buf, 2, 200)
                    if (maxRes >= 2) volumeMaxDb = ((buf[1].toInt() shl 8) or (buf[0].toInt() and 0xFF)).toShort()

                    val resRes = conn.controlTransfer(BM_REQ_CLASS_INTERFACE_GET, UAC1_REQ_GET_RES, wValue, wIndex, buf, 2, 200)
                    if (resRes >= 2) volumeResDb = ((buf[1].toInt() shl 8) or (buf[0].toInt() and 0xFF)).toShort()

                    val curRes = conn.controlTransfer(BM_REQ_CLASS_INTERFACE_GET, UAC1_REQ_GET_CUR, wValue, wIndex, buf, 2, 200)
                    if (curRes >= 2) {
                        val curDb = ((buf[1].toInt() shl 8) or (buf[0].toInt() and 0xFF)).toShort()
                        val percent = dbToPercent(curDb, volumeMinDb, volumeMaxDb)
                        _hardwareVolume.value = percent
                    }
                    _isHardwareVolumeSupported.value = true
                    return
                } else {
                    val rangeRes = conn.controlTransfer(BM_REQ_CLASS_INTERFACE_GET, UAC2_REQ_RANGE, wValue, wIndex, buf, 8, 200)
                    if (rangeRes >= 8) {
                        volumeMinDb = ((buf[3].toInt() shl 8) or (buf[2].toInt() and 0xFF)).toShort()
                        volumeMaxDb = ((buf[5].toInt() shl 8) or (buf[4].toInt() and 0xFF)).toShort()
                        volumeResDb = ((buf[7].toInt() shl 8) or (buf[6].toInt() and 0xFF)).toShort()
                    }
                    val curRes = conn.controlTransfer(BM_REQ_CLASS_INTERFACE_GET, UAC2_REQ_CUR, wValue, wIndex, buf, 2, 200)
                    if (curRes >= 2) {
                        val curDb = ((buf[1].toInt() shl 8) or (buf[0].toInt() and 0xFF)).toShort()
                        _hardwareVolume.value = dbToPercent(curDb, volumeMinDb, volumeMaxDb)
                    }
                    _isHardwareVolumeSupported.value = true
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed reading hardware volume properties", e)
            }
        }

        _isHardwareVolumeSupported.value = true
    }

    /**
     * Sets hardware volume and triggers the floating volume HUD overlay.
     */
    fun setHardwareVolume(percent: Float, triggerHud: Boolean = true) {
        val clamped = percent.coerceIn(0.0f, 1.0f)
        _hardwareVolume.value = clamped

        if (triggerHud) {
            triggerVolumeHud()
        }

        val conn = usbConnection ?: return
        val acId = audioControlInterfaceId

        if (hasFeatureUnitVolume && featureUnitId > 0) {
            engineScope.launch {
                try {
                    val dbValue = percentToDb(clamped, volumeMinDb, volumeMaxDb)
                    val buf = ByteArray(2)
                    buf[0] = (dbValue.toInt() and 0xFF).toByte()
                    buf[1] = ((dbValue.toInt() shr 8) and 0xFF).toByte()

                    for (ch in 0..2) {
                        val wValue = (FU_VOLUME_CONTROL shl 8) or ch
                        val wIndex = (featureUnitId shl 8) or (acId and 0xFF)
                        val req = if (isUac2) UAC2_REQ_CUR else UAC1_REQ_SET_CUR
                        conn.controlTransfer(
                            BM_REQ_CLASS_INTERFACE_SET,
                            req,
                            wValue,
                            wIndex,
                            buf,
                            2,
                            150
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting hardware volume on DAC", e)
                }
            }
        }
    }

    /**
     * Increments or decrements the hardware volume by a step (e.g. from volume button).
     */
    fun stepHardwareVolume(up: Boolean) {
        val step = 0.05f // 5% step
        val current = _hardwareVolume.value
        val newVol = if (up) (current + step).coerceAtMost(1.0f) else (current - step).coerceAtLeast(0.0f)
        setHardwareVolume(newVol, triggerHud = true)
    }

    /**
     * Triggers the floating volume HUD and resets the auto-dismiss timer.
     */
    fun triggerVolumeHud() {
        _showVolumeHud.value = true
        hudDismissJob?.cancel()
        hudDismissJob = engineScope.launch {
            delay(2000L)
            _showVolumeHud.value = false
        }
    }

    fun dismissVolumeHud() {
        hudDismissJob?.cancel()
        _showVolumeHud.value = false
    }

    private fun percentToDb(percent: Float, minDb: Short, maxDb: Short): Short {
        if (percent <= 0.001f) return minDb
        if (percent >= 0.999f) return maxDb
        val factor = percent.toDouble().pow(2.0)
        val dbRange = maxDb.toDouble() - minDb.toDouble()
        val calculated = minDb.toDouble() + (factor * dbRange)
        return calculated.roundToInt().toShort()
    }

    private fun dbToPercent(currentDb: Short, minDb: Short, maxDb: Short): Float {
        val dbRange = maxDb.toDouble() - minDb.toDouble()
        if (dbRange <= 0) return 1.0f
        val linear = (currentDb.toDouble() - minDb.toDouble()) / dbRange
        return linear.coerceIn(0.0, 1.0).pow(0.5).toFloat()
    }

    @Synchronized
    fun release() {
        try {
            usbConnection?.close()
        } catch (ignored: Exception) {}

        usbConnection = null
        usbDevice = null
        _isClaimedAndActive.value = false
        _isHardwareVolumeSupported.value = false
        _showVolumeHud.value = false
        hudDismissJob?.cancel()
        Log.i(TAG, "USB Audio Engine released and disconnected")
    }
}
