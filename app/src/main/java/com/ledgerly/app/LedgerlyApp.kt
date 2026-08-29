package com.ledgerly.app

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.ledgerly.app.data.db.LedgerDatabase
import com.ledgerly.app.data.repository.LedgerRepository

class AppContainer(context: Context) {
    private val database: LedgerDatabase by lazy {
        Room.databaseBuilder(context, LedgerDatabase::class.java, LedgerDatabase.NAME).build()
    }
    val repository: LedgerRepository by lazy { LedgerRepository(database) }
}

class LedgerlyApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}