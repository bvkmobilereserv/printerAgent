package dev.liau.printeragent

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.dantsu.escposprinter.connection.usb.UsbPrintersConnections
import com.sunmi.peripheral.printer.InnerPrinterCallback
import com.sunmi.peripheral.printer.InnerPrinterManager
import com.sunmi.peripheral.printer.SunmiPrinterService
import dev.liau.printeragent.databinding.ActivityPrinterTestBinding

class PrinterTestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPrinterTestBinding
    private var sunmiPrinterService: SunmiPrinterService? = null

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
                                this@PrinterTestActivity,
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

        setupListeners()
//        checkPrinterStatus()
        checkPrinterSunmi()
    }

    override fun onResume() {
        super.onResume()
        // Register receiver safely using ContextCompat with export flag for Android 14+
//        val filter = IntentFilter(ACTION_USB_PERMISSION)
//        ContextCompat.registerReceiver(
//            this,
//            usbReceiver,
//            filter,
//            ContextCompat.RECEIVER_NOT_EXPORTED
//        )
    }

    override fun onPause() {
        super.onPause()
        // Unregister receiver to prevent memory leaks
//        try {
//            unregisterReceiver(usbReceiver)
//        } catch (e: IllegalArgumentException) {
//            Log.w(TAG, "Receiver was not registered: ${e.message}")
//        }

        super.onDestroy()
        try {
            if (sunmiPrinterService != null) {
                InnerPrinterManager.getInstance().unBindService(this, null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /* check connection printer sunmi k1 - k2 */
    private fun checkPrinterSunmi() {

        var innerPrinterCallback: InnerPrinterCallback? = null
        innerPrinterCallback = object : InnerPrinterCallback() {
            override fun onConnected(service: SunmiPrinterService) {
                sunmiPrinterService = service
                binding.tvPrinterStatus.text = "Printer Connected"
            }

            override fun onDisconnected() {
                sunmiPrinterService = null
                binding.tvPrinterStatus.text = "Printer Disconnected"
            }
        }

        try {
            InnerPrinterManager.getInstance().bindService(
                this, innerPrinterCallback
            )
            binding.tvPrinterStatus.text = "Printer Binding"
        } catch (e: Exception) {
            binding.tvPrinterStatus.text = "Error: ${e.localizedMessage}"
            e.printStackTrace()
        }
    }


    private fun checkPrinterUsb() {
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
            }
        } catch (e: Exception) {
            binding.tvPrinterStatus.text = "Error: ${e.localizedMessage}"
        }
    }


    private fun checkPrinterStatus() {
        try {
            val printerHelper = SunmiPrinterHelper()
            printerHelper.initPrinter(this)
            printerHelper.printReceipt(this)
        } catch (e: Exception) {
            binding.tvPrinterStatus.text = "Error: ${e.localizedMessage}"
        }
    }

    private fun setupListeners() {
        binding.btnCheckStatus.setOnClickListener {
//            checkPrinterStatus()
            checkPrinterSunmi()
        }
        binding.btnTestPrintText.setOnClickListener { testPrintText() }
        binding.btnTestPrintBitmap.setOnClickListener { testPrintBitmap() }
        binding.btnTestCutPaper.setOnClickListener { testCutPaper() }
    }

    private fun testPrintText() { /* ... */
    }

    private fun testPrintBitmap() { /* ... */
    }

    private fun testCutPaper() { /* ... */
    }

    companion object {
        private const val TAG = "PrinterTestActivity"
        const val ACTION_USB_PERMISSION = "dev.liau.printeragent.USB_PERMISSION"
    }
}