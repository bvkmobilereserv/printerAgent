package dev.liau.printeragent

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.snackbar.Snackbar
import dev.liau.printeragent.databinding.ActivityMainBinding
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isServerRunning = false
    private var currentServerUrl: String = ""

    //permission notification
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
           startServer()
        }else{
            binding.toggleServer.isChecked = false
            val permissionNotification = Manifest.permission.POST_NOTIFICATIONS
            val isPermanentlyDenied = !ActivityCompat.shouldShowRequestPermissionRationale(this, permissionNotification)

            if (isPermanentlyDenied) {
                // Pengguna menolak permanen, arahkan ke Settings karena dialog sistem tidak bisa muncul lagi
                val snackbar = Snackbar.make(
                    binding.main,
                    getString(R.string.permission_denied_permanently),
                    Snackbar.LENGTH_LONG
                )
                snackbar.setAction(getString(R.string.setting)) {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                    startActivity(intent)
                }
                snackbar.show()
            } else {
                // Pengguna hanya menolak biasa (klik tombol Deny/Tolak biasa)
                Snackbar.make(binding.main, getString(R.string.permission_required_to_start), Snackbar.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        setSupportActionBar(binding.toolbar)
        initViews()
        updateServerUI(WebServerManager.isRunning())

    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            val permissionNotification = Manifest.permission.POST_NOTIFICATIONS

            when {
                ContextCompat.checkSelfPermission(this, permissionNotification) == PackageManager.PERMISSION_GRANTED -> {
                   startServer()
                }
                ActivityCompat.shouldShowRequestPermissionRationale(this, permissionNotification) -> {
                    // Tampilkan Rationale via Snackbar sebelum meminta izin lagi
                    val snackbar = Snackbar.make(
                        binding.main,
                        "Aplikasi membutuhkan izin ini untuk dapat menyalakan engine. Mohon izinkan.",
                        Snackbar.LENGTH_INDEFINITE // Gunakan Indefinite agar snackbar tidak hilang saat dibaca
                    )
                    snackbar.setAction(getString(R.string.try_again)) {
                        // Tembak kembali permission dialog setelah user paham
                        requestPermissionLauncher.launch(permissionNotification)
                    }
                    snackbar.show()
                    binding.toggleServer.isChecked = false
                }
                else ->{
                    requestPermissionLauncher.launch(permissionNotification)
                }
            }
        }
    }

    private fun updateServerUI(running: Boolean) {
        if (running) {
            binding.tvStatusServer.text = getString(R.string.server_running)
            binding.tvStatusServer.setTextColor(
                resources.getColor(
                    android.R.color.holo_green_dark,
                    null
                )
            )
            binding.btnCopy.isEnabled = true

//            val ipAddress = getDeviceIpAddress()
            val ipAddress = getLocalIpAddress()
            currentServerUrl = "http://$ipAddress:${WebServerManager.SERVER_PORT}"
            binding.tvIpServer.text = "URL: $currentServerUrl"

        } else {
            binding.tvStatusServer.text = getString(R.string.stopped)
            binding.tvStatusServer.setTextColor(
                resources.getColor(
                    android.R.color.holo_red_dark,
                    null
                )
            )
            binding.btnCopy.isEnabled = false
            currentServerUrl = ""
            binding.tvIpServer.text = "URL: $currentServerUrl"
        }
    }

    // initiazling the views of the layout
    private fun initViews() {
        binding.toggleServer.setOnCheckedChangeListener { _, isChecked ->

            if(isChecked){
                checkAndRequestPermissions()
            }else{
                stopServer()
            }
        }

        binding.btnCopy.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("URL", currentServerUrl)
            clipboard.setPrimaryClip(clip)
        }

    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.home_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_printer -> {
                startActivity(Intent(this, PrinterTestActivity::class.java))
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }


    private fun startServer(){
        WebServerManager.startServer(applicationContext) { isRunning, message ->
            runOnUiThread {
                updateServerUI(isRunning)
            }
        }
    }

    private fun stopServer(){
        WebServerManager.stopServer { isRunning, message ->
            runOnUiThread {
                updateServerUI(isRunning)
            }
        }
    }

    private fun getDeviceIpAddress(): String {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ipsetInt = wifiManager.connectionInfo.ipAddress
        return String.format(
            Locale.getDefault(),
            "%d.%d.%d.%d",
            (ipsetInt and 0xff),
            (ipsetInt shr 8 and 0xff),
            (ipsetInt shr 16 and 0xff),
            (ipsetInt shr 24 and 0xff)
        )
    }
    private fun getLocalIpAddress(): String = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
            ?.hostAddress ?: "127.0.0.1"
    } catch (_: Exception) {
        "127.0.0.1"
    }
}