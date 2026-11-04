package dev.liau.printeragent

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.sunmi.extprinterservice.ExtPrinterService


class KioskPrinterHelper(private val context: Context) {


    private var extPrinter: ExtPrinterService? = null

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            extPrinter = ExtPrinterService.Stub.asInterface(service)
            Log.d("K1", "K1 Printer Connected status=${extPrinter?.printerStatus}")
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            extPrinter = null
        }
    }

    fun bind() {
        val intent = Intent().apply {
            setPackage("com.sunmi.extprinterservice")
            action = "com.sunmi.extprinterservice.PrinterService"
        }
        context.bindService(intent, conn, Context.BIND_AUTO_CREATE)
    }

    fun printReceiptK1() {
        // K1/K2 tidak pakai ICallback, semua sync
        // 0 = normal, 2 = out of paper, 1 = open cover
        if (extPrinter?.printerStatus != 0) {
            Log.e("K1", "Printer tidak siap: ${extPrinter?.printerStatus}")
            return
        }

        extPrinter?.printerInit() // clear cache

        // K1/K2 support transaction juga tapi namanya beda
        extPrinter?.startTransBuffer() // mulai buffer

        extPrinter?.setAlignMode(1)
        extPrinter?.printText("SELF ORDER K1\n")
        extPrinter?.setFontZoom(1, 1)
        extPrinter?.printText("----------------------------\n")

        extPrinter?.setAlignMode(0)
        extPrinter?.printColumnsText(
            arrayOf("Kopi Susu", "2x", "36.000"),
            intArrayOf(14, 4, 10),
            intArrayOf(0, 1, 2)
        )

        extPrinter?.setAlignMode(1)
        extPrinter?.printQrCode("ORDER-12345", 6, 1) // code, moduleSize 1-16, errorLevel 0-3
        extPrinter?.printText("\n")

        extPrinter?.lineWrap(3)

        extPrinter?.cutPaper(0, 0) // m=0 full cut, n=0 auto feed

        extPrinter?.endTransBuffer() // commit dan print
    }

    fun unbind() {
        context.unbindService(conn)
    }
}