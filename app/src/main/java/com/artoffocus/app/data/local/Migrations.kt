package com.artoffocus.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * I12 — a completed painting is never destroyed, and that includes surviving an app update.
 *
 * This is the first migration in this database, written by hand: version 1 shipped with
 * `exportSchema = false` and no schema JSON, so Room's `@AutoMigration` (which verifies
 * against exported schemas) is unavailable for this step. `fallbackToDestructiveMigration()`
 * is not an option at any point in this file's future — it drops the table, which is exactly
 * the outcome I12 forbids.
 *
 * Strictly additive: one nullable column. Every existing row keeps every value it had, and
 * NULL in the new column means "this painting is a generated gradient", which is what every
 * pre-existing painting is. SQLite's ALTER TABLE ADD COLUMN requires the new column to be
 * nullable or carry a default; nullable is the honest choice here rather than a sentinel.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE paintings ADD COLUMN photoUri TEXT")
    }
}

/**
 * Adds `source` and `credit` so community art can be told apart from the user's own photos.
 *
 * Also strictly additive, and it back-fills honestly rather than leaving a null the rest of
 * the app has to guess about. Before this version, "has an image" could only mean "the user's
 * own photo" — that was the only way an image could get there — so the back-fill is a
 * statement of fact about existing rows, not an assumption:
 *
 *   photoUri IS NOT NULL -> OWN_PHOTO, and the Pictures library keeps protecting it (I12)
 *   photoUri IS NULL     -> GENERATED, a seeded gradient with nothing to protect
 *
 * `credit` stays NULL for every existing row: neither a gradient nor a user's photograph has
 * an artist to credit.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE paintings ADD COLUMN source TEXT NOT NULL DEFAULT 'GENERATED'")
        db.execSQL("ALTER TABLE paintings ADD COLUMN credit TEXT")
        db.execSQL("UPDATE paintings SET source = 'OWN_PHOTO' WHERE photoUri IS NOT NULL")
    }
}
