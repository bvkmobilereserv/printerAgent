package dev.liau.printeragent

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.core.graphics.createBitmap
import com.sunmi.peripheral.printer.InnerPrinterCallback
import com.sunmi.peripheral.printer.InnerPrinterManager
import com.sunmi.peripheral.printer.InnerResultCallback
import com.sunmi.peripheral.printer.SunmiPrinterService
import java.io.File

@Suppress("unused")
class SunmiPrinterHelper {
    var sunmiPrinterService: SunmiPrinterService? = null
    private var innerPrinterCallback: InnerPrinterCallback? = null

    // 1. Inisialisasi Koneksi Printer
    fun initPrinter(context: Context) {
        innerPrinterCallback = object : InnerPrinterCallback() {
            override fun onConnected(service: SunmiPrinterService) {
                sunmiPrinterService = service
            }

            override fun onDisconnected() {
                sunmiPrinterService = null
            }
        }

        try {
            InnerPrinterManager.getInstance().bindService(context, innerPrinterCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 2. Metode Cetak Teks & Auto-Cut Standar
    fun printReceipt(context: Context) {
        val service = sunmiPrinterService ?: return

        try {
            service.printerInit(null)
            service.setFontSize(24f, null)

            service.printText("     TOKO SUNMI KIOK K1\n", null)
            service.printText("================================\n", null)
            service.printText("Item A                Rp 50.000\n", null)
            service.printText("Item B                Rp 25.000\n", null)
            service.printText("================================\n", null)
            service.printText("Total                 Rp 75.000\n\n", null)

            service.lineWrap(3, null)

            // Auto-Cut
            service.cutPaper(object : InnerResultCallback() {
                override fun onRunResult(isSuccess: Boolean) {
                    // Berhasil potong kertas
                }

                override fun onReturnString(result: String?) {}

                override fun onRaiseException(code: Int, msg: String?) {
                    // Gagal potong kertas
                }

                override fun onPrintResult(code: Int, msg: String?) {}
            })
        } catch (e: Exception) {
            Toast.makeText(context,"Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    // 3. Tambahan: Fungsi untuk Mencetak File PDF
    fun printPdfFile(pdfFile: File) {
        val service = sunmiPrinterService ?: return

        try {
            service.printerInit(null)

            // Membuka file PDF menggunakan Android PdfRenderer
            val fileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val pdfRenderer = PdfRenderer(fileDescriptor)

            // Iterasi / Ulangi proses untuk setiap halaman PDF
            for (i in 0 until pdfRenderer.pageCount) {
                val page = pdfRenderer.openPage(i)

                // Printer Sunmi K1 (80mm) memiliki lebar efektif sekitar 576 pixel
                val targetWidth = 576
                // Sesuaikan tinggi secara proporsional dengan aspek rasio halaman PDF
                val targetHeight = (targetWidth * page.height) / page.width

                // Membuat Canvas kosong berukuran printer 80mm
                val bitmap = createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)

                // Bersihkan bitmap dengan warna putih (PdfRenderer tidak menghapus background)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)

                // Merender halaman PDF ke dalam format Bitmap
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                // Mengirim Bitmap ke Printer Sunmi
                service.printBitmap(bitmap, null)

                // Lepaskan resource halaman
                page.close()
                bitmap.recycle()
            }

            // Tutup PDF renderer
            pdfRenderer.close()
            fileDescriptor.close()

            // Beri jarak lalu potong kertas
            service.lineWrap(3, null)
            service.cutPaper(null)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 4. Putus Koneksi saat Activity/Fragment Hancur
    fun unbindPrinter(context: Context) {
        try {
            if (innerPrinterCallback != null) {
                InnerPrinterManager.getInstance().unBindService(
                    context, innerPrinterCallback
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
