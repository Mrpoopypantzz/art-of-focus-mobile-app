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
