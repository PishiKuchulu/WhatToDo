package com.example.pfptodo.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
import com.example.pfptodo.data.local.dao.AttachmentDao
import com.example.pfptodo.data.local.dao.BirthdayDao
import com.example.pfptodo.data.local.dao.CategoryDao
import com.example.pfptodo.data.local.dao.TagDao
import com.example.pfptodo.data.local.dao.TaskDao
import com.example.pfptodo.data.local.entity.AttachmentEntity
import com.example.pfptodo.data.local.entity.BirthdayEntity
import com.example.pfptodo.data.local.entity.CategoryEntity
import com.example.pfptodo.data.local.entity.TagEntity
import com.example.pfptodo.data.local.entity.TaskEntity
import com.example.pfptodo.data.local.entity.TaskTagCrossRef

@Database(
    entities = [TaskEntity::class, CategoryEntity::class, AttachmentEntity::class, TagEntity::class, TaskTagCrossRef::class, BirthdayEntity::class],
    version = 7,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class PishiDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun categoryDao(): CategoryDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun tagDao(): TagDao
    abstract fun birthdayDao(): BirthdayDao

    companion object {
        @Volatile
        private var INSTANCE: PishiDatabase? = null

        /**
         * v1 (original release):
         *   tasks(id PK autoincrement, title TEXT NOT NULL, description TEXT, notes TEXT,
         *         isCompleted INTEGER NOT NULL, priority TEXT NOT NULL, parentTaskId INTEGER,
         *         dueDate INTEGER, reminderTime INTEGER, orderIndex INTEGER NOT NULL,
         *         recurrenceRule TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)
         *
         * v2 added categories (id PK, name, color, iconName) and tasks.categoryId (nullable, FK SET_NULL).
         * v3 added attachments (id PK, taskId FK CASCADE, type, uri, fileName).
         *
         * Both migrations rebuild the `tasks` table to guarantee the final schema is byte-for-byte
         * identical to what Room expects from the current entities (columns, FKs, indices).
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_2_SQL.forEach { db.execSQL(it) }
            }
        }

        /** Exposed for host-JVM migration tests. */
        val MIGRATION_1_2_SQL: List<String> = listOf(
            // Categories table (prepopulated by the app callback on fresh installs only).
            """
            CREATE TABLE IF NOT EXISTS `categories` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `color` TEXT NOT NULL,
                `iconName` TEXT NOT NULL
            )
            """.trimIndent(),
            // Recreate tasks with the v2 schema (adds categoryId + FK + index).
            "ALTER TABLE `tasks` RENAME TO `tasks_old`",
            """
            CREATE TABLE IF NOT EXISTS `tasks` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `title` TEXT NOT NULL,
                `description` TEXT,
                `notes` TEXT,
                `isCompleted` INTEGER NOT NULL,
                `priority` TEXT NOT NULL,
                `categoryId` INTEGER,
                `parentTaskId` INTEGER,
                `dueDate` INTEGER,
                `reminderTime` INTEGER,
                `orderIndex` INTEGER NOT NULL,
                `recurrenceRule` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                FOREIGN KEY(`parentTaskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
            """
            INSERT INTO `tasks` (`id`, `title`, `description`, `notes`, `isCompleted`, `priority`, `categoryId`, `parentTaskId`, `dueDate`, `reminderTime`, `orderIndex`, `recurrenceRule`, `createdAt`, `updatedAt`)
            SELECT `id`, `title`, `description`, `notes`, `isCompleted`, `priority`, NULL, `parentTaskId`, `dueDate`, `reminderTime`, `orderIndex`, `recurrenceRule`, `createdAt`, `updatedAt`
            FROM `tasks_old`
            """.trimIndent(),
            "DROP TABLE `tasks_old`",
            "CREATE INDEX IF NOT EXISTS `index_tasks_categoryId` ON `tasks` (`categoryId`)",
            "CREATE INDEX IF NOT EXISTS `index_tasks_parentTaskId` ON `tasks` (`parentTaskId`)",
            "CREATE INDEX IF NOT EXISTS `index_tasks_orderIndex` ON `tasks` (`orderIndex`)",
            "CREATE INDEX IF NOT EXISTS `index_tasks_title` ON `tasks` (`title`)",
            "CREATE INDEX IF NOT EXISTS `index_tasks_dueDate` ON `tasks` (`dueDate`)"
        )

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_2_3_SQL.forEach { db.execSQL(it) }
            }
        }

        /** Exposed for host-JVM migration tests. */
        val MIGRATION_2_3_SQL: List<String> = listOf(
            // Recreate tasks with the v3 schema (unchanged from v2, rebuilt for validation safety).
            "ALTER TABLE `tasks` RENAME TO `tasks_old`",
            """
            CREATE TABLE IF NOT EXISTS `tasks` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `title` TEXT NOT NULL,
                `description` TEXT,
                `notes` TEXT,
                `isCompleted` INTEGER NOT NULL,
                `priority` TEXT NOT NULL,
                `categoryId` INTEGER,
                `parentTaskId` INTEGER,
                `dueDate` INTEGER,
                `reminderTime` INTEGER,
                `orderIndex` INTEGER NOT NULL,
                `recurrenceRule` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                FOREIGN KEY(`parentTaskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
            """
            INSERT INTO `tasks` (`id`, `title`, `description`, `notes`, `isCompleted`, `priority`, `categoryId`, `parentTaskId`, `dueDate`, `reminderTime`, `orderIndex`, `recurrenceRule`, `createdAt`, `updatedAt`)
            SELECT `id`, `title`, `description`, `notes`, `isCompleted`, `priority`, `categoryId`, `parentTaskId`, `dueDate`, `reminderTime`, `orderIndex`, `recurrenceRule`, `createdAt`, `updatedAt`
            FROM `tasks_old`
            """.trimIndent(),
            "DROP TABLE `tasks_old`",
            // Recreate task indices (they were renamed away with tasks_old).
            "CREATE INDEX IF NOT EXISTS `index_tasks_categoryId` ON `tasks` (`categoryId`)",
            "CREATE INDEX IF NOT EXISTS `index_tasks_parentTaskId` ON `tasks` (`parentTaskId`)",
            "CREATE INDEX IF NOT EXISTS `index_tasks_orderIndex` ON `tasks` (`orderIndex`)",
            "CREATE INDEX IF NOT EXISTS `index_tasks_title` ON `tasks` (`title`)",
            "CREATE INDEX IF NOT EXISTS `index_tasks_dueDate` ON `tasks` (`dueDate`)",
            // Attachments table with FK cascade + index.
            """
            CREATE TABLE IF NOT EXISTS `attachments` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `taskId` INTEGER NOT NULL,
                `type` TEXT NOT NULL,
                `uri` TEXT NOT NULL,
                `fileName` TEXT NOT NULL,
                FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
            "CREATE INDEX IF NOT EXISTS `index_attachments_taskId` ON `attachments` (`taskId`)"
        )

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_3_4_SQL.forEach { db.execSQL(it) }
            }
        }

        /** Exposed for host-JVM migration tests. */
        val MIGRATION_3_4_SQL: List<String> = listOf(
            // v4 adds tasks.recurrenceAnchor (nullable, no default).
            "ALTER TABLE `tasks` ADD COLUMN `recurrenceAnchor` INTEGER",
            // Backfill: existing recurring tasks anchor to their current due date.
            "UPDATE `tasks` SET `recurrenceAnchor` = `dueDate` WHERE `recurrenceRule` IS NOT NULL AND `recurrenceRule` != 'NONE' AND `dueDate` IS NOT NULL"
        )

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_4_5_SQL.forEach { db.execSQL(it) }
            }
        }

        /** Exposed for host-JVM migration tests. */
        val MIGRATION_4_5_SQL: List<String> = listOf(
            // v5 adds tasks.completedAt (nullable, no default).
            "ALTER TABLE `tasks` ADD COLUMN `completedAt` INTEGER",
            // Backfill: completed tasks record their last update as completion time.
            "UPDATE `tasks` SET `completedAt` = `updatedAt` WHERE `isCompleted` = 1 AND `completedAt` IS NULL"
        )

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_5_6_SQL.forEach { db.execSQL(it) }
            }
        }

        /** Exposed for host-JVM migration tests. */
        val MIGRATION_5_6_SQL: List<String> = listOf(
            """
            CREATE TABLE IF NOT EXISTS `tags` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `color` TEXT,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent(),
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags` (`name`)",
            """
            CREATE TABLE IF NOT EXISTS `task_tag_cross_ref` (
                `taskId` INTEGER NOT NULL,
                `tagId` INTEGER NOT NULL,
                PRIMARY KEY(`taskId`, `tagId`),
                FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
            "CREATE INDEX IF NOT EXISTS `index_task_tag_cross_ref_taskId` ON `task_tag_cross_ref` (`taskId`)",
            "CREATE INDEX IF NOT EXISTS `index_task_tag_cross_ref_tagId` ON `task_tag_cross_ref` (`tagId`)"
        )

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_6_7_SQL.forEach { db.execSQL(it) }
            }
        }

        /** Exposed for host-JVM migration tests. */
        val MIGRATION_6_7_SQL: List<String> = listOf(
            // v7 adds the birthdays table for annual birthday/anniversary reminders.
            """
            CREATE TABLE IF NOT EXISTS `birthdays` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `month` INTEGER NOT NULL,
                `day` INTEGER NOT NULL,
                `birthYear` INTEGER,
                `notes` TEXT,
                `reminderMinutesOfDay` INTEGER NOT NULL,
                `notifyOnDayBefore` INTEGER NOT NULL,
                `contactId` TEXT,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent(),
            "CREATE INDEX IF NOT EXISTS `index_birthdays_name` ON `birthdays` (`name`)"
        )

        fun getDatabase(context: Context): PishiDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PishiDatabase::class.java,
                    "pishi_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Prepopulate default categories inside the same transaction that
                            // created the schema (Room callback runs during onCreate).
                            seedDefaultCategories(db, context)
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private fun seedDefaultCategories(db: SupportSQLiteDatabase, context: Context) {
            db.beginTransaction()
            try {
                val defaultCategories = listOf(
                    Triple(
                        context.getString(com.example.pfptodo.R.string.category_personal),
                        "#FF6200EE",
                        "person"
                    ),
                    Triple(
                        context.getString(com.example.pfptodo.R.string.category_work),
                        "#FF03DAC5",
                        "work"
                    ),
                    Triple(
                        context.getString(com.example.pfptodo.R.string.category_shopping),
                        "#FFFFAB00",
                        "shopping_cart"
                    ),
                    Triple(
                        context.getString(com.example.pfptodo.R.string.category_study),
                        "#FF64DD17",
                        "school"
                    )
                )
                defaultCategories.forEach { (name, color, icon) ->
                    db.execSQL(
                        "INSERT INTO `categories` (`name`, `color`, `iconName`) VALUES (?, ?, ?)",
                        arrayOf(name, color, icon)
                    )
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }
}

typealias WToDoDatabase = PishiDatabase

