package dev.liau.printeragent


import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sunmi.printerx.enums.Align
import com.sunmi.printerx.enums.ImageAlgorithm
import com.sunmi.printerx.style.BaseStyle
import com.sunmi.printerx.style.BitmapStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File


class PrintWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_PRINT_ID = "print_id"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val printId = inputData.getLong(KEY_PRINT_ID, -1L)
        if (printId == -1L) return@withContext Result.failure()

        val dao = AppDatabase.getDatabase(applicationContext).printDao()
        val printTask = dao.getPrintById(printId) ?: return@withContext Result.failure()

        try {
            dao.updateStatus(printTask.id, "PRINTING")

            // Render PDF ke Bitmap dan cetak ke Sunmi Printer

            val success = renderAndPrintPdf(printTask.filePath)

            if (success) {
                dao.updateStatus(printTask.id, "SUCCESS")
                Result.success()
            } else {
                dao.updateStatus(printTask.id, "FAILED")
                Result.failure()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            dao.updateStatus(printTask.id, "FAILED")
            Result.failure()
        }
    }

    private fun scaleBitmapToFitWidth(srcBitmap: Bitmap, targetWidth: Int): Bitmap {
        if (srcBitmap.width == targetWidth) return srcBitmap // Sudah pas

        // Hitung tinggi proporsional berdasarkan rasio asli gambar
        val aspectRatio = srcBitmap.height.toFloat() / srcBitmap.width.toFloat()
        val targetHeight = (targetWidth * aspectRatio).toInt()

        return Bitmap.createScaledBitmap(srcBitmap, targetWidth, targetHeight, true)
    }

    private suspend fun renderAndPrintPdf(filePath: String): Boolean {
        val file = File(filePath)
        if (!file.exists()) return false

        val parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val pdfRenderer = PdfRenderer(parcelFileDescriptor)

        try {
            // Di dalam doWork() atau fungsi renderAndPrintPdf di PrintWorker.kt
            val printerManager = SunmiPrinterManager.getInstance(applicationContext)

            // 1. Query status printer secara langsung untuk memastikan siap
            val currentStatus = printerManager.queryStatus()

            if (!currentStatus.isReady) {
                // Jika printer tidak siap, gagalkan pekerjaan worker atau beri pesan error
                Log.e("PrintWorker", "Printer tidak siap: ${currentStatus.message}")
                return false
            }

            for (i in 0 until pdfRenderer.pageCount) {
                val page = pdfRenderer.openPage(i)

                // K2 Mini mendukung lebar 384 dot (58mm) atau 576 dot (80mm)
                val targetWidth = 576
                val targetHeight = (targetWidth.toFloat() / page.width * page.height).toInt()

                val bitmap =
                    Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                // 3. Kirim ke printer dengan algoritma Dithering
                printerManager.printBitmap(
                    bitmap = bitmap,
                    style = BitmapStyle.getStyle()
                        .setAlign(Align.CENTER)
                        .setAlgorithm(ImageAlgorithm.DITHERING)
                        .setValue(120),
                    autoOut = true
                )
                page.close()
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
            pdfRenderer.close()
            parcelFileDescriptor.close()
        }
    }
}