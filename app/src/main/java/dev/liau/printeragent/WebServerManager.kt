package dev.liau.printeragent
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.gson.*
import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.ktor.server.engine.*
import io.ktor.server.cio.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.content.*
import io.ktor.http.*
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import java.io.File

/**
 * Created by budiliauw87 on 29/10/26
 */
data class UploadResponse(
    val status: String,
    val message: String,
    val path: String?
)

object WebServerManager {
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    var SERVER_PORT :Int = 2212

    fun startServer(context: Context, port:Int, onStatusChanged: (Boolean, String) -> Unit) {
        if (server != null) return // Server sudah jalan
        SERVER_PORT = port
        try {
            server = embeddedServer(CIO, port = SERVER_PORT) {
                // 1. Pasang Plugin ContentNegotiation dengan Gson
                install(ContentNegotiation) {
                    gson {
                        setPrettyPrinting() // Opsional: agar format JSON di browser/postman rapi
                        disableHtmlEscaping()
                    }
                }
                install(StatusPages) {
                    // Menghandle HTTP Status 404 (Not Found / Routing tidak ditemukan)
                    status(HttpStatusCode.NotFound) { call, status ->
                        call.respond(
                            status,
                            UploadResponse(
                                status = "error",
                                message = "Not found",
                                path = null
                            )
                        )
                    }
                    // Menghandle 405 (Method Not Allowed / Path ada, tapi method salah misal GET ke POST)
                    status(HttpStatusCode.MethodNotAllowed) { call, status ->
                        call.respond(
                            status,
                            UploadResponse(
                                status = "error",
                                message = "Invalid Request",
                                path = null
                            )
                        )
                    }

                    // (Opsional) Menghandle error umum / 500 Internal Server Error
                    exception<Throwable> { call, cause ->
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            UploadResponse(
                                status = "error",
                                message = "Terjadi kesalahan pada server: ${cause.localizedMessage}",
                                path = null
                            )
                        )
                    }
                }
                routing {
                    post("/print") {
                        try {
                            val multipart = call.receiveMultipart()
                            var savedPath: String? = null
                            var originalFilename = "document.pdf"
                            var customFileName: String? = null
                            var tempFile: File? = null

                            val directory = File(context.filesDir, "pdf_prints")
                            if (!directory.exists()) directory.mkdirs()

                            multipart.forEachPart { part ->
                                when (part) {
                                    is PartData.FormItem -> {
                                        // Menangkap field teks "name_file" dari form-data
                                        if (part.name == "name_file" && part.value.isNotBlank()) {
                                            customFileName = part.value.trim()
                                        }
                                    }
                                    is PartData.FileItem -> {
                                        originalFilename = part.originalFileName ?: "document.pdf"

                                        // Simpan sementara file yang diunggah
                                        val temp = File(directory, "temp_${System.currentTimeMillis()}.tmp")
                                        part.streamProvider().use { input ->
                                            temp.outputStream().use { output ->
                                                input.copyTo(output)
                                            }
                                        }
                                        tempFile = temp
                                    }
                                    else -> {}
                                }
                                part.dispose()
                            }

                            // Jika file berhasil diunggah, ubah nama file sementara ke nama file akhir
                            if (tempFile != null && tempFile.exists()) {
                                var finalName = customFileName?.replace(Regex("[^a-zA-Z0-9._-]"), "_") ?: originalFilename
                                if (!finalName.endsWith(".pdf", ignoreCase = true)) {
                                    finalName += ".pdf"
                                }

                                val finalFile = File(directory, "${System.currentTimeMillis()}_$finalName")
                                tempFile.renameTo(finalFile)
                                savedPath = finalFile.absolutePath
                            }

                            if (savedPath != null) {
                                // TODO: Simpan path ke SQLite & Trigger Print Service di sini

                                val database = AppDatabase.getDatabase(context)
                                val insertedId = database.printDao().insertPrint(
                                    PrintEntity(fileName = originalFilename, filePath = savedPath!!)
                                )

                                // 2. Masukkan ke Antrean WorkManager secara berurutan
                                val workRequest = OneTimeWorkRequestBuilder<PrintWorker>()
                                    .setInputData(workDataOf(PrintWorker.KEY_PRINT_ID to insertedId))
                                    .build()

                                WorkManager.getInstance(context)
                                    .beginUniqueWork(
                                        "sunmi_print_queue",
                                        ExistingWorkPolicy.APPEND_OR_REPLACE,
                                        workRequest
                                    )
                                    .enqueue()

                                val responseObj = UploadResponse(
                                    status = "success",
                                    message = "PDF berhasil diunggah dan masuk antrean cetak!",
                                    path = savedPath
                                )

                                call.respond(HttpStatusCode.OK, responseObj)
                            } else {
                                call.respond(HttpStatusCode.BadRequest, mapOf("status" to "error", "message" to "File PDF tidak ditemukan."))
                            }
                        } catch (e: Exception) {
                            call.respond(HttpStatusCode.InternalServerError, mapOf("status" to "error", "message" to e.localizedMessage))
                        }
                    }

                    // Endpoint opsional untuk cek health server dari browser
                    get("/") {
                        call.respondText("Android Printer Agent server is Running!")
                    }
                }
            }.start(wait = false)

            onStatusChanged(true, "Server berhasil dijalankan di port $SERVER_PORT")
        } catch (e: Exception) {
            e.printStackTrace()
            server = null
            onStatusChanged(false, "Gagal menjalankan server: ${e.localizedMessage}")
        }
    }

    fun stopServer(onStatusChanged: (Boolean, String) -> Unit) {
        server?.let {
            it.stop(1000, 2000)
            server = null
            onStatusChanged(false, "Server dihentikan.")
        } ?: run {
            onStatusChanged(false, "Server memang sedang tidak aktif.")
        }
    }

    fun isRunning(): Boolean = server != null
}