package com.ethernet.controller.util

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.net.Inet4Address
import java.net.NetworkInterface

object EthernetUtils {

    data class EthernetInfo(
        val isConnected: Boolean,
        val isUp: Boolean,
        val ip: String,
        val mac: String
    )

    fun isAdapterConnected(context: Context? = null): Boolean {
        // 1. Check NetworkInterface list
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces != null && interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val name = iface.name.lowercase()
                if (name.startsWith("eth") || name.startsWith("lan") || name.startsWith("usb") || name.startsWith("rndis")) {
                    return true
                }
            }
        } catch (_: Exception) {}

        // 2. Check /proc/net/dev (readable by untrusted apps on Android)
        try {
            val procFile = File("/proc/net/dev")
            if (procFile.exists() && procFile.canRead()) {
                BufferedReader(FileReader(procFile)).use { reader ->
                    var line = reader.readLine()
                    while (line != null) {
                        val trimmed = line.trim().lowercase()
                        if (trimmed.startsWith("eth") || trimmed.contains(":")) {
                            val ifaceName = trimmed.substringBefore(":").trim()
                            if (ifaceName.startsWith("eth") || ifaceName.startsWith("lan") || ifaceName.startsWith("usb")) {
                                return true
                            }
                        }
                        line = reader.readLine()
                    }
                }
            }
        } catch (_: Exception) {}

        // 3. Check UsbManager for connected USB OTG Ethernet/Network adapters
        if (context != null) {
            try {
                val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
                val deviceList = usbManager?.deviceList
                if (deviceList != null && deviceList.isNotEmpty()) {
                    for ((_, device) in deviceList) {
                        // Check Device Class
                        val devClass = device.deviceClass
                        if (devClass == UsbConstants.USB_CLASS_COMM ||
                            devClass == UsbConstants.USB_CLASS_CDC_DATA ||
                            devClass == UsbConstants.USB_CLASS_VENDOR_SPEC ||
                            devClass == UsbConstants.USB_CLASS_PER_INTERFACE
                        ) {
                            return true
                        }
                        // Check Interface Classes
                        for (i in 0 until device.interfaceCount) {
                            val iface = device.getInterface(i)
                            val ifClass = iface.interfaceClass
                            if (ifClass == UsbConstants.USB_CLASS_COMM ||
                                ifClass == UsbConstants.USB_CLASS_CDC_DATA ||
                                ifClass == UsbConstants.USB_CLASS_VENDOR_SPEC
                            ) {
                                return true
                            }
                        }
                    }
                    // Any USB device attached on OTG port
                    return true
                }
            } catch (_: Exception) {}
        }

        return false
    }

    fun getEthernetInfo(context: Context? = null): EthernetInfo {
        var ethFound = false
        var ethIp = "Nessun IP"
        var ethMac = "Non disponibile"
        var isUp = false

        // Check active Network Interfaces
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces != null && interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val name = iface.name.lowercase()
                if (name.startsWith("eth") || name.contains("eth")) {
                    ethFound = true
                    isUp = iface.isUp

                    val macBytes = iface.hardwareAddress
                    if (macBytes != null && macBytes.isNotEmpty()) {
                        ethMac = macBytes.joinToString(":") { String.format("%02x", it) }
                    }

                    val addrs = iface.inetAddresses
                    while (addrs != null && addrs.hasMoreElements()) {
                        val addr = addrs.nextElement()
                        if (addr is Inet4Address && !addr.isLoopbackAddress) {
                            ethIp = addr.hostAddress ?: ethIp
                        }
                    }
                    break
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // If not found via NetworkInterface, check /proc/net/dev and UsbManager
        if (!ethFound) {
            ethFound = isAdapterConnected(context)
        }

        return EthernetInfo(
            isConnected = ethFound,
            isUp = isUp,
            ip = ethIp,
            mac = ethMac
        )
    }

    fun isEthernetActive(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}
