package dev.liau.printeragent


import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
// Sesuaikan import berikut dengan package SDK Sunmi di project Anda


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

    private fun renderAndPrintPdf(filePath: String): Boolean {
        val file = File(filePath)
        if (!file.exists()) return false

        val parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val pdfRenderer = PdfRenderer(parcelFileDescriptor)

        try {
//            val printer = SdkManager.getInstance().printer
//            if (printer != null && printer.isOpened) {
//                printer.init()
//
//                for (i in 0 until pdfRenderer.pageCount) {
//                    val page = pdfRenderer.openPage(i)
//
//                    // K2 Mini mendukung lebar 384 dot (58mm) atau 576 dot (80mm)
//                    val targetWidth = 576
//                    val targetHeight = (targetWidth.toFloat() / page.width * page.height).toInt()
//
//                    val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
//                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
//
//                    // Kirim Bitmap ke buffer printer Sunmi[cite: 3, 4]
//                    printer.startTransBuffer(bitmap.width, bitmap.height)
//                    printer.printBitmap(0, 0, bitmap)
//                    printer.endTransBuffer()
//
//                    page.close()
//                }
//
//                // Potong kertas otomatis setelah seluruh halaman selesai[cite: 4, 5]
//                printer.cutPaper(0, 0)
//                return true
//            }
            return false
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
            pdfRenderer.close()
            parcelFileDescriptor.close()
        }
    }
}