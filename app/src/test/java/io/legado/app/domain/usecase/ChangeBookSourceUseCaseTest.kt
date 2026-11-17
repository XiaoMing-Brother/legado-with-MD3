package io.legado.app.domain.usecase

import android.app.Application
import androidx.room.Room
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.repository.ReadRecordRepository
import io.legado.app.data.repository.SettingsRepository
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.gateway.ReadSettingsGateway
import io.legado.app.domain.model.settings.OtherSettings
import io.legado.app.domain.model.settings.ReadSettings
import io.legado.app.help.book.BookHelp
import io.legado.app.help.config.AppConfigStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ChangeBookSourceUseCaseTest {

    private lateinit var database: AppDatabase
    private lateinit var useCase: ChangeBookSourceUseCase

    private val oldBook = Book(
        bookUrl = "https://old.example/book",
        name = "Same work",
        author = "Same author",
        origin = "https://old.example/source",
    )
    private val newBook = Book(
        bookUrl = "https://new.example/book",
        name = oldBook.name,
        author = oldBook.author,
        origin = "https://new.example/source",
    )
    private val oldChapter = BookChapter(
        bookUrl = oldBook.bookUrl,
        url = "https://old.example/chapter/1",
        title = "Chapter One",
        index = 0,
    )
    private val newChapter = oldChapter.copy(
        bookUrl = newBook.bookUrl,
        url = "https://new.example/chapter/1",
    )

    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication().also {
            it.injectAsAppCtx()
            AppConfigStore.init(it)
        }
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        useCase = ChangeBookSourceUseCase(
            database = database,
            bookDao = database.bookDao,
            bookChapterDao = database.bookChapterDao,
            otherSettingsGateway = StubOtherSettingsGateway(),
            readSettingsGateway = StubReadSettingsGateway(),
            readRecordRepository = ReadRecordRepository(
                database.readRecordDao,
                database,
                SettingsRepository(),
            ),
        )
    }

    @After
    fun tearDown() {
        BookHelp.clearCache(oldBook)
        BookHelp.clearCache(newBook)
        database.close()
    }

    @Test
    fun `source switch never reuses content cached by the previous source`() = runBlocking {
        database.bookDao.insert(oldBook)
        database.bookChapterDao.insert(oldChapter)
        val oldContent = BookHelp.getChapterContentFile(oldBook, oldChapter).apply {
            parentFile!!.mkdirs()
            writeText("Content from the old source")
        }
        val chapterImage = File(oldContent.parentFile, "cover.jpg").apply {
            writeText("Shared image")
        }

        useCase.changeTo(
            oldBook = oldBook,
            newBook = newBook,
            chapters = listOf(newChapter),
            options = ChangeSourceMigrationOptions(
                migrateChapters = false,
                deleteDownloadedChapters = false,
            ),
        )

        assertNull(BookHelp.getContent(newBook, newChapter))
        val movedImage = File(
            BookHelp.getChapterContentFile(newBook, newChapter).parentFile,
            chapterImage.name,
        )
        assertTrue("未勾选删除图片时，图片缓存应随目录迁移保留", movedImage.isFile)
    }

    private class StubOtherSettingsGateway : OtherSettingsGateway {
        override val currentSettings: OtherSettings = OtherSettings()
        override val settings = MutableStateFlow(OtherSettings())
        override suspend fun update(transform: (OtherSettings) -> OtherSettings) = Unit
    }

    private class StubReadSettingsGateway : ReadSettingsGateway {
        override val currentSettings: ReadSettings = ReadSettings()
        override val settings = MutableStateFlow(ReadSettings())
        override suspend fun update(transform: (ReadSettings) -> ReadSettings) = Unit
    }
}
