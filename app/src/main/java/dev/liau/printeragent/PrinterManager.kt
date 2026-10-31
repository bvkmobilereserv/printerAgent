package dev.liau.printeragent

import android.content.Context
import android.os.Build
import android.util.Log
import com.sunmi.printerx.PrinterSdk
import com.sunmi.printerx.style.TextStyle

/**
 * Created by budiliauw87 on 31/10/26
 */
class PrinterManager(context: Context) {

    companion object {
        private const val TAG = "PrinterManager"
    }

    private val appContext = context.applicationContext

    @Volatile
    private var printer: PrinterSdk.Printer? = null

    @Volatile
    private var connecting = false

    fun connect(callback: (Boolean, String) -> Unit) {

        if (printer != null) {
            callback(true, "Printer sudah terhubung")
            return
        }

        if (connecting) {
            callback(false, "Koneksi sedang diproses")
            return
        }

        val manufacturer = Build.MANUFACTURER.lowercase()
        val model = Build.MODEL


        Log.d(TAG, "Manufacturer: $manufacturer")
        Log.d(TAG, "Model: $model")

        if (manufacturer != "sunmi") {
            callback(false, "Bukan perangkat SUNMI")
            return
        }

        if (model != "K2_MINI") {
            callback(false, "Model tidak sesuai: $model")
            return
        }

        connecting = true

        try {
            PrinterSdk.getInstance().getPrinter(
                appContext,
                object : PrinterSdk.PrinterListen {

                    override fun onPrinters(
                        printers: MutableList<PrinterSdk.Printer>?
                    ) {
                        Log.d(
                            TAG,
                            "Available printers: ${printers?.size}"
                        )
                    }

                    override fun onDefPrinter(
                        printerDevice: PrinterSdk.Printer?
                    ) {
                        printer = printerDevice
                        connecting = false

                        if (printerDevice != null) {
                            Log.d(TAG, "PRINTER CONNECTED")
                            callback(true, "Printer connected")
                        } else {
                            Log.e(TAG, "PRINTER NOT FOUND")
                            callback(false, "Default printer not found")
                        }
                    }
                }
            )

        } catch (e: Exception) {
            connecting = false
            Log.e(TAG, "Connection error", e)
            callback(false, e.message ?: "Connection error")
        }
    }

    fun printText(
        text: String,
        callback: (Boolean, String) -> Unit
    ) {
        try {
            val device = printer

            if (device == null) {
                callback(false, "Printer belum terhubung")
                return
            }

            val lineApi = device.lineApi()

            if (lineApi == null) {
                callback(false, "Printer Line API tidak tersedia")
                return
            }

            lineApi.printText(
                text,
                TextStyle.getStyle()
            )

            callback(true, "Print command sent")

        } catch (e: Exception) {
            Log.e(TAG, "Print error", e)
            callback(false, e.message ?: "Print error")
        }
    }

    fun getStatus(): String {
        return try {
            printer?.queryApi()?.status?.toString()
                ?: "DISCONNECTED"
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }
}