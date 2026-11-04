package dev.liau.printeragent

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sunmi.printerx.SdkException
import com.sunmi.printerx.enums.Align
import com.sunmi.printerx.enums.ImageAlgorithm
import com.sunmi.printerx.style.BaseStyle
import com.sunmi.printerx.style.BitmapStyle
import com.sunmi.printerx.style.QrStyle
import com.sunmi.printerx.style.TextStyle
import dev.liau.printeragent.databinding.ActivityPrinterTestBinding
import kotlinx.coroutines.launch

class PrinterTestActivity : AppCompatActivity() {
    private val printerManager by lazy {
        SunmiPrinterManager.getInstance(this)
    }
    private lateinit var binding: ActivityPrinterTestBinding
    private var isReadyPrinter = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityPrinterTestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
        }
        binding.toolbar.setNavigationOnClickListener { finish() }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                printerManager.status.collect { snapshot ->
                    isReadyPrinter = snapshot.isReady

                    binding.tvPrinterStatus.text = when (snapshot.state) {
                        PrinterState.READY -> {
                            val paperSize = when (snapshot.info?.paper) {
                                "576" -> "80mm"
                                "58" -> "58mm" // Contoh jika ada ukuran lain
                                else -> snapshot.info?.paper ?: "-"
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
        setupListeners()
        checkPrinterStatus()
    }

    override fun onResume() {
        super.onResume()

    }

    override fun onPause() {
        super.onPause()
    }


    private fun checkPrinterStatus() {
        Log.e("checkPrinterStatus", "Ok")
        printerManager.refreshStatus()
    }

    private fun setupListeners() {
        binding.btnCheckStatus.setOnClickListener { checkPrinterStatus() }
        binding.btnTestPrintText.setOnClickListener { testPrintText() }
        binding.btnTestPrintBitmap.setOnClickListener { testPrintBitmap() }
    }

    private fun testPrintText() {

        lifecycleScope.launch {
            try {
                // Inisialisasi perataan teks (Tengah)
                printerManager.initLine(
                    BaseStyle.getStyle().setAlign(Align.CENTER)
                )

                // Cetak Judul Struk (AutoOut = false agar kertas belum dipotong)
                printerManager.printText(
                    "==============================\n" +
                            "       BUANA MOBILE DEV       \n" +
                            "==============================\n\n",
                    style = TextStyle.getStyle().enableBold(true).setTextSize(28),
                    autoOut = false
                )

                // Cetak Baris Tabel / Multi Kolom (Product, Qty, Price)
                printerManager.printTexts(
                    texts = arrayOf("Kopi Hitam", "2x", "Rp 30.000"),
                    widths = intArrayOf(2, 1, 1),
                    styles = arrayOf(
                        TextStyle.getStyle().setAlign(Align.LEFT),
                        TextStyle.getStyle().setAlign(Align.CENTER),
                        TextStyle.getStyle().setAlign(Align.RIGHT)
                    ),
                    autoOut = false
                )

                // Cetak QR Code Struk & Potong Kertas (AutoOut = true)
                printerManager.printQrCode(
                    data = "https://bvk.co.id/",
                    style = QrStyle.getStyle().setDot(8).setAlign(Align.CENTER),
                    autoOut = false // Melakukan feed & cut kertas otomatis setelah QR
                )
                printerManager.printText(
                    "-------------------------------\n" +
                            "            Good Test          \n" +
                            "-------------------------------\n\n",
                    style = TextStyle.getStyle().enableBold(true).setTextSize(28),
                    autoOut = true
                )

                Toast.makeText(
                    this@PrinterTestActivity,
                    "Berhasil mencetak struk!",
                    Toast.LENGTH_SHORT
                ).show()

            } catch (e: PrinterException) {
                // Tangkap exception spesifik printer (misal: kertas habis atau printer disconnected)
                Toast.makeText(
                    this@PrinterTestActivity,
                    "Gagal mencetak: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    }

    private fun testPrintBitmap() {

        lifecycleScope.launch {
            try {
                // Inisialisasi perataan teks (Tengah)
                printerManager.initLine(
                    BaseStyle.getStyle().setAlign(Align.CENTER)
                )

                // Cetak Judul Struk (AutoOut = false agar kertas belum dipotong)
                printerManager.printText(
                    "==============================\n" +
                            "       BUANA MOBILE DEV       \n" +
                            "==============================\n\n",
                    style = TextStyle.getStyle().enableBold(true).setTextSize(28),
                    autoOut = false
                )

                val optionsBitmapFactory = BitmapFactory.Options()
                printerManager.printBitmap(
                    BitmapFactory.decodeResource(
                        getResources(),
                        R.mipmap.ic_launcher,
                        optionsBitmapFactory
                    ),
                    BitmapStyle.getStyle()
                        .setAlign(Align.CENTER).setAlgorithm(ImageAlgorithm.DITHERING).setValue(120)
                        .setWidth(250).setHeight(200),
                    false
                )

                printerManager.printText(
                    "-------------------------------\n" +
                            "            Good Test          \n" +
                            "-------------------------------\n\n",
                    style = TextStyle.getStyle().enableBold(true).setTextSize(28),
                    autoOut = true
                )

                Toast.makeText(
                    this@PrinterTestActivity,
                    "Berhasil mencetak gambar!",
                    Toast.LENGTH_SHORT
                ).show()

            } catch (e: PrinterException) {
                // Tangkap exception spesifik printer (misal: kertas habis atau printer disconnected)
                Toast.makeText(
                    this@PrinterTestActivity,
                    "Gagal mencetak: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    }

    companion object {
        private const val TAG = "PrinterTestActivity"

    }
}