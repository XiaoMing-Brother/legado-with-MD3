package io.legado.app.domain.usecase

import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.TranslationCache
import io.legado.app.data.repository.translation.GoogleTranslateClient
import io.legado.app.domain.gateway.TranslationCacheGateway
import io.legado.app.domain.gateway.TranslationSettingsGateway
import io.legado.app.domain.model.ContentChunker
import io.legado.app.domain.model.PartialTranslationAssembler
import io.legado.app.domain.model.TextChunk
import io.legado.app.domain.model.TranslationConstants
import io.legado.app.help.book.BookHelp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 整章翻译。
 *
 * 只保留 Google 翻译：应用内 AI 翻译（`PROVIDER_APP_AI`）已从产品移除，
 * 因此这里不再有 AI 提示词、词典增量与流式预览那套逻辑。
 * 分块、缓存与部分翻译拼装保持不变，翻译进度仍是逐块上报。
 */
class TranslateChapterUseCase(
    private val translationCacheGateway: TranslationCacheGateway,
    private val translationSettingsGateway: TranslationSettingsGateway,
    private val googleTranslate: GoogleTranslateClient = GoogleTranslateClient(),
) {

    data class TranslationProgress(
        val currentChunk: Int,
        val totalChunks: Int,
        val mixedContent: String? = null,
        val translatedChunkIndices: Set<Int> = emptySet()
    )

    suspend fun execute(
        book: Book,
        bookChapter: BookChapter,
        onProgress: (TranslationProgress) -> Unit,
        onTranslateStarted: () -> Unit,
        /** 保留参数以兼容既有调用方：Google 翻译不产生思考过程。 */
        onThinkingChanged: (Boolean) -> Unit = {},
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val settings = translationSettingsGateway.currentSettings
            val targetLanguage = settings.targetLanguage

            val originalContent = BookHelp.getContent(book, bookChapter)
                ?: return@withContext Result.failure(Exception("Failed to read original content"))

            val cachedTranslation =
                translationCacheGateway.readTranslation(book, bookChapter, targetLanguage)
            if (cachedTranslation != null) {
                onProgress(TranslationProgress(1, 1, cachedTranslation, emptySet()))
                return@withContext Result.success(cachedTranslation)
            }

            val contentHash = translationCacheGateway.computeContentHash(originalContent)

            val chunks = ContentChunker.chunk(
                originalContent,
                settings.maxCharsPerChunk.coerceAtLeast(1000)
            )
            if (chunks.isEmpty()) {
                return@withContext Result.failure(Exception("Failed to chunk content"))
            }

            val cachedChunks =
                translationCacheGateway.getCachedChunks(book, bookChapter, targetLanguage, contentHash)
            val cachedChunkMap = cachedChunks.filter { it.isSuccess }.associateBy { it.chunkIndex }

            val translatedChunks = mutableMapOf<Int, String>()
            val pendingChunks = mutableListOf<TextChunk>()

            for (chunk in chunks) {
                val cached = cachedChunkMap[chunk.index]
                if (cached != null && cached.translatedChunkContent != null) {
                    translatedChunks[chunk.index] = cached.translatedChunkContent
                } else {
                    pendingChunks.add(chunk)
                }
            }

            if (translatedChunks.isNotEmpty()) {
                val mixedContent = PartialTranslationAssembler.assemble(chunks, translatedChunks)
                onProgress(
                    TranslationProgress(
                        translatedChunks.size,
                        chunks.size,
                        mixedContent,
                        translatedChunks.keys.toSet()
                    )
                )
            }

            if (pendingChunks.isEmpty()) {
                val sortedChunks = chunks.sortedBy { it.index }.mapNotNull {
                    translatedChunks[it.index]?.let { content ->
                        TextChunk(it.index, content, it.paragraphIndices)
                    }
                }
                val mergedContent = ContentChunker.merge(sortedChunks)
                translationCacheGateway.writeTranslation(
                    book,
                    bookChapter,
                    targetLanguage,
                    mergedContent
                )
                onProgress(
                    TranslationProgress(
                        chunks.size,
                        chunks.size,
                        mergedContent,
                        chunks.map { it.index }.toSet()
                    )
                )
                return@withContext Result.success(mergedContent)
            }

            onTranslateStarted()
            for (chunk in pendingChunks.sortedBy { it.index }) {
                val publishWithoutPartial = {
                    val mixedContent = PartialTranslationAssembler.assemble(chunks, translatedChunks)
                    onProgress(
                        TranslationProgress(
                            currentChunk = translatedChunks.size,
                            totalChunks = chunks.size,
                            mixedContent = mixedContent,
                            translatedChunkIndices = translatedChunks.keys.toSet(),
                        )
                    )
                }
                val result = try {
                    translateAndCacheChunk(
                        chunk = chunk,
                        book = book,
                        bookChapter = bookChapter,
                        targetLanguage = targetLanguage,
                        contentHash = contentHash,
                        retryCount = settings.retryCount,
                    )
                } catch (e: CancellationException) {
                    publishWithoutPartial()
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
                if (result.isFailure) {
                    publishWithoutPartial()
                    return@withContext Result.failure(
                        result.exceptionOrNull() ?: Exception("Translation failed")
                    )
                }

                translatedChunks[chunk.index] = result.getOrThrow()
                publishWithoutPartial()
            }

            if (translatedChunks.size != chunks.size) {
                return@withContext Result.failure(Exception("Translation incomplete"))
            }

            val allTranslatedChunks = chunks.sortedBy { it.index }.mapNotNull { chunk ->
                translatedChunks[chunk.index]?.let { content ->
                    TextChunk(chunk.index, content, chunk.paragraphIndices)
                }
            }
            val mergedContent = ContentChunker.merge(allTranslatedChunks)
            translationCacheGateway.writeTranslation(book, bookChapter, targetLanguage, mergedContent)

            onProgress(
                TranslationProgress(
                    chunks.size,
                    chunks.size,
                    mergedContent,
                    chunks.map { it.index }.toSet()
                )
            )
            Result.success(mergedContent)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun translateAndCacheChunk(
        chunk: TextChunk,
        book: Book,
        bookChapter: BookChapter,
        targetLanguage: String,
        contentHash: String,
        retryCount: Int,
    ): Result<String> {
        val existingCache =
            translationCacheGateway.getCachedChunk(book, bookChapter, targetLanguage, chunk.index)
        if (existingCache?.isSuccess == true && existingCache.translatedChunkContent != null) {
            return Result.success(existingCache.translatedChunkContent)
        }

        val result = translateChunkWithRetry(
            chunk = chunk,
            targetLanguage = targetLanguage,
            retryCount = retryCount,
        )
        if (result.isSuccess) {
            translationCacheGateway.saveChunk(
                book, bookChapter, targetLanguage,
                chunk.index, chunk.content, contentHash,
                TranslationConstants.PROVIDER_GOOGLE,
                TranslationCache.STATUS_SUCCESS, result.getOrThrow(), null
            )
        }
        return result
    }

    private suspend fun translateChunkWithRetry(
        chunk: TextChunk,
        targetLanguage: String,
        retryCount: Int,
    ): Result<String> {
        var lastError: Exception? = null
        for (attempt in 0..retryCount.coerceIn(0, 5)) {
            val result = googleTranslate.translate(chunk.content, targetLanguage)
            if (result.isSuccess) {
                return result
            }
            lastError = result.exceptionOrNull() as? Exception
        }
        return Result.failure(lastError ?: Exception("Translation failed after retries"))
    }
}
