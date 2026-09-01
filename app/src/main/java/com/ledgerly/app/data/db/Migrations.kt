package com.ledgerly.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> v2: makes budgets.categoryId nullable so an uncategorized "Overall"
 * monthly budget can be stored (categoryId = NULL means the budget spans all
 * spending). SQLite cannot alter a column, so the budgets table is rebuilt.
 * Existing rows are copied over verbatim (they keep their real categoryId).
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE budgets RENAME TO budgets_old")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS budgets (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                profileId INTEGER NOT NULL,
                categoryId INTEGER,
                amountMinor INTEGER NOT NULL,
                period TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(profileId) REFERENCES profiles(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(categoryId) REFERENCES categories(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO budgets (id, profileId, categoryId, amountMinor, period, createdAt)
            SELECT id, profileId, categoryId, amountMinor, period, createdAt FROM budgets_old
            """.trimIndent()
        )
        db.execSQL("DROP TABLE budgets_old")
        db.execSQL("CREATE INDEX index_budgets_profileId ON budgets(profileId)")
        db.execSQL("CREATE UNIQUE INDEX index_budgets_categoryId ON budgets(categoryId)")
    }
}