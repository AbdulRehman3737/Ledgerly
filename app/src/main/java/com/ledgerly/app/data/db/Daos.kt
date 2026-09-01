package com.ledgerly.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.ledgerly.app.domain.model.TxType
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE id = :id")
    fun observeById(id: Long): Flow<ProfileEntity?>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getById(id: Long): ProfileEntity?

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(profile: ProfileEntity): Long

    @Update
    suspend fun update(profile: ProfileEntity)

    @Delete
    suspend fun delete(profile: ProfileEntity)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE profileId = :profileId ORDER BY isSystem DESC, name COLLATE NOCASE ASC")
    fun observeAll(profileId: Long): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE profileId = :profileId AND archived = 0 ORDER BY name COLLATE NOCASE ASC")
    fun observeActive(profileId: Long): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE profileId = :profileId AND archived = 0 ORDER BY name COLLATE NOCASE ASC")
    suspend fun getActive(profileId: Long): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE profileId = :profileId AND type = :type AND archived = 0")
    suspend fun getByType(profileId: Long, type: TxType): List<CategoryEntity>

    @Query("SELECT COUNT(*) FROM categories WHERE profileId = :profileId AND name = :name AND type = :type AND archived = 0")
    suspend fun countByName(profileId: Long, name: String, type: TxType): Int

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("UPDATE categories SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)
}

@Dao
interface TransactionDao {
    @Transaction
    @Query("SELECT * FROM transactions WHERE profileId = :profileId ORDER BY dateEpochDay DESC, createdAt DESC")
    fun observeAll(profileId: Long): Flow<List<TransactionWithCategory>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query(
        """
        SELECT COUNT(*) FROM transactions WHERE profileId = :profileId
            AND type = :type AND dateEpochDay BETWEEN :start AND :endInclusive
        """
    )
    fun countInRange(profileId: Long, type: TxType, start: Long, endInclusive: Long): Flow<Long>

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE profileId = :profileId")
    fun observeAll(profileId: Long): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE profileId = :profileId AND categoryId = :categoryId")
    suspend fun getForCategory(profileId: Long, categoryId: Long): BudgetEntity?

    @Query("SELECT * FROM budgets WHERE profileId = :profileId AND categoryId IS NULL")
    suspend fun getOverall(profileId: Long): BudgetEntity?

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun getById(id: Long): BudgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(budget: BudgetEntity): Long

    @Update
    suspend fun update(budget: BudgetEntity)

    @Delete
    suspend fun delete(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE profileId = :profileId AND categoryId = :categoryId")
    suspend fun deleteForCategory(profileId: Long, categoryId: Long)

    @Query("DELETE FROM budgets WHERE profileId = :profileId AND categoryId IS NULL")
    suspend fun deleteOverall(profileId: Long)
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings WHERE `key` = :key")
    fun observe(key: String): Flow<AppSettingEntity?>

    @Query("SELECT * FROM app_settings WHERE `key` = :key")
    suspend fun get(key: String): AppSettingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: AppSettingEntity)
}