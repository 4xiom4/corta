package com.corta.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.corta.db.CortaDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

actual class DatabaseDriverFactory(private val context: Context) {
    init {
        try {
            System.loadLibrary("sqlcipher")
        } catch (e: UnsatisfiedLinkError) {
            // Log or handle error if necessary
        }
    }

    actual fun createDriver(passphrase: ByteArray?): SqlDriver {
        val factory = SupportOpenHelperFactory(passphrase)
        return AndroidSqliteDriver(
            schema = CortaDatabase.Schema,
            context = context,
            name = "corta.db",
            factory = factory,
            callback = object : AndroidSqliteDriver.Callback(CortaDatabase.Schema) {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    db.execSQL("PRAGMA foreign_keys=ON;")
                }
            }
        )
    }
}
