package com.corta.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.corta.db.CortaDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

actual class DatabaseDriverFactory(private val context: Context) {
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
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS SpamKeyword (
                            id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                            keyword TEXT NOT NULL UNIQUE,
                            category TEXT NOT NULL DEFAULT 'GENERAL',
                            isActive INTEGER DEFAULT 1,
                            createdAt INTEGER NOT NULL
                        );
                    """.trimIndent())
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS ArchivedConversation (
                            threadId INTEGER NOT NULL PRIMARY KEY,
                            address TEXT NOT NULL,
                            archivedAt INTEGER NOT NULL
                        );
                    """.trimIndent())
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS TrashItem (
                            id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                            itemType TEXT NOT NULL,
                            originalId TEXT NOT NULL,
                            senderOrNumber TEXT NOT NULL,
                            contentPreview TEXT,
                            deletedAt INTEGER NOT NULL
                        );
                    """.trimIndent())
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS ContactGroup (
                            id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                            name TEXT NOT NULL UNIQUE,
                            iconName TEXT NOT NULL DEFAULT 'Group',
                            colorHex TEXT NOT NULL DEFAULT '#1976D2'
                        );
                    """.trimIndent())
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS ContactGroupMember (
                            contactId TEXT NOT NULL,
                            groupId INTEGER NOT NULL,
                            PRIMARY KEY (contactId, groupId)
                        );
                    """.trimIndent())
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_spam_keyword_active ON SpamKeyword(isActive);")
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_trash_deleted_at ON TrashItem(deletedAt DESC);")
                }
            }
        )
    }
}
