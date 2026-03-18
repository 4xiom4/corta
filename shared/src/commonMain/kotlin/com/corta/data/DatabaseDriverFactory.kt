package com.corta.data

import app.cash.sqldelight.db.SqlDriver

expect class DatabaseDriverFactory {
    fun createDriver(passphrase: ByteArray?): SqlDriver
}
