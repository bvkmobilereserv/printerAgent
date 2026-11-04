# 🖨️ Sunmi Printer Agent - Dokumentasi & Rangkuman Proyek

Dokumentasi resmi dan rangkuman teknis untuk aplikasi **Sunmi Printer Agent** (`dev.liau.printeragent`).

---

## 📌 Ringkasan Aplikasi

**Sunmi Printer Agent** adalah aplikasi Android berbasis agen cetak (*Printer Agent*) yang berfungsi sebagai **Embedded HTTP Web Server** sekaligus **Thermal Printer Queue Manager**. Aplikasi ini memungkinkan sistem POS, Web Kiosk, atau backend pihak ketiga untuk mengirimkan file dokumen (seperti PDF struk) melalui REST API HTTP POST secara lokal, lalu secara otomatis memproses antrean pencetakan ke perangkat thermal printer (Sunmi Printer & USB ESC/POS).

---

## 🛠️ Spesifikasi & Tech Stack

| Komponen | Spesifikasi / Library |
| :--- | :--- |
| **Package Name** | `dev.liau.printeragent` |
| **Version** | `1.1` (VersionCode: `2`) |
| **Min SDK / Target SDK** | Android 7.0 (API 24) / Android 14 (API 34) |
| **Bahasa Pemrograman** | Kotlin |
| **Web Server** | Ktor CIO Engine (`io.ktor:ktor-server-cio`) |
| **Database Lokal** | Android Room Database (SQLite) |
| **Background Processing** | Android Jetpack WorkManager (`PrintWorker`) |
| **Printer SDK** | Sunmi PrinterX SDK (`com.sunmi:printerx`) & ESC/POS USB Connection |
| **UI Framework** | Android ViewBinding, Material Design 3, RecyclerView |

---

## 🏗️ Arsitektur & Komponen Utama

```mermaid
graph TD
    Client["💻 Client POS / Web Kiosk"] -->|POST /print (Multipart PDF)| WebServer["🌐 WebServerManager (Ktor Engine - Port 2212)"]
    WebServer -->|1. Simpan File PDF| Storage["📁 Local Storage (pdf_prints/)"]
    WebServer -->|2. Insert Record (PENDING)| DB[("🗄️ Room DB (print_queue)")]
    DB -->|3. Trigger Job| Worker["⚙️ PrintWorker (WorkManager)"]
    Worker -->|4. Render PDF to Bitmap| PdfRenderer["🖼️ Android PdfRenderer (576px/384px)"]
    PdfRenderer -->|5. Print Dithered Bitmap| PrinterMgr["🖨️ SunmiPrinterManager / USB ESC/POS"]
    PrinterMgr -->|6. Physical Print & Cut| Paper["📜 Thermal Paper"]
    DB -->|7. Live Data Update (Flow)| UI["📱 MainActivity (RecyclerView)"]
```

### 1. `MainActivity.kt` & UI Dashboard
* **Fitur Utama**:
  * Kontrol **Start/Stop HTTP Server** dan pengaturan port HTTP (Default: `2212`).
  * Informasi URL Server lokal (misal `http://192.168.1.50:2212`) lengkap dengan tombol *Copy Address*.
  * Tampilan antrean cetak *real-time* berbasis `RecyclerView` + `ListAdapter`.
  * Tombol **Clear Queue** (`@id/btnClearQueue`) dengan dialog konfirmasi untuk membersihkan seluruh antrean & file PDF fisik.

### 2. `WebServerManager.kt`
* Menjalankan server HTTP Ktor di dalam perangkat Android.
* Membuka endpoint `POST /print` yang menerima unggahan file PDF via `multipart/form-data`.
* Menyimpan file terunggah ke direktori internal `filesDir/pdf_prints/` dan mencatat entri ke database SQLite dengan status `PENDING`.

