package com.artoffocus.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * I12 — a completed painting is never destroyed, and that includes surviving an app update.
 *
 * The migrations are hand-written (version 1 shipped with `exportSchema = false`, so Room's
 * automatic verification was never available) and until now had NO tests: three migrations
 * guarding the one thing this project declares inviolable. A migration that silently dropped
 * the museum would have been found by a user rather than by a test.
 *
 * Deliberately NOT using `MigrationTestHelper`. That helper loads exported schema JSON from
 * assets and fails outright without it — `1.json` does not exist and cannot be produced
 * retroactively, since v1 shipped without export. So these drive the real `Migration` objects
 * against a real SQLite database instead. Same migration code, same SQL, no fixture files, and
 * each starting schema is written out explicitly to match the shape the database genuinely had
 * on a device (v1 and v2 confirmed against a real install).
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val dbName = "migration_test.db"
    private lateinit var context: Context
    private var helper: SupportSQLiteOpenHelper? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        helper?.close()
        context.deleteDatabase(dbName)
    }

    /**
     * Opens the test database at a bare SQLite level, with no Room identity check — Room would
     * refuse a hand-built v1 table for lacking its metadata, which is the whole thing being
     * simulated here.
     */
    private fun open(): SupportSQLiteDatabase {
        helper?.close()
        val created = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) = Unit
                })
                .build(),
        )
        helper = created
        return created.writableDatabase
    }

    /** The v1 table, as Room created it before photoUri existed. */
    private fun SupportSQLiteDatabase.createV1() {
        execSQL(
            """
            CREATE TABLE IF NOT EXISTS `paintings` (
                `localId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `paintingKey` TEXT NOT NULL,
                `seed` INTEGER NOT NULL,
                `title` TEXT NOT NULL,
                `totalMinutes` INTEGER NOT NULL,
                `sittingMinutes` INTEGER NOT NULL,
                `pieces` INTEGER NOT NULL,
                `startedAt` INTEGER NOT NULL,
                `completedAt` INTEGER NOT NULL,
                `syncedAt` INTEGER
            )
            """.trimIndent(),
        )
    }

    private fun SupportSQLiteDatabase.insertPainting(
        key: String,
        title: String,
        photoUri: String? = null,
        withPhotoColumn: Boolean = false,
    ) {
        val columns = StringBuilder(
            "paintingKey, seed, title, totalMinutes, sittingMinutes, pieces, startedAt, completedAt",
        )
        val values = StringBuilder("'$key', 1, '$title', 120, 30, 4, 1000, 2000")
        if (withPhotoColumn) {
            columns.append(", photoUri")
            values.append(if (photoUri == null) ", NULL" else ", '$photoUri'")
        }
        execSQL("INSERT INTO paintings ($columns) VALUES ($values)")
    }

    private fun SupportSQLiteDatabase.titles(): List<String> {
        val found = mutableListOf<String>()
        query("SELECT title FROM paintings ORDER BY localId").use { cursor ->
            while (cursor.moveToNext()) found.add(cursor.getString(0))
        }
        return found
    }

    private fun SupportSQLiteDatabase.value(key: String, column: String): String? {
        query("SELECT $column FROM paintings WHERE paintingKey = ?", arrayOf(key)).use { cursor ->
            assertTrue("row $key is missing", cursor.moveToFirst())
            return if (cursor.isNull(0)) null else cursor.getString(0)
        }
    }

    @Test
    fun migrating_1_to_2_keeps_every_painting() {
        val db = open()
        db.createV1()
        db.insertPainting("1000:1", "Cathedral Light")
        db.insertPainting("3000:2", "Study in Ochre")

        MIGRATION_1_2.migrate(db)

        assertEquals(listOf("Cathedral Light", "Study in Ochre"), db.titles())
        // The new column exists and is null, which is what "this is a gradient" means.
        assertNull(db.value("1000:1", "photoUri"))
    }

    @Test
    fun migrating_2_to_3_backfills_source_honestly() {
        val db = open()
        db.createV1()
        MIGRATION_1_2.migrate(db)

        // Before v3, an image could only ever be the user's own photo — community art did not
        // exist yet. The back-fill must state exactly that, and nothing more.
        db.insertPainting("1000:1", "My Own Picture", photoUri = "/photos/a.jpg", withPhotoColumn = true)
        db.insertPainting("3000:2", "Study in Ochre", photoUri = null, withPhotoColumn = true)

        MIGRATION_2_3.migrate(db)

        assertEquals(listOf("My Own Picture", "Study in Ochre"), db.titles())
        assertEquals("OWN_PHOTO", db.value("1000:1", "source"))
        assertEquals("GENERATED", db.value("3000:2", "source"))
        // Neither has an artist to credit.
        assertNull(db.value("1000:1", "credit"))
        assertNull(db.value("3000:2", "credit"))
    }

    @Test
    fun migrating_3_to_4_keeps_photo_paintings_intact() {
        val db = open()
        db.createV1()
        MIGRATION_1_2.migrate(db)
        db.insertPainting("1000:1", "My Own Picture", photoUri = "/photos/a.jpg", withPhotoColumn = true)
        MIGRATION_2_3.migrate(db)

        MIGRATION_3_4.migrate(db)

        // The picture reference and its classification both survive; only remoteUrl is added,
        // and a photo painting rightly has none — private photos are never uploaded.
        assertEquals("/photos/a.jpg", db.value("1000:1", "photoUri"))
        assertEquals("OWN_PHOTO", db.value("1000:1", "source"))
        assertNull(db.value("1000:1", "remoteUrl"))
    }

    @Test
    fun migrating_all_the_way_from_1_to_5_loses_nothing() {
        // The path a user on the very first release actually takes.
        val db = open()
        db.createV1()
        db.insertPainting("1000:1", "Cathedral Light")
        db.insertPainting("3000:2", "Dusk Over the Harbour")

        MIGRATION_1_2.migrate(db)
        MIGRATION_2_3.migrate(db)
        MIGRATION_3_4.migrate(db)
        MIGRATION_4_5.migrate(db)

        assertEquals(listOf("Cathedral Light", "Dusk Over the Harbour"), db.titles())
        assertEquals("GENERATED", db.value("1000:1", "source"))
        assertNull(db.value("1000:1", "remoteUrl"))
    }


    @Test
    fun migrating_4_to_5_collapses_duplicates_and_keeps_the_original() {
        // A museum that already picked up a duplicate (possible before v5, because
        // OnConflictStrategy.IGNORE had no unique index to conflict against) must be able to
        // reach v5 at all: creating the index would otherwise fail outright.
        val db = open()
        db.createV1()
        MIGRATION_1_2.migrate(db)
        MIGRATION_2_3.migrate(db)
        MIGRATION_3_4.migrate(db)

        db.insertPainting("1000:1", "Cathedral Light")
        db.insertPainting("1000:1", "Cathedral Light")
        db.insertPainting("3000:2", "Study in Ochre")
        assertEquals(3, db.titles().size)

        MIGRATION_4_5.migrate(db)

        // One of each identity survives, and no distinct painting is lost.
        assertEquals(listOf("Cathedral Light", "Study in Ochre"), db.titles())
    }

    @Test
    fun after_5_the_same_painting_cannot_be_stored_twice() {
        val db = open()
        db.createV1()
        MIGRATION_1_2.migrate(db)
        MIGRATION_2_3.migrate(db)
        MIGRATION_3_4.migrate(db)
        MIGRATION_4_5.migrate(db)

        db.insertPainting("1000:1", "Cathedral Light")
        // The index is what makes the repository's "idempotent by paintingKey" claim true.
        var rejected = false
        try {
            db.insertPainting("1000:1", "Cathedral Light")
        } catch (expected: android.database.sqlite.SQLiteConstraintException) {
            rejected = true
        }
        assertTrue("a duplicate paintingKey must be rejected", rejected)
        assertEquals(1, db.titles().size)
    }

    @Test
    fun a_migrated_database_is_accepted_by_room_and_reads_back() {
        // Migrations passing in isolation is not the same as Room accepting the result. This
        // sets user_version so Room sees a v1 file, then opens the real AppDatabase over it —
        // which is exactly what the app does on launch after an update — and reads the museum
        // back through the generated DAO.
        val raw = open()
        raw.createV1()
        raw.insertPainting("1000:1", "Cathedral Light")
        raw.version = 1
        helper?.close()
        helper = null

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build()
        try {
            val museum = runBlocking { db.paintingDao().getAll() }
            assertEquals(1, museum.size)
            assertEquals("Cathedral Light", museum.first().title)
            // Migrated rows land on the honest defaults, not on nulls the app has to guess at.
            assertEquals("GENERATED", museum.first().source)
            assertNull(museum.first().remoteUrl)
        } finally {
            db.close()
        }
    }
}
