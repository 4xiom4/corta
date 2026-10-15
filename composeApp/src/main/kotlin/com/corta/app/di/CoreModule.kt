package com.corta.app.di

import com.corta.data.DatabaseDriverFactory
import com.corta.data.SecurityProvider
import com.corta.db.CortaDatabase
import org.koin.dsl.module

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import timber.log.Timber

/**
 * Low-level infrastructure: SQL driver, DB schema, security helpers.
 * Independent of business logic; no domain types leak here.
 */
val coreModule = module {
    single<SqlDriver> {
        val context = get<Context>()
        try {
            val passphrase = SecurityProvider.getDatabasePassphrase(context)
            DatabaseDriverFactory(context).createDriver(passphrase)
        } catch (e: Throwable) {
            // We deliberately do NOT call context.deleteDatabase() here: silent data loss
            // is worse than a crash. The caller (CortaApplication) is responsible for
            // surfacing this to the user with a recovery flow.
            Timber.tag(TAG).e(e, "Failed to create SqlDriver")
            throw DatabaseInitException.DriverUnavailable(e)
        }
    }
    single {
        CortaDatabase(get())
    }
}

private const val TAG = "Corta.CoreModule"
