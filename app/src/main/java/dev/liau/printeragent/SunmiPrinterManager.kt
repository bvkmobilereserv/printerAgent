package dev.liau.printeragent

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import com.sunmi.printerx.PrinterSdk
import com.sunmi.printerx.SdkException
import com.sunmi.printerx.api.QueryApi
import com.sunmi.printerx.enums.PrinterInfo
import com.sunmi.printerx.style.BaseStyle
import com.sunmi.printerx.style.BitmapStyle
import com.sunmi.printerx.style.QrStyle
import com.sunmi.printerx.style.TextStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

enum class PrinterState {
    UNKNOWN,
    CHECKING,
    READY,
    ERROR
}

data class PrinterInfoSnapshot(
    val name: String? = null,
    val version: String? = null,
    val type: String? = null,
    val paper: String? = null
)

data class PrinterStatusSnapshot(
    val state: PrinterState,
    val rawStatus: String? = null,
    val message: String? = null,
    val info: PrinterInfoSnapshot? = null
) {
    val isReady: Boolean
        get() = state == PrinterState.READY

    companion object {
        fun unknown(message: String? = null): PrinterStatusSnapshot =
            PrinterStatusSnapshot(PrinterState.UNKNOWN, message = message)
    }
}

class PrinterException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The only component allowed to talk to PrinterX.
 *
 * Initialization follows the supplied K2PrinterPlugin reference: wait for
 * onDefPrinter before using lineApi(), and do not use ExtPrinterService on
 * K2 MINI. Print calls are serialized and the SDK status is checked first.
 */
class SunmiPrinterManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val initMutex = Mutex()
    private val printMutex = Mutex()

    @Volatile
    private var printer: PrinterSdk.Printer? = null

    @Volatile
    private var initDeferred: CompletableDeferred<PrinterSdk.Printer>? = null

    private val _status = MutableStateFlow(PrinterStatusSnapshot.unknown())
    val status: StateFlow<PrinterStatusSnapshot> = _status.asStateFlow()

    val isK2Mini: Boolean
        get() = Build.MANUFACTURER.equals(MANUFACTURER_SUNMI, ignoreCase = true) &&
            Build.MODEL.equals(MODEL_K2_MINI, ignoreCase = true)

    private val printerListener = object : PrinterSdk.PrinterListen {
        override fun onPrinters(printers: MutableList<PrinterSdk.Printer>?) {
            // K2 MINI exposes a default printer through onDefPrinter. The list is
            // logged only; selecting a random printer could target the wrong device.
            Log.d(TAG, "onPrinters received ${printers?.size ?: 0} printer(s)")
        }

        override fun onDefPrinter(defaultPrinter: PrinterSdk.Printer?) {
            try {
                if (defaultPrinter == null) {
                    val error = PrinterException("SUNMI tidak mengembalikan printer default")
                    Log.e(TAG, error.message ?: "SUNMI tidak mengembalikan printer default")
                    _status.value = PrinterStatusSnapshot(
                        PrinterState.ERROR,
                        message = error.message
                    )
                    initDeferred?.completeExceptionally(error)
                    return
                }

                printer = defaultPrinter
                initDeferred?.complete(defaultPrinter)
                Log.i(TAG, "Default printer acquired: $defaultPrinter")
            } catch (e: Exception) {
                Log.e(TAG, "Exception in onDefPrinter listener: ${e.message}", e)
                initDeferred?.completeExceptionally(e)
            }
        }
    }

    /**
     * Initializes PrinterX lazily and at most once concurrently. A timeout clears
     * the in-flight deferred so a manual refresh can retry; it is never treated
     * as a successful initialization.
     */
    private suspend fun ensureInitialized(): PrinterSdk.Printer {
        printer?.let { return it }

        return initMutex.withLock {
            printer?.let { return@withLock it }

            val deferred = initDeferred ?: CompletableDeferred<PrinterSdk.Printer>().also { created ->
                initDeferred = created
                try {
                    // The reference plugin calls getPrinter from the main thread.
                    withContext(Dispatchers.Main.immediate) {
                        PrinterSdk.getInstance().getPrinter(
                            appContext, printerListener)
                    }
                } catch (e: Throwable) {
                    initDeferred = null
                    val error = PrinterException("Gagal menginisialisasi printer SUNMI", e)
                    if (!created.isCompleted) created.completeExceptionally(error)
                    _status.value = PrinterStatusSnapshot(
                        PrinterState.ERROR,
                        message = error.message
                    )
                    throw error
                }
            }

            try {
                withTimeout(INIT_TIMEOUT_MS) { deferred.await() }
            } catch (e: TimeoutCancellationException) {
                initDeferred = null
                val error = PrinterException(
                    "Printer SUNMI tidak merespons dalam ${INIT_TIMEOUT_MS / 1000} detik"
                )
                _status.value = PrinterStatusSnapshot(
                    PrinterState.ERROR,
                    message = error.message
                )
                throw error
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                initDeferred = null
                val error = if (e is PrinterException) {
                    e
                } else {
                    PrinterException("Gagal menunggu inisialisasi printer", e)
                }
                _status.value = errorSnapshot(error)
                throw error
            }
        }
    }

    /** Refreshes status in the background and publishes it through [status]. */
    fun refreshStatus(): Job = scope.launch {
        _status.value = _status.value.copy(
            state = PrinterState.CHECKING,
            rawStatus = null,
            message = "Memeriksa printer..."
        )
        try {
            queryStatus()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _status.value = errorSnapshot(e)
        }
    }

    /** Queries PrinterX directly and updates [status] with the result. */
    suspend fun queryStatus(): PrinterStatusSnapshot {
        val activePrinter = try {
            ensureInitialized()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return errorSnapshot(e).also { _status.value = it }
        }

        return printMutex.withLock {
            readStatus(activePrinter).also { _status.value = it }
        }
    }

    suspend fun printBitmap(
        bitmap: Bitmap,
        style: BitmapStyle = BitmapStyle.getStyle(),
        autoOut: Boolean = true
    ) {
        if (bitmap.isRecycled) {
            throw PrinterException("Bitmap sudah di-recycle dan tidak dapat dicetak")
        }

        withReadyPrinter { activePrinter ->
            try {
                val line = activePrinter.lineApi()
                line.printBitmap(bitmap, style)
                if (autoOut) line.autoOut()
            } catch (e: SdkException) {
                throw PrinterException("Gagal mengirim bitmap ke printer", e)
            }
        }
    }

    suspend fun printText(
        text: String,
        style: TextStyle = TextStyle.getStyle(),
        autoOut: Boolean = true
    ) {
        withReadyPrinter { activePrinter ->
            try {
                val line = activePrinter.lineApi()
                line.printText(text, style)
                if (autoOut) line.autoOut()
            } catch (e: SdkException) {
                throw PrinterException("Gagal mengirim teks ke printer", e)
            }
        }
    }

    suspend fun printTexts(
        texts: Array<String>,
        widths: IntArray,
        styles: Array<TextStyle>,
        autoOut: Boolean = true
    ) {
        require(texts.size == widths.size && texts.size == styles.size) {
            "Jumlah texts, widths, dan styles harus sama"
        }

        withReadyPrinter { activePrinter ->
            try {
                val line = activePrinter.lineApi()
                line.printTexts(texts, widths, styles)
                if (autoOut) line.autoOut()
            } catch (e: SdkException) {
                throw PrinterException("Gagal mengirim kolom teks ke printer", e)
            }
        }
    }

    suspend fun printQrCode(
        data: String,
        style: QrStyle = QrStyle.getStyle(),
        autoOut: Boolean = true
    ) {
        withReadyPrinter { activePrinter ->
            try {
                val line = activePrinter.lineApi()
                line.printQrCode(data, style)
                if (autoOut) line.autoOut()
            } catch (e: SdkException) {
                throw PrinterException("Gagal mengirim QR code ke printer", e)
            }
        }
    }

    /** Resets the line/alignment state before a print block. */
    suspend fun initLine(style: BaseStyle = BaseStyle.getStyle()) {
        withReadyPrinter { activePrinter ->
            try {
                activePrinter.lineApi().initLine(style)
            } catch (e: SdkException) {
                throw PrinterException("Gagal menginisialisasi line printer", e)
            }
        }
    }

    /** Ends the current print block without printing additional content. */
    suspend fun autoOut() {
        withReadyPrinter { activePrinter ->
            try {
                activePrinter.lineApi().autoOut()
            } catch (e: SdkException) {
                throw PrinterException("Gagal mengakhiri perintah cetak", e)
            }
        }
    }

    private suspend fun <T> withReadyPrinter(block: suspend (PrinterSdk.Printer) -> T): T =
        withContext(Dispatchers.IO) {
            printMutex.withLock {
                val activePrinter = ensureInitialized()
                val snapshot = readStatus(activePrinter)
                _status.value = snapshot

                if (snapshot.state != PrinterState.READY) {
                    throw PrinterException(snapshot.message ?: "Printer tidak siap")
                }
                block(activePrinter)
            }
        }

    private fun readStatus(activePrinter: PrinterSdk.Printer): PrinterStatusSnapshot {
        val query = try {
            activePrinter.queryApi()
        } catch (e: SdkException) {
            return errorSnapshot(e)
        }

        val info = readInfo(query)
        val status = try {
            query.getStatus()
        } catch (e: SdkException) {
            return errorSnapshot(e, info)
        }

        return PrinterStatusMapper.map(status, info)
    }

    private fun readInfo(query: QueryApi): PrinterInfoSnapshot? {
        val name = safeInfo(query, PrinterInfo.NAME)
        val version = safeInfo(query, PrinterInfo.VERSION)
        val type = safeInfo(query, PrinterInfo.TYPE)
        val paper = safeInfo(query, PrinterInfo.PAPER)

        if (name == null && version == null && type == null && paper == null) return null
        return PrinterInfoSnapshot(name, version, type, paper)
    }

    private fun safeInfo(query: QueryApi, key: PrinterInfo): String? =
        try {
            query.getInfo(key)?.takeIf { it.isNotBlank() }
        } catch (e: SdkException) {
            null
        }

    private fun errorSnapshot(
        throwable: Throwable,
        info: PrinterInfoSnapshot? = null
    ): PrinterStatusSnapshot = PrinterStatusSnapshot(
        state = PrinterState.ERROR,
        rawStatus = null,
        message = throwable.message ?: "Terjadi kesalahan printer",
        info = info
    )

    companion object {
        private const val TAG = "SunmiPrinterManager"
        private const val MANUFACTURER_SUNMI = "sunmi"
        private const val MODEL_K2_MINI = "K2_MINI"
        private const val INIT_TIMEOUT_MS = 10_000L

        @Volatile
        private var instance: SunmiPrinterManager? = null

        fun getInstance(context: Context): SunmiPrinterManager =
            instance ?: synchronized(this) {
                instance ?: SunmiPrinterManager(context.applicationContext).also { instance = it }
            }
    }
}
