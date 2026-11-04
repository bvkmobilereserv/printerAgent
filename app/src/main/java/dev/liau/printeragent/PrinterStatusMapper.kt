package dev.liau.printeragent

import com.sunmi.printerx.enums.Status

/**
 * Pure mapping from PrinterX [Status] values to the UI-facing [PrinterStatusSnapshot].
 *
 * Kept separate from [SunmiPrinterManager] so the mapping can be unit-tested on the
 * JVM without an Android Context or a physical printer.
 *
 * WARN_* statuses still allow printing (paper is low, not gone). Every ERR_* status,
 * OFFLINE, and COMM blocks printing. UNKNOWN is not treated as ready because the
 * reference plugin shows that the SDK can report it without a usable printer.
 */
internal object PrinterStatusMapper {

    fun map(status: Status, info: PrinterInfoSnapshot? = null): PrinterStatusSnapshot {
        val name = status.name
        val state = when {
            status == Status.READY -> PrinterState.READY
            name.startsWith("WARN_") -> PrinterState.READY
            name.startsWith("ERR_") -> PrinterState.ERROR
            status == Status.OFFLINE || status == Status.COMM -> PrinterState.ERROR
            else -> PrinterState.UNKNOWN
        }

        return PrinterStatusSnapshot(
            state = state,
            rawStatus = name,
            message = describe(status),
            info = info
        )
    }

    fun describe(status: Status): String = when (status.name) {
        "READY" -> "Printer siap"
        "OFFLINE" -> "Printer offline"
        "COMM" -> "Komunikasi printer gagal"
        "UNKNOWN" -> "Status printer tidak diketahui"
        "WARN_THERMAL_PAPER" -> "Kertas thermal hampir habis"
        "WARN_SPECIAL_PAPER" -> "Kertas khusus hampir habis"
        "WARN_STANDARD_PAPER" -> "Kertas hampir habis"
        "WARN_PICK_PAPER" -> "Printer perlu diperiksa"
        "WARN_CARTRIDGE" -> "Cartridge perlu diperiksa"
        "ERR_PAPER_OUT" -> "Kertas habis"
        "ERR_PAPER_JAM" -> "Kertas macet"
        "ERR_PAPER_MISMATCH" -> "Jenis kertas tidak sesuai"
        "ERR_PRINTER_HOT" -> "Printer terlalu panas"
        "ERR_MOTOR_HOT" -> "Motor printer terlalu panas"
        "ERR_COVER" -> "Cover printer terbuka"
        "ERR_COVER_INCOMPLETE" -> "Cover printer tidak tertutup sempurna"
        "ERR_CUTTER" -> "Cutter printer bermasalah"
        else -> status.name.replace('_', ' ')
    }
}
