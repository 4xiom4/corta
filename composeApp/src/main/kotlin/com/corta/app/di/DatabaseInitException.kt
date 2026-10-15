package com.corta.app.di

/**
 * Sealed hierarchy of failures that can occur when bootstrapping the local SQLCipher database.
 *
 * These exceptions are intentionally NOT caught and silently retried: data loss on a working
 * production device is worse than a clean crash with full diagnostic context. The caller
 * (typically [com.corta.app.CortaApplication]) is responsible for deciding whether to surface
 * a recovery UI or escalate to crash reporting.
 */
sealed class DatabaseInitException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {

    /**
     * SQLCipher native library failed to load, or the database driver could not be constructed
     * for a reason unrelated to encryption (e.g. read-only filesystem).
     */
    class DriverUnavailable(cause: Throwable) : DatabaseInitException(
        message = "SQLCipher driver could not be created; the device may be unsupported.",
        cause = cause,
    )

    /**
     * The encryption passphrase could not be retrieved from the Android KeyStore. The DB is
     * intact but unreadable until the user clears app data or restores a backup.
     */
    class PassphraseUnavailable(cause: Throwable) : DatabaseInitException(
        message = "Database passphrase is unavailable. KeyStore may have been wiped.",
        cause = cause,
    )

    /**
     * The existing database file is unreadable with the current passphrase. This usually means
     * the encrypted file is corrupted (disk failure, force-stop during write, downgrade). It is
     * the ONLY case where deleting the file and recreating is acceptable — and only as a last
     * resort behind a user-facing confirmation, never automatically.
     */
    class DatabaseCorrupted(cause: Throwable) : DatabaseInitException(
        message = "Database file is corrupted and cannot be opened.",
        cause = cause,
    )
}
