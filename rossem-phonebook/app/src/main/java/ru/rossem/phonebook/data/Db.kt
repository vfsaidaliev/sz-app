package ru.rossem.phonebook.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "employees")
data class EmployeeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val role: String,
    val department: String,
    val organization: String,
    val office: String,
    val ext: String,
    val mobile: String,
    val email: String,
    val birthday: String,
    val isLead: Boolean,
    val managerId: String,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(@PrimaryKey val id: String)

@Dao
interface EmployeeDao {
    @Query("SELECT * FROM employees")
    suspend fun all(): List<EmployeeEntity>

    @Query("DELETE FROM employees")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<EmployeeEntity>)

    @Transaction
    suspend fun replaceAll(items: List<EmployeeEntity>) {
        clear()
        insertAll(items)
    }

    @Query("SELECT id FROM favorites")
    fun favorites(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(f: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE id = :id")
    suspend fun removeFavorite(id: String)
}

@Database(entities = [EmployeeEntity::class, FavoriteEntity::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): EmployeeDao

    companion object {
        @Volatile private var instance: AppDb? = null
        fun get(context: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, "phonebook.db")
                .fallbackToDestructiveMigration(true)
                .build().also { instance = it }
        }
    }
}
