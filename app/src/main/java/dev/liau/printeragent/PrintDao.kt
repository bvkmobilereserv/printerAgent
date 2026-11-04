package dev.liau.printeragent


import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PrintDao {
    @Insert
    suspend fun insertPrint(print: PrintEntity): Long

    @Query("SELECT * FROM print_queue WHERE id = :id")
    suspend fun getPrintById(id: Long): PrintEntity?

    @Query("SELECT * FROM print_queue WHERE status = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPendingPrints(): List<PrintEntity>

    @Query("SELECT * FROM print_queue ORDER BY createdAt DESC")
    fun getAllPrints(): Flow<List<PrintEntity>>

    @Query("UPDATE print_queue SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("UPDATE print_queue SET status = 'PENDING' WHERE status = 'PRINTING'")
    suspend fun resetPrintingToPending()

    @Query("DELETE FROM print_queue")
    suspend fun deleteAllPrints()
}