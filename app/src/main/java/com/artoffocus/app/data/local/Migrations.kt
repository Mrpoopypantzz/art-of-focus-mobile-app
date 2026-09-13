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

/**
 * Adds `remoteUrl` so a community painting can be rendered on another device.
 *
 * Additive and nullable, like every migration before it. A painting made before this version
 * keeps its local image and simply has no URL — it still renders perfectly on the device that
 * made it, which is the only device that has ever had it.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE paintings ADD COLUMN remoteUrl TEXT")
    }
}

/**
 * Adds the unique index on `paintingKey` that should have existed from version 1.
 *
 * Without it, `OnConflictStrategy.IGNORE` had no constraint to conflict against, so
 * `recordCompletion` was not idempotent despite saying it was, and the sync pull could not rely
 * on "insert only if absent". Both now hold.
 *
 * Existing duplicates are collapsed first, keeping the EARLIEST row of each set (lowest
 * localId), or creating the index would fail on any museum that already has one. Deleting a
 * duplicate is not an I12 violation: the painting itself survives, and I12 protects a completed
 * painting, not a redundant second copy of the same one. Nothing unique to the user is lost —
 * the rows being removed are byte-identical in identity (startedAt, seed).
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            DELETE FROM paintings
            WHERE localId NOT IN (SELECT MIN(localId) FROM paintings GROUP BY paintingKey)
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_paintings_paintingKey ON paintings (paintingKey)")
    }
}
