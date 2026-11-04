package dev.liau.printeragent

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import dev.liau.printeragent.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var printQueueAdapter: PrintQueueAdapter
    private var currentServerUrl: String = ""
    private var portServer :Int = 2212

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
        }else{
            startServer()
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
            val ipAddress = getLocalIpAddress()
            currentServerUrl = "http://$ipAddress:${portServer}"
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
        portServer.toString().also {
            binding.edtInputPort.setText(it)
        }
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

        binding.btnClearQueue.setOnClickListener {
            showClearQueueConfirmationDialog()
        }

        setupRecyclerView()
        observePrintQueue()
    }

    private fun showClearQueueConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.clear_queue))
            .setMessage("Apakah Anda yakin ingin menghapus semua antrean cetak?")
            .setPositiveButton("Hapus") { _, _ ->
                clearPrintQueue()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun clearPrintQueue() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Hapus semua data antrean dari tabel SQLite
                val printDao = AppDatabase.getDatabase(applicationContext).printDao()
                printDao.deleteAllPrints()

                // Hapus file fisik PDF yang tersimpan di pdf_prints
                val directory = File(filesDir, "pdf_prints")
                if (directory.exists()) {
                    directory.listFiles()?.forEach { file ->
                        file.delete()
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Antrean cetak berhasil dibersihkan", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing print queue", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Gagal menghapus antrean: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupRecyclerView() {
        printQueueAdapter = PrintQueueAdapter()
        binding.rvqueuePrinter.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = printQueueAdapter
        }
    }

    private fun observePrintQueue() {
        val printDao = AppDatabase.getDatabase(this).printDao()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                printDao.getAllPrints().collect { list ->
                    printQueueAdapter.submitList(list)
                }
            }
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
        Log.e(TAG, "Starting server...")
        //validasi input port
        val inputPort = binding.edtInputPort.text.toString().trim()
        val portServer = inputPort.toIntOrNull()
        if (portServer == null || portServer < 1 || portServer > 65535) {
            Toast.makeText(this, "Masukkan port yang valid (1-65535)", Toast.LENGTH_SHORT).show()
            binding.toggleServer.setChecked(false)
            return
        }

        this.portServer = portServer


        WebServerManager.startServer(applicationContext, portServer) { isRunning, message ->
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

    private fun getLocalIpAddress(): String = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
            ?.hostAddress ?: "127.0.0.1"
    } catch (_: Exception) {
        "127.0.0.1"
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}