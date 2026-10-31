package dev.liau.printeragent


import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "print_queue")
data class PrintEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileName: String,
    val filePath: String,
    val status: String = "PENDING", // PENDING, PRINTING, SUCCESS, FAILED
    val createdAt: Long = System.currentTimeMillis()
)