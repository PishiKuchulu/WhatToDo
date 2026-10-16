package com.example.pfptodo.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/**
 * Host-JVM migration tests using the real SQLite engine (org.xerial:sqlite-jdbc).
 *
 * The migration objects expose their SQL through `sqlStatements`;
 * these tests execute that exact DDL/DML against SQLite and assert:
 *  - user data survives every migration step
 *  - final schema (tables, indices) matches the exported Room schema expectations
 *  - foreign keys are declared with the correct actions
 */
class PishiDatabaseMigrationTest {

    private fun inMemory(): Connection = DriverManager.getConnection("jdbc:sqlite::memory:")

    @Test
    fun migrate1To3_preservesTasksAndCreatesAllTables() {
        inMemory().use { conn ->
            val st = conn.createStatement()
            // v1 schema: tasks only, pre-category
            st.execute(
                """
                CREATE TABLE `tasks` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `title` TEXT NOT NULL, `description` TEXT, `notes` TEXT,
                    `isCompleted` INTEGER NOT NULL, `priority` TEXT NOT NULL,
                    `parentTaskId` INTEGER, `dueDate` INTEGER, `reminderTime` INTEGER,
                    `orderIndex` INTEGER NOT NULL, `recurrenceRule` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            st.execute("INSERT INTO `tasks` (`title`,`isCompleted`,`priority`,`orderIndex`,`recurrenceRule`,`createdAt`,`updatedAt`) VALUES ('Legacy task', 0, 'HIGH', 0, 'NONE', 1700000000000, 1700000000000)")

            for (sql in PishiDatabase.MIGRATION_1_2_SQL) st.execute(sql)
            for (sql in PishiDatabase.MIGRATION_2_3_SQL) st.execute(sql)

            val rs = st.executeQuery("SELECT title, priority, categoryId FROM tasks")
            assertTrue(rs.next())
            assertEquals("Legacy task", rs.getString("title"))
            assertEquals("HIGH", rs.getString("priority"))
            assertNull(rs.getObject("categoryId"))

            val tables = mutableSetOf<String>()
            st.executeQuery("SELECT name FROM sqlite_master WHERE type='table'").use { t ->
                while (t.next()) tables.add(t.getString("name"))
            }
            assertTrue("tasks" in tables)
            assertTrue("categories" in tables)
            assertTrue("attachments" in tables)
            assertFalse("tasks_old" in tables)

            val indexNames = mutableSetOf<String>()
            st.executeQuery("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='tasks'").use { i ->
                while (i.next()) indexNames.add(i.getString("name"))
            }
            assertTrue("index_tasks_categoryId" in indexNames)
            assertTrue("index_tasks_parentTaskId" in indexNames)
            assertTrue("index_tasks_orderIndex" in indexNames)
            assertTrue("index_tasks_dueDate" in indexNames)
        }
    }

    @Test
    fun migrate2To3_preservesCategoryAndSubtaskRelationships() {
        inMemory().use { conn ->
            val st = conn.createStatement()
            st.execute("CREATE TABLE `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `color` TEXT NOT NULL, `iconName` TEXT NOT NULL)")
            st.execute(
                """
                CREATE TABLE `tasks` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `title` TEXT NOT NULL, `description` TEXT, `notes` TEXT,
                    `isCompleted` INTEGER NOT NULL, `priority` TEXT NOT NULL, `categoryId` INTEGER,
                    `parentTaskId` INTEGER, `dueDate` INTEGER, `reminderTime` INTEGER,
                    `orderIndex` INTEGER NOT NULL, `recurrenceRule` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            st.execute("INSERT INTO categories VALUES (1, 'Work', '#123456', 'work')")
            st.execute("INSERT INTO tasks (id,title,isCompleted,priority,categoryId,parentTaskId,orderIndex,recurrenceRule,createdAt,updatedAt) VALUES (1,'Parent',0,'MEDIUM',1,NULL,0,'NONE',1700000000000,1700000000000)")
            st.execute("INSERT INTO tasks (id,title,isCompleted,priority,categoryId,parentTaskId,orderIndex,recurrenceRule,createdAt,updatedAt) VALUES (2,'Subtask',0,'LOW',1,1,0,'NONE',1700000000000,1700000000000)")

            for (sql in PishiDatabase.MIGRATION_2_3_SQL) st.execute(sql)

            val rs = st.executeQuery("SELECT title, categoryId, parentTaskId FROM tasks WHERE id=2")
            rs.next()
            assertEquals(1L, rs.getLong("categoryId"))
            assertEquals(1L, rs.getLong("parentTaskId"))

            val fk = st.executeQuery("PRAGMA foreign_key_list(attachments)")
            assertTrue("attachments must declare FK to tasks", fk.next())
            assertEquals("tasks", fk.getString("table"))
            assertEquals("CASCADE", fk.getString("on_delete"))
        }
    }

    @Test
    fun stepwiseMigration_v1ToV2ThenV2ToV3_keepsAllData() {
        inMemory().use { conn ->
            val st = conn.createStatement()
            st.execute(
                """
                CREATE TABLE `tasks` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `title` TEXT NOT NULL, `description` TEXT, `notes` TEXT,
                    `isCompleted` INTEGER NOT NULL, `priority` TEXT NOT NULL,
                    `parentTaskId` INTEGER, `dueDate` INTEGER, `reminderTime` INTEGER,
                    `orderIndex` INTEGER NOT NULL, `recurrenceRule` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            st.execute("INSERT INTO `tasks` (`title`,`description`,`isCompleted`,`priority`,`orderIndex`,`recurrenceRule`,`createdAt`,`updatedAt`) VALUES ('Step','d',0,'MEDIUM',1,'DAILY',1700000000000,1700000000000)")

            for (sql in PishiDatabase.MIGRATION_1_2_SQL) st.execute(sql)
            for (sql in PishiDatabase.MIGRATION_2_3_SQL) st.execute(sql)

            val rs = st.executeQuery("SELECT title, recurrenceRule, orderIndex FROM tasks")
            rs.next()
            assertEquals("Step", rs.getString("title"))
            assertEquals("DAILY", rs.getString("recurrenceRule"))
            assertEquals(1, rs.getInt("orderIndex"))
        }
    }

    @Test
    fun migrate3To4_addsRecurrenceAnchorAndBackfillsExistingRecurringTasks() {
        inMemory().use { conn ->
            val st = conn.createStatement()
            // v3 schema (no recurrenceAnchor)
            st.execute(
                """
                CREATE TABLE `tasks` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `title` TEXT NOT NULL, `description` TEXT, `notes` TEXT,
                    `isCompleted` INTEGER NOT NULL, `priority` TEXT NOT NULL, `categoryId` INTEGER,
                    `parentTaskId` INTEGER, `dueDate` INTEGER, `reminderTime` INTEGER,
                    `orderIndex` INTEGER NOT NULL, `recurrenceRule` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            st.execute("INSERT INTO tasks (id,title,isCompleted,priority,dueDate,orderIndex,recurrenceRule,createdAt,updatedAt) VALUES (1,'Recurring',0,'LOW',1750000000000,0,'WEEKLY',1700000000000,1700000000000)")
            st.execute("INSERT INTO tasks (id,title,isCompleted,priority,orderIndex,recurrenceRule,createdAt,updatedAt) VALUES (2,'OneOff',0,'LOW',0,'NONE',1700000000000,1700000000000)")

            for (sql in PishiDatabase.MIGRATION_3_4_SQL) st.execute(sql)

            val recurring = st.executeQuery("SELECT recurrenceAnchor FROM tasks WHERE id=1")
            recurring.next()
            assertEquals(
                "recurring task must backfill anchor from dueDate",
                1750000000000L,
                recurring.getLong("recurrenceAnchor")
            )

            val oneOff = st.executeQuery("SELECT recurrenceAnchor FROM tasks WHERE id=2")
            oneOff.next()
            assertNull(oneOff.getObject("recurrenceAnchor"))
        }
    }

    @Test
    fun categoryDeleteSetsTaskCategoryNull_whenForeignKeysEnabled() {
        inMemory().use { conn ->
            val st = conn.createStatement()
            st.execute("PRAGMA foreign_keys = ON")
            st.execute("CREATE TABLE `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `color` TEXT NOT NULL, `iconName` TEXT NOT NULL)")
            st.execute("CREATE TABLE `tasks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `description` TEXT, `notes` TEXT, `isCompleted` INTEGER NOT NULL, `priority` TEXT NOT NULL, `categoryId` INTEGER, `parentTaskId` INTEGER, `dueDate` INTEGER, `reminderTime` INTEGER, `orderIndex` INTEGER NOT NULL, `recurrenceRule` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
            st.execute("INSERT INTO categories VALUES (1,'C','#FFF','x')")
            st.execute("INSERT INTO tasks (id,title,isCompleted,priority,categoryId,orderIndex,recurrenceRule,createdAt,updatedAt) VALUES (1,'T',0,'LOW',1,0,'NONE',1700000000000,1700000000000)")

            st.execute("DELETE FROM categories WHERE id=1")

            val rs = st.executeQuery("SELECT categoryId FROM tasks WHERE id=1")
            rs.next()
            assertNull(rs.getObject("categoryId"))
        }
    }

    @Test
    fun migrate5To6_createsTagsAndCrossRefTablesWithIndicesAndCascade() {
        inMemory().use { conn ->
            val st = conn.createStatement()
            st.execute("PRAGMA foreign_keys = ON")

            // Setup v5 schema
            st.execute("CREATE TABLE `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `color` TEXT NOT NULL, `iconName` TEXT NOT NULL)")
            st.execute(
                """
                CREATE TABLE `tasks` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `title` TEXT NOT NULL, `description` TEXT, `notes` TEXT,
                    `isCompleted` INTEGER NOT NULL, `priority` TEXT NOT NULL, `categoryId` INTEGER,
                    `parentTaskId` INTEGER, `dueDate` INTEGER, `reminderTime` INTEGER,
                    `orderIndex` INTEGER NOT NULL, `recurrenceRule` TEXT NOT NULL,
                    `recurrenceAnchor` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL,
                    `completedAt` INTEGER,
                    FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                    FOREIGN KEY(`parentTaskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            st.execute(
                """
                CREATE TABLE `attachments` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `taskId` INTEGER NOT NULL, `type` TEXT NOT NULL, `uri` TEXT NOT NULL, `fileName` TEXT NOT NULL,
                    FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )

            st.execute("INSERT INTO categories VALUES (1, 'Personal', '#FF5722', 'person')")
            st.execute("INSERT INTO tasks (id,title,isCompleted,priority,categoryId,orderIndex,recurrenceRule,createdAt,updatedAt) VALUES (10,'Task 1',0,'HIGH',1,0,'NONE',1700000000000,1700000000000)")
            st.execute("INSERT INTO tasks (id,title,isCompleted,priority,categoryId,orderIndex,recurrenceRule,createdAt,updatedAt) VALUES (20,'Task 2',0,'LOW',1,1,'NONE',1700000000000,1700000000000)")

            // Run migration 5 -> 6
            for (sql in PishiDatabase.MIGRATION_5_6_SQL) {
                st.execute(sql)
            }

            // Verify tables created
            val tables = mutableSetOf<String>()
            st.executeQuery("SELECT name FROM sqlite_master WHERE type='table'").use { t ->
                while (t.next()) tables.add(t.getString("name"))
            }
            assertTrue("tags table must exist", "tags" in tables)
            assertTrue("task_tag_cross_ref table must exist", "task_tag_cross_ref" in tables)

            // Verify unique index on tags(name)
            val tagIndices = mutableSetOf<String>()
            st.executeQuery("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='tags'").use { i ->
                while (i.next()) tagIndices.add(i.getString("name"))
            }
            assertTrue("index_tags_name must exist", "index_tags_name" in tagIndices)

            // Test tag insertion & cross-ref insertion
            st.execute("INSERT INTO tags (id, name, color, createdAt) VALUES (1, 'Urgent', '#E57373', 1700000000000)")
            st.execute("INSERT INTO tags (id, name, color, createdAt) VALUES (2, 'Home', '#81C784', 1700000000000)")
            st.execute("INSERT INTO task_tag_cross_ref (taskId, tagId) VALUES (10, 1)")
            st.execute("INSERT INTO task_tag_cross_ref (taskId, tagId) VALUES (10, 2)")
            st.execute("INSERT INTO task_tag_cross_ref (taskId, tagId) VALUES (20, 2)")

            // Test cascade delete on tag delete
            st.execute("DELETE FROM tags WHERE id = 1")
            var countRs = st.executeQuery("SELECT COUNT(*) FROM task_tag_cross_ref WHERE tagId = 1")
            countRs.next()
            assertEquals("Cross-ref referencing deleted tag 1 must be cascade deleted", 0, countRs.getInt(1))

            // Test cascade delete on task delete
            st.execute("DELETE FROM tasks WHERE id = 20")
            countRs = st.executeQuery("SELECT COUNT(*) FROM task_tag_cross_ref WHERE taskId = 20")
            countRs.next()
            assertEquals("Cross-ref referencing deleted task 20 must be cascade deleted", 0, countRs.getInt(1))

            // Verify existing task 10 is intact
            val taskRs = st.executeQuery("SELECT title FROM tasks WHERE id = 10")
            assertTrue(taskRs.next())
            assertEquals("Task 1", taskRs.getString("title"))
        }
    }

    @Test
    fun migrate6To7_createsBirthdaysTableAndPreservesAllData() {
        inMemory().use { conn ->
            val st = conn.createStatement()

            // Full v6 schema (tasks + categories + attachments + tags + task_tag_cross_ref)
            st.execute("CREATE TABLE `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `color` TEXT NOT NULL, `iconName` TEXT NOT NULL)")
            st.execute(
                """
                CREATE TABLE `tasks` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `title` TEXT NOT NULL, `description` TEXT, `notes` TEXT,
                    `isCompleted` INTEGER NOT NULL, `priority` TEXT NOT NULL, `categoryId` INTEGER,
                    `parentTaskId` INTEGER, `dueDate` INTEGER, `reminderTime` INTEGER,
                    `orderIndex` INTEGER NOT NULL, `recurrenceRule` TEXT NOT NULL,
                    `recurrenceAnchor` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL,
                    `completedAt` INTEGER,
                    FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                    FOREIGN KEY(`parentTaskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            st.execute(
                """
                CREATE TABLE `attachments` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `taskId` INTEGER NOT NULL, `type` TEXT NOT NULL, `uri` TEXT NOT NULL, `fileName` TEXT NOT NULL,
                    FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            st.execute("CREATE TABLE `tags` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `color` TEXT, `createdAt` INTEGER NOT NULL)")
            st.execute("CREATE UNIQUE INDEX `index_tags_name` ON `tags` (`name`)")
            st.execute(
                """
                CREATE TABLE `task_tag_cross_ref` (
                    `taskId` INTEGER NOT NULL,
                    `tagId` INTEGER NOT NULL,
                    PRIMARY KEY(`taskId`, `tagId`),
                    FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )

            // Representative v6 data: a task with a tag, an attachment-bearing task, subtask linkage.
            st.execute("INSERT INTO categories VALUES (1, 'Personal', '#FF5722', 'person')")
            st.execute("INSERT INTO tasks (id,title,isCompleted,priority,categoryId,parentTaskId,orderIndex,recurrenceRule,createdAt,updatedAt) VALUES (10,'Task 1',0,'HIGH',1,NULL,0,'NONE',1700000000000,1700000000000)")
            st.execute("INSERT INTO tasks (id,title,isCompleted,priority,categoryId,parentTaskId,orderIndex,recurrenceRule,createdAt,updatedAt) VALUES (20,'Task 2',1,'LOW',1,NULL,1,'NONE',1700000000000,1700000000000)")
            st.execute("INSERT INTO attachments (id,taskId,type,uri,fileName) VALUES (1,10,'IMAGE','content://x/1','pic.png')")
            st.execute("INSERT INTO tags (id, name, color, createdAt) VALUES (1, 'Urgent', '#E57373', 1700000000000)")
            st.execute("INSERT INTO task_tag_cross_ref (taskId, tagId) VALUES (10, 1)")

            // Run migration 6 -> 7
            for (sql in PishiDatabase.MIGRATION_6_7_SQL) st.execute(sql)

            // 1. birthdays table exists with the exact expected columns
            val tables = mutableSetOf<String>()
            st.executeQuery("SELECT name FROM sqlite_master WHERE type='table'").use { t ->
                while (t.next()) tables.add(t.getString("name"))
            }
            assertTrue("birthdays table must exist after 6->7", "birthdays" in tables)

            val columns = mutableMapOf<String, Int>() // name -> notnull flag
            st.executeQuery("PRAGMA table_info(`birthdays`)").use { c ->
                while (c.next()) columns[c.getString("name")] = c.getInt("notnull")
            }
            assertEquals(
                setOf("id", "name", "month", "day", "birthYear", "notes", "reminderMinutesOfDay", "notifyOnDayBefore", "contactId", "createdAt"),
                columns.keys
            )
            // NOT NULL contract: id/name/month/day/reminderMinutesOfDay/notifyOnDayBefore/createdAt
            assertEquals(1, columns["name"])
            assertEquals(1, columns["month"])
            assertEquals(1, columns["day"])
            assertEquals(1, columns["reminderMinutesOfDay"])
            assertEquals(1, columns["notifyOnDayBefore"])
            assertEquals(1, columns["createdAt"])
            // Nullable: birthYear, notes, contactId
            assertEquals(0, columns["birthYear"])
            assertEquals(0, columns["notes"])
            assertEquals(0, columns["contactId"])

            // 2. name index exists
            val indexNames = mutableSetOf<String>()
            st.executeQuery("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='birthdays'").use { i ->
                while (i.next()) indexNames.add(i.getString("name"))
            }
            assertTrue("index_birthdays_name must exist", "index_birthdays_name" in indexNames)

            // 3. Prior v6 data is fully intact
            var rs = st.executeQuery("SELECT title, priority FROM tasks WHERE id=10")
            assertTrue(rs.next())
            assertEquals("Task 1", rs.getString("title"))
            assertEquals("HIGH", rs.getString("priority"))

            rs = st.executeQuery("SELECT COUNT(*) FROM task_tag_cross_ref WHERE taskId=10 AND tagId=1")
            assertTrue(rs.next())
            assertEquals(1, rs.getInt(1))

            rs = st.executeQuery("SELECT fileName FROM attachments WHERE id=1")
            assertTrue(rs.next())
            assertEquals("pic.png", rs.getString("fileName"))

            // 4. Birthday rows (including NULL optional fields) round-trip
            st.execute("INSERT INTO birthdays (name, month, day, birthYear, notes, reminderMinutesOfDay, notifyOnDayBefore, contactId, createdAt) VALUES ('Ali', 5, 10, 1990, NULL, 540, 1, NULL, 1700000000000)")
            rs = st.executeQuery("SELECT id, name, month, day, birthYear, notes, reminderMinutesOfDay, notifyOnDayBefore, contactId FROM birthdays")
            assertTrue(rs.next())
            assertEquals("Ali", rs.getString("name"))
            assertEquals(5, rs.getInt("month"))
            assertEquals(10, rs.getInt("day"))
            assertEquals(1990, rs.getInt("birthYear"))
            assertNull(rs.getObject("notes"))
            assertEquals(540, rs.getInt("reminderMinutesOfDay"))
            assertEquals(1, rs.getInt("notifyOnDayBefore"))
            assertNull(rs.getObject("contactId"))
        }
    }

    @Test
    fun stepwiseMigration_v1ToV7_keepsAllData() {
        inMemory().use { conn ->
            val st = conn.createStatement()

            // v1 schema
            st.execute(
                """
                CREATE TABLE `tasks` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `title` TEXT NOT NULL, `description` TEXT, `notes` TEXT,
                    `isCompleted` INTEGER NOT NULL, `priority` TEXT NOT NULL,
                    `parentTaskId` INTEGER, `dueDate` INTEGER, `reminderTime` INTEGER,
                    `orderIndex` INTEGER NOT NULL, `recurrenceRule` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            st.execute("INSERT INTO `tasks` (`id`,`title`,`isCompleted`,`priority`,`orderIndex`,`recurrenceRule`,`createdAt`,`updatedAt`) VALUES (1, 'V1 Task', 0, 'HIGH', 0, 'NONE', 1700000000000, 1700000000000)")

            // Stepwise 1 -> 2 -> 3 -> 4 -> 5 -> 6 -> 7
            for (sql in PishiDatabase.MIGRATION_1_2_SQL) st.execute(sql)
            for (sql in PishiDatabase.MIGRATION_2_3_SQL) st.execute(sql)
            for (sql in PishiDatabase.MIGRATION_3_4_SQL) st.execute(sql)
            for (sql in PishiDatabase.MIGRATION_4_5_SQL) st.execute(sql)
            for (sql in PishiDatabase.MIGRATION_5_6_SQL) st.execute(sql)
            for (sql in PishiDatabase.MIGRATION_6_7_SQL) st.execute(sql)

            st.execute("PRAGMA foreign_keys = ON")

            val rs = st.executeQuery("SELECT title, priority, isCompleted FROM tasks WHERE id=1")
            assertTrue(rs.next())
            assertEquals("V1 Task", rs.getString("title"))
            assertEquals("HIGH", rs.getString("priority"))

            val tables = mutableSetOf<String>()
            st.executeQuery("SELECT name FROM sqlite_master WHERE type='table'").use { t ->
                while (t.next()) tables.add(t.getString("name"))
            }
            assertTrue("tasks" in tables)
            assertTrue("categories" in tables)
            assertTrue("attachments" in tables)
            assertTrue("tags" in tables)
            assertTrue("task_tag_cross_ref" in tables)
            assertTrue("birthdays" in tables)
        }
    }
}

