package dev.liau.printeragent

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.usb.UsbConnection
import com.dantsu.escposprinter.connection.usb.UsbPrintersConnections
import com.dantsu.escposprinter.textparser.PrinterTextParserImg
import com.sunmi.printerx.PrinterSdk
import com.sunmi.printerx.SdkException
import com.sunmi.printerx.enums.Align
import com.sunmi.printerx.enums.ImageAlgorithm
import com.sunmi.printerx.enums.PrinterInfo
import com.sunmi.printerx.style.BaseStyle
import com.sunmi.printerx.style.BitmapStyle
import dev.liau.printeragent.databinding.ActivityPrinterDiagBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class PrinterDiag : AppCompatActivity() {

    private lateinit var binding: ActivityPrinterDiagBinding
    private val kioskPrinterHelper by lazy { KioskPrinterHelper(this) }
    private var selectPrinter: PrinterSdk.Printer? = null
    private val printerManager by lazy {
        SunmiPrinterManager.getInstance(this)
    }
    private var selectedBitmap: Bitmap? = null
    private val MANUFACTURER = Build.MANUFACTURER
    private val MODEL = Build.MODEL
    private var selectedFile: Uri? = null
    private var isReadyPrinter = false

    private val selectPdfLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                renderPdfToPreview(it)

                val extension = getFileExtension(this, it)
                // Validasi apakah benar PDF
                if (extension.equals("pdf", ignoreCase = true)) {
                    renderPdfToPreview(it)
                } else {
                    // Beritahu pengguna bahwa file bukan PDF
                    Toast.makeText(this@PrinterDiag, "Pilih file PDF yang valid", Toast.LENGTH_SHORT).show()
                }

            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityPrinterDiagBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        setupListeners()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                printerManager.status.collect { snapshot ->
                    isReadyPrinter = snapshot.isReady

                    binding.tvPrinterStatus.text = when (snapshot.state) {
                        PrinterState.READY -> {
                            val paperSize = when (snapshot.info?.paper) {
                                "576" -> "80mm"
                                "58" -> "58mm" // Contoh jika ada ukuran lain
                                else -> snapshot.info?.paper?: "-"
                            }
                            "Printer Siap (${snapshot.info?.name ?: "-"} / $paperSize)"
                        }
                        PrinterState.CHECKING -> "Memeriksa printer..."
                        PrinterState.ERROR -> "Error: ${snapshot.message}"
                        PrinterState.UNKNOWN -> "Status tidak diketahui"
                    }
                    binding.btnTestPrintText.isEnabled = isReadyPrinter
                    binding.btnTestPrintBitmap.isEnabled = isReadyPrinter
                }
            }
        }
    }

    fun getFileExtension(context: Context, uri: Uri): String? {
        // 1. Dapatkan MIME type dari Uri menggunakan ContentResolver
        val mimeType = context.contentResolver.getType(uri)
        // 2. Petakan MIME type ke ekstensi file (contoh: "application/pdf" -> "pdf")
        return MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
    }

    private fun setupListeners() {
        binding.btnCheckStatus.setOnClickListener {
//            checkPrinterStatus()
//            checkPrinterStatusSunmi()
            printerManager.refreshStatus()
        }
        binding.btnTestPrintText.setOnClickListener { testPrintText() }
        binding.btnTestPrintBitmap.setOnClickListener {
//            testPrintBitmap()
            printSampleBitmap()

        }
        binding.btnTestPrintBitmap.setOnLongClickListener {
            selectPdfLauncher.launch("application/pdf")
            true
        }
        binding.ivPreviewPrinter.setOnClickListener {
            selectPdfLauncher.launch("application/pdf")
        }
        binding.btnTestCutPaper.setOnClickListener { testCutPaper() }
    }

    //test library sunmi printer

    private fun checkPrinterStatusSunmi() {

        Log.e(TAG, "MANUFACTOR : $MANUFACTURER, Model : $MODEL")

        if (MODEL == "K1" || MODEL == "K2") {
            try {
                PrinterSdk.getInstance().getPrinter(this, object : PrinterSdk.PrinterListen {
                    override fun onDefPrinter(defaultPrinter: PrinterSdk.Printer?) {
                        try {
                            Log.e(TAG, "onDefPrinter: $defaultPrinter")
                            selectPrinter = defaultPrinter
                            if (defaultPrinter == null) {
                                Log.e(TAG, "Default printer is null")
                                return
                            }
                            val queryApi = selectPrinter?.queryApi()
                            val statusPrinter = queryApi?.status
                            val namePrinter = queryApi?.getInfo(PrinterInfo.NAME)
                            val typePrinter = queryApi?.getInfo(PrinterInfo.TYPE)
                            val paperPrinter = queryApi?.getInfo(PrinterInfo.PAPER)
                            Log.e(
                                TAG,
                                "statusPrinter: $statusPrinter\nnamePrinter: $namePrinter\ntypePrinter: $typePrinter\npaperPrinter: $paperPrinter"
                            )
                            binding.tvPrinterStatus.text =
                                "statusPrinter: $statusPrinter\nnamePrinter: $namePrinter\ntypePrinter: $typePrinter\npaperPrinter: $paperPrinter"

//                            val api: FileApi? = selectPrinter?.fileApi()
//                            api?.printFile("https://dummyimage.com/489x396/000000/fff&text=print+sunmi+example", object : PrintResult() {
//                                override fun onResult(resultCode: Int, message: String?) {
//                                    Log.e(TAG, "onResult: $resultCode, $message")
//                                }
//                            })
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in onDefPrinter: ${e.message}", e)
                        }
                    }

                    override fun onPrinters(printers: MutableList<PrinterSdk.Printer>?) {
                        Log.e(TAG, "onPrinters: $printers")
                    }

                })
            } catch (e: SdkException) {
                e.printStackTrace()
                Log.e(TAG, "Error initializing printer: ${e.message}")
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e(TAG, "Error accessing printer SDK: ${e.message}")
            }


        } else {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    printerManager.status.collect { snapshot ->
                        binding.tvPrinterStatus.text = buildString {
                            append("State: ${snapshot.state}\n")
                            append("Message: ${snapshot.message ?: "OK"}\n")
                            snapshot.info?.let { info ->
                                append("Device: ${info.name} (${info.type})\n")
                                append("Paper: ${info.paper}")
                            }
                        }
                        binding.btnTestPrintText.isEnabled = snapshot.isReady
                        binding.btnTestPrintBitmap.isEnabled = snapshot.isReady
                    }
                }
            }
        }
    }



    private fun renderPdfToPreview(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Salin file dari Uri ke cache lokal aplikasi
                val cacheFile = File(cacheDir, "temp_preview_${System.currentTimeMillis()}.pdf")
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    FileOutputStream(cacheFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                } ?: throw IOException("Gagal membuka input stream dari URI")


//                val pfd: ParcelFileDescriptor = contentResolver.openFileDescriptor(uri, "r") ?: return@launch

                val pfd = ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val pdfRenderer = PdfRenderer(pfd)
                if (pdfRenderer.pageCount > 0) {
                    val page = pdfRenderer.openPage(0)

                    // Target width 576 dots for 80mm thermal printer at 203 DPI
                    val targetWidth = 576
                    // 2. Hitung tinggi secara proporsional (Aspect Ratio)
                    val aspectRatio = page.height.toFloat() / page.width.toFloat()
                    val targetHeight = (targetWidth * aspectRatio).toInt()

                    val bitmap =
                        Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)

                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    pdfRenderer.close()
                    pfd.close()

                    // Hapus file cache setelah selesai dibaca agar tidak menumpuk memori internal
                    cacheFile.delete()

                    selectedBitmap = bitmap

                    withContext(Dispatchers.Main) {
                        binding.ivPreviewPrinter.setImageBitmap(bitmap)
                        binding.tvPrinterStatus.text = "PDF rendered to preview successfully"
                        Toast.makeText(
                            this@PrinterDiag,
                            "PDF loaded into preview",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error rendering PDF", e)
                withContext(Dispatchers.Main) {
                    binding.tvPrinterStatus.text = "Failed to render PDF: ${e.localizedMessage}"
                    Toast.makeText(
                        this@PrinterDiag,
                        "Failed to render PDF: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    fun scaleBitmapToFitWidth(srcBitmap: Bitmap, targetWidth: Int): Bitmap {
        if (srcBitmap.width == targetWidth) return srcBitmap // Sudah pas

        // Hitung tinggi proporsional berdasarkan rasio asli gambar
        val aspectRatio = srcBitmap.height.toFloat() / srcBitmap.width.toFloat()
        val targetHeight = (targetWidth * aspectRatio).toInt()

        return Bitmap.createScaledBitmap(srcBitmap, targetWidth, targetHeight, true)
    }

    private fun checkPrinterStatus() {
        Log.e("checkPrinterStatus", "Ok")

        try {
            val usbConnection = UsbPrintersConnections.selectFirstConnected(this)
            val usbManager = getSystemService(Context.USB_SERVICE) as? UsbManager

            if (usbConnection == null || usbManager == null) {
                AlertDialog.Builder(this)
                    .setTitle("USB Connection")
                    .setMessage("No USB printer found.")
                    .setPositiveButton("OK", null)
                    .show()
                return
            }

            val device = usbConnection.device
            if (!usbManager.hasPermission(device)) {
                Log.i(TAG, "Request USB permission for device: ${device.deviceName}")
                val intent = Intent(ACTION_USB_PERMISSION).setPackage(packageName)
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val permissionIntent = PendingIntent.getBroadcast(this, 0, intent, flags)
                usbManager.requestPermission(device, permissionIntent)
            } else {
                Log.i(TAG, "Already has USB permission for device: ${device.deviceName}")
                printUsb(usbManager, device)
            }
        } catch (e: Exception) {
            binding.tvPrinterStatus.text = "Error: ${e.localizedMessage}"

        }
    }

    private fun printUsb(usbManager: UsbManager, usbDevice: UsbDevice) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val usbConnection = UsbConnection(usbManager, usbDevice)
                val printer = EscPosPrinter(usbConnection, 203, 80f, 47)

                withContext(Dispatchers.Main) {
                    binding.tvPrinterStatus.text = "Print completed successfully"
                    Toast.makeText(this@PrinterDiag, "Print successful", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "USB Print Error", e)
                withContext(Dispatchers.Main) {
                    binding.tvPrinterStatus.text = "Print error: ${e.localizedMessage}"
                    Toast.makeText(
                        this@PrinterDiag,
                        "Print failed: ${e.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }


    private fun testPrintText() {
        val bitmap = selectedBitmap ?: run {
            Toast.makeText(this, "Select a PDF file to render", Toast.LENGTH_SHORT).show()
            selectPdfLauncher.launch("application/pdf")
            return
        }
        try {
            // Query paper width to determine layout (80mm is approx 576px, 58mm is approx 384px)
            val paperType = selectPrinter?.queryApi()?.getInfo(PrinterInfo.PAPER)
            val is80mm = paperType?.contains("576") == true
            val maxWidth = if (is80mm) 576 else 384
            Log.e(TAG, "Lebar $maxWidth")

            // Pastikan bitmap disesuaikan dengan maxWidth kertas
            val scaledBitmap = scaleBitmapToFitWidth(bitmap, maxWidth)
            // Adjust column weights for different widths
            val columnWeights = if (is80mm) intArrayOf(2, 1, 1) else intArrayOf(1, 1, 1)

            selectPrinter?.lineApi()?.run {
                initLine(BaseStyle.getStyle().setAlign(Align.CENTER).setWidth(maxWidth))
                val optionsBitmapFactory = BitmapFactory.Options()
                val height = bitmap.height
                optionsBitmapFactory.inScaled = false
                printBitmap(
                    bitmap,
                    BitmapStyle.getStyle()
                        .setAlign(Align.CENTER)
                        .setAlgorithm(ImageAlgorithm.DITHERING)
                        .setValue(120)
                        .setWidth(maxWidth)
                        .setHeight(scaledBitmap.height)
                )
//                printBitmap(
//                    BitmapFactory.decodeResource(
//                        getResources(),
//                        R.mipmap.ic_launcher,
//                        optionsBitmapFactory
//                    ),
//                    BitmapStyle.getStyle()
//                        .setAlign(Align.CENTER).setAlgorithm(ImageAlgorithm.DITHERING).setValue(120)
//                        .setWidth(if (is80mm) 250 else 180).setHeight(if (is80mm) 200 else 140)
//                )
//                printDividingLine(DividingLine.EMPTY, 30);
//                printDividingLine(DividingLine.DOTTED, 2);
//
//                addText("My Favorite Store\n", TextStyle.getStyle().enableBold(true).setTextSize(if (is80mm) 28 else 24));
//                addText("Address: Calle Falsa 123\n", TextStyle.getStyle());
//                addText("Phone: 123-456-7890\n", TextStyle.getStyle());
//                printDividingLine(DividingLine.EMPTY, 30);
//                printDividingLine(DividingLine.DOTTED, 2);
//                val align = TextStyle.getStyle().setAlign(Align.LEFT)
//                printTexts(
//                    arrayOf<String>("Product", "Qty", "Price"),
//                    columnWeights,
//                    arrayOf<TextStyle?>(align, align, align)
//                )
//                printTexts(
//                    arrayOf<String>("Apples", "2", "$1.00"),
//                    intArrayOf(1, 1, 1),
//                    arrayOf<TextStyle?>(align, align, align)
//                )
//                printTexts(
//                    arrayOf<String>("Bananas", "3", "$0.50"),
//                    intArrayOf(1, 1, 1),
//                    arrayOf<TextStyle?>(align, align, align)
//                )
//                printText("-------------------------------\n", TextStyle.getStyle());
//                initLine(BaseStyle.getStyle().setAlign(Align.RIGHT));
//                printText("Subtotal:           $3.50\n", TextStyle.getStyle().setTextSize(18));
//                printText("IVA (16%):          $0.56\n", TextStyle.getStyle().setTextSize(18));
//                printText("TOTAL:              $2,94\n", TextStyle.getStyle().setTextSize(22));
//
//                printText(
//                    "Thank you for shopping!",
//                    TextStyle.getStyle().enableBold(true).setTextSize(24)
//                );
//                val time = Calendar.getInstance().time
//                val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm")
//                val currentDate = formatter.format(time)
//                printText(currentDate, TextStyle.getStyle());
//                printDividingLine(DividingLine.EMPTY, 30);
//                printQrCode(
//                    "Order:00001;TOTAL: $2.94; Date:" + currentDate,
//                    QrStyle.getStyle().setDot(9).setAlign(Align.CENTER)
//                );
//                printDividingLine(DividingLine.EMPTY, 10);
//                printText(
//                    "Order:00001;TOTAL: $2.94; Date:" + currentDate,
//                    TextStyle.getStyle().setTextSize(24)
//                )
                autoOut()

//                autoOut()
//

//                printText("==============================\n")
//                printText("       SUNMI PRINTER AGENT    \n")
//                printText("==============================\n\n")

            }

//            selectPrinter?.canvasApi()?.run {
//                initCanvas(BaseStyle.getStyle().setWidth(384).setHeight(220))
//                renderArea(
//                    AreaStyle.getStyle().setStyle(Shape.BOX).setPosX(0).setPosY(0).setWidth(384)
//                        .setHeight(219)
//                )
//                renderText(
//                    "可口可乐(2L)", TextStyle.getStyle().setTextSize(30).enableBold(true)
//                        .setPosX(10).setPosY(20)
//                )
//                renderText(
//                    "2L", TextStyle.getStyle().setTextSize(20)
//                        .setPosX(10).setPosY(60)
//                )
//                renderText(
//                    "200000", TextStyle.getStyle().setTextSize(20)
//                        .setPosX(10).setPosY(85)
//                )
//                renderText(
//                    "瓶", TextStyle.getStyle().setTextSize(24)
//                        .setPosX(10).setPosY(130)
//                )
//
//                renderText(
//                    "￥ 7.8",
//                    TextStyle.getStyle().setTextSize(16).setTextWidthRatio(1).setTextHeightRatio(1)
//                        .enableBold(true).setPosX(190).setPosY(160)
//                )
//                printCanvas(1, object : PrintResult() {
//                    override fun onResult(resultCode: Int, message: String?) {
//                        if (resultCode == 0) {
//                            //打印完成
//                        } else {
//                            //打印失败
//                            println(selectPrinter?.queryApi()?.status)
//                        }
//                    }
//                })
//            }
        } catch (e: SdkException) {
            e.printStackTrace()
            Toast.makeText(this@PrinterDiag, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
        }


//        lifecycleScope.launch {
//            try {
//                printerManager.printText("==============================\n", autoOut = false)
//                printerManager.printText("       SUNMI PRINTER AGENT    \n", autoOut = false)
//                printerManager.printText("==============================\n\n", autoOut = false)
//                printerManager.printQrCode("https://sunmi.com", autoOut = true)
//            } catch (e: PrinterException) {
//                Toast.makeText(this@PrinterDiag, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
//            }
//        }
    }

    private fun getBitmapFromDrawable(drawableId: Int): Bitmap {
        val drawable = ContextCompat.getDrawable(this, drawableId)
            ?: return Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth.coerceAtLeast(100),
            drawable.intrinsicHeight.coerceAtLeast(100),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun printSampleBitmap() {
        val bitmap = selectedBitmap ?: run {
            Toast.makeText(this, "Select a PDF file to render", Toast.LENGTH_SHORT).show()
            selectPdfLauncher.launch("application/pdf")
            return
        }
        lifecycleScope.launch {
            try {
                // 1. Cek ukuran kertas printer dari status snapshot
                val paperInfo = printerManager.status.value.info?.paper
                val is80mm = paperInfo?.contains("576") == true
                val targetWidth = if (is80mm) 576 else 384

                // 2. Pastikan bitmap sesuai lebar kertas secara
                val scaledBitmap = scaleBitmapToFitWidth(bitmap, targetWidth)

                // 3. Kirim ke printer dengan algoritma Dithering
                printerManager.printBitmap(
                    bitmap = scaledBitmap,
                    style = BitmapStyle.getStyle()
                        .setAlign(Align.CENTER)
                        .setAlgorithm(ImageAlgorithm.DITHERING)
                        .setValue(120),
                    autoOut = true
                )

                Toast.makeText(this@PrinterDiag, "Cetak Bitmap Sukses!", Toast.LENGTH_SHORT).show()

            } catch (e: PrinterException) {
                Toast.makeText(this@PrinterDiag, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }


//        lifecycleScope.launch {
//            try {
//                val api: FileApi? = selectPrinter?.fileApi()
//                api?.printFile(
//                    selectedFile.toString(),
//                    object : PrintResult() {
//                        override fun onResult(resultCode: Int, message: String?) {
//                            Log.e(TAG, "onResult: $resultCode, $message")
//                        }
//                    })
//            } catch (e: Exception) {
//                e.printStackTrace()
//                Toast.makeText(this@PrinterDiag, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
//            }
//        }


//        lifecycleScope.launch {
//            try {
//                val bitmap = getBitmapFromDrawable(R.drawable.ic_launcher_foreground)
//                printerManager.printBitmap(bitmap, autoOut = true)
//            } catch (e: Exception) {
//                Toast.makeText(this@PrinterDiag, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
//            }
//        }
    }

    private fun testPrintBitmap() {
        if (selectedBitmap == null) {
            Toast.makeText(this, "Select a PDF file to render", Toast.LENGTH_SHORT).show()
            selectPdfLauncher.launch("application/pdf")
            return
        }

        try {
            val usbConnection = UsbPrintersConnections.selectFirstConnected(this)
            val usbManager = getSystemService(USB_SERVICE) as? UsbManager

            if (usbConnection == null || usbManager == null) {
                AlertDialog.Builder(this)
                    .setTitle("USB Connection")
                    .setMessage("No USB printer found.")
                    .setPositiveButton("OK", null)
                    .show()
                return
            }

            val device = usbConnection.device
            if (!usbManager.hasPermission(device)) {
                Log.i(TAG, "Request USB permission for device: ${device.deviceName}")
                val intent = Intent(ACTION_USB_PERMISSION).setPackage(packageName)
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
                val permissionIntent = PendingIntent.getBroadcast(this, 0, intent, flags)
                usbManager.requestPermission(device, permissionIntent)
            } else {
                printBitmapUsb(usbManager, device)
            }
        } catch (e: Exception) {
            binding.tvPrinterStatus.text = "Error: ${e.localizedMessage}"
        }
    }

    private fun printBitmapUsb(usbManager: UsbManager, usbDevice: UsbDevice) {
        val bitmapToPrint = selectedBitmap ?: run {
            Toast.makeText(this, "Select a PDF file first", Toast.LENGTH_SHORT).show()
            selectPdfLauncher.launch("application/pdf")
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val usbConnection = UsbConnection(usbManager, usbDevice)
                val printer = EscPosPrinter(usbConnection, 203, 80f, 47)

                val hexImage =
                    PrinterTextParserImg.bitmapToHexadecimalString(printer, bitmapToPrint)

                printer.printFormattedTextAndCut(
                    "[C]<img>$hexImage</img>\n" +
                            "[L]\n"
                )

                withContext(Dispatchers.Main) {
                    binding.tvPrinterStatus.text = "Bitmap printed successfully"
                    Toast.makeText(this@PrinterDiag, "Bitmap print successful", Toast.LENGTH_SHORT)
                        .show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Bitmap Print Error", e)
                withContext(Dispatchers.Main) {
                    binding.tvPrinterStatus.text = "Print error: ${e.localizedMessage}"
                    Toast.makeText(
                        this@PrinterDiag,
                        "Bitmap print failed: ${e.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun testCutPaper() { /* ... */
    }


//    override fun onStop() {
//        super.onStop()
//        // 5. Putuskan (unbind) koneksi service agar tidak bocor (memory leak)
//        kioskPrinterHelper.unbind()
//    }

    override fun onResume() {
        super.onResume()
        // Register receiver safely using ContextCompat with export flag for Android 14+
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        ContextCompat.registerReceiver(
            this,
            usbReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onPause() {
        super.onPause()
        // Unregister receiver to prevent memory leaks
        try {
            unregisterReceiver(usbReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Receiver was not registered: ${e.message}")
        }
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val action = intent.action
            Log.d(TAG, "onReceive action: $action")
            if (ACTION_USB_PERMISSION == action) {
                synchronized(this) {
                    val usbDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    }

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (usbDevice != null) {
                            Log.i(TAG, "USB Permission granted for device: ${usbDevice.deviceName}")
                            Toast.makeText(
                                this@PrinterDiag,
                                "USB Permission granted for device: ${usbDevice.deviceName}",
                                Toast.LENGTH_SHORT
                            ).show()

                        }
                    } else {
                        Log.w(TAG, "USB Permission denied for device: ${usbDevice?.deviceName}")
                    }
                }
            }
        }
    }


    companion object {
        private const val TAG = "PrinterDiag"
        const val ACTION_USB_PERMISSION = "dev.liau.printeragent.USB_PERMISSION"
    }
}