package app.forma.android.data

import androidx.room.migration.Migration

/**
 * Room schema migrations. Schema version 1 is the first release, so there are none yet.
 *
 * Rules for future versions (see docs/DATA.md):
 *  - Bump FormaDatabase.version, keep the exported JSON in app/schemas under version control and
 *    add an explicit Migration here (or an AutoMigration with a spec) for every step.
 *  - Never use fallbackToDestructiveMigration: workout history must survive app updates.
 *  - Add a MigrationTestHelper test for each step (app/src/androidTest).
 */
val ALL_MIGRATIONS: Array<Migration> = emptyArray()