### 3. Database Layer (`AppDatabase.kt`, `PrintEntity.kt`, `PrintDao.kt`)
* **Entity `print_queue`**:
  * `id` (Long, Primary Key auto-generate)
  * `fileName` (String)
  * `filePath` (String)
  * `status` (String: `PENDING`, `PRINTING`, `SUCCESS`, `FAILED`)
  * `createdAt` (Long, Timestamp)
* **DAO Methods**:
  * `getAllPrints()`: Mengembalikan `Flow<List<PrintEntity>>` untuk pembaruan UI otomatis.
  * `getPendingPrints()` & `updateStatus()`: Digunakan oleh pengelola antrean worker.
  * `deleteAllPrints()`: Menghapus seluruh riwayat antrean.

### 4. `PrintWorker.kt` (WorkManager)
* Mengelola eksekusi pencetakan di latar belakang (*background task*).
* Membaca file PDF menggunakan `android.graphics.pdf.PdfRenderer`.
* Menyesuaikan lebar target secara proporsional sesuai kertas printer:
  * **80mm Paper**: `576px`
  * **58mm Paper**: `384px`
* Mengaplikasikan algoritma **Dithering** (`ImageAlgorithm.DITHERING`) untuk menghasilkan cetakan hitam-putih yang tajam pada printer thermal.

### 5. `SunmiPrinterManager.kt`
* Layer abstraksi Singleton yang mengisolasi komunikasi dengan `Sunmi PrinterX SDK`.
* **Thread-Safe**: Menggunakan Kotlin Coroutine `Mutex` & `CompletableDeferred` untuk mencegah bentrokan perintah cetak (*race condition*).
* Menyediakan pemantauan status hardware secara interaktif via `StateFlow` (`READY`, `CHECKING`, `ERROR`, `UNKNOWN`).

---

## 📡 REST API Documentation

### **Endpoint: Send Print Job**

> [!NOTE]
> Kirimkan request HTTP POST multipart dari sistem POS / Kiosk Anda ke IP perangkat Android.

* **URL**: `http://<IP_DEVICE>:<PORT>/print` (Contoh: `http://192.168.1.50:2212/print`)
* **Method**: `POST`
* **Content-Type**: `multipart/form-data`

#### **Form-Data Parameters:**

| Parameter | Type | Required | Description |
| :--- | :--- | :--- | :--- |
| `file` | File (`application/pdf`) | **Ya** | Dokumen file PDF yang akan dicetak. |
| `name_file` | Text String | Tidak | Nama kustom untuk tampilan antrean di aplikasi. |

#### **Example Request (cURL):**
```bash
curl -X POST "http://192.168.1.50:2212/print" \
  -F "file=@/path/to/struk_pembayaran.pdf" \
  -F "name_file=Struk Transaksi #10023"
```

#### **Example Response (Success):**
```json
{
  "status": true,
  "message": "File PDF berhasil diterima dan masuk antrean cetak",
  "id": 15
}
```

---

## 🚀 Fitur Utama & Keunggulan

> [!TIP]
> **Resilient & Auto-Recovery**: Jika printer kehabisan kertas mid-print, antrean tidak hilang dan status printer akan diperbarui ke `ERROR`. Begitu kertas diisi ulang, antrean dapat dilanjutkan.

1. **Auto Width Scaling & Dithering**: Otomatis menyesuaikan aspek rasio PDF dengan lebar kertas (80mm / 58mm) tanpa hasil cetak gepeng atau terpotong.
2. **Kiosk Background Agent**: Dapat terus berjalan melayani HTTP request dari client POS eksternal tanpa mengganggu aplikasi utama.
3. **Thread-Safe Printing Queue**: Mengantrekan cetakan secara berurutan (*FIFO*) sehingga tidak ada hasil cetakan yang tumpang tindih saat banyak client mengirim print job bersamaan.
4. **Real-time Live Monitoring**: Pemantauan antrean cetak di UI Android dengan indikator status warna dinamis (Hijau = Success, Biru = Printing, Merah = Failed, Oranye = Pending).
