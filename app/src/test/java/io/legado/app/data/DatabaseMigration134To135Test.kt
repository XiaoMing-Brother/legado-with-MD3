package io.legado.app.data

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 本地发布的 134 与上游 134 版本号相同、schema 不同：本地库没有 AI 表。
 * 134→135 的自动迁移只补列，AI 表由 [DatabaseMigrations.Migration_134_135] 补建。
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [35])
class DatabaseMigration134To135Test {

    private val expectedTables = listOf(
        "ai_provider_profiles",
        "ai_model_profiles",
        "ai_task_presets",
        "ai_artifacts",
        "ai_chat_conversations",
        "ai_chat_messages",
        "ai_memory",
        "ai_prompt_presets",
    )

    @Test
    fun createsAiTablesOnLocal134Database() {
        val db = openEmptyDatabase(version = 134)
        db.execSQL("CREATE TABLE `highlightRules` (`id` TEXT NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL(
            "CREATE TABLE `read_aloud_audio_downloads` " +
                "(`bookUrl` TEXT NOT NULL, `chapterIndex` INTEGER NOT NULL, " +
                "`title` TEXT NOT NULL, `files` TEXT NOT NULL, `createTime` INTEGER NOT NULL, " +
                "PRIMARY KEY(`bookUrl`, `chapterIndex`))"
        )
        DatabaseMigrations.Migration_134_135().onPostMigrate(db)
        assertAiSchema(db)
        assertColumns(
            db,
            "highlightRules",
            listOf(
                "textColorNight",
                "bgColorNight",
                "borderColorNight",
                "underlineColorNight",
            ),
        )
        assertColumns(db, "read_aloud_audio_downloads", listOf("expectedSentenceCount"))
        // 从上游 134（已有 AI 表）升级时也不能因重复建表失败
        DatabaseMigrations.Migration_134_135().onPostMigrate(db)
        assertAiSchema(db)
        db.close()
    }

    private fun openEmptyDatabase(version: Int): SupportSQLiteDatabase {
        val context = RuntimeEnvironment.getApplication()
        val file = context.getDatabasePath("migration-134-135-test")
        file.parentFile?.mkdirs()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(file.absolutePath)
                .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = Unit
                })
                .build()
        )
        return helper.writableDatabase
    }

    private fun assertAiSchema(db: SupportSQLiteDatabase) {
        val tables = db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table'"
        ).use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
        expectedTables.forEach { table ->
            assertTrue("missing table $table", table in tables)
        }
        val indices = db.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND name LIKE 'index_ai_%'"
        ).use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
        assertEquals(8, indices.size)
    }

    private fun assertColumns(
        db: SupportSQLiteDatabase,
        table: String,
        columns: List<String>,
    ) {
        val existing = db.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameAt = cursor.getColumnIndexOrThrow("name")
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(nameAt))
            }
        }
        columns.forEach { column ->
            assertTrue("missing column $table.$column", column in existing)
        }
    }
}
