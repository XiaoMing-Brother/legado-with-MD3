package io.legado.app.help.book

import io.legado.app.constant.AppPattern
import io.legado.app.domain.model.TextProjection
import io.legado.app.feature.reader.legacy.LegacyReaderImageOptionsResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentHelpReSegmentTest {
    private val paragraphCommentImage =
        """<img src="data:image/svg+xml;base64,AAAA,{"style":"TEXT","type":"qd","click":"showQdCmt('1','2','3','4')"}">"""

    @Test
    fun keepsParagraphCommentImageOptionsWhenRewritingQuotes() {
        val content = buildString {
            append(paragraphCommentImage)
            append("“第一句。”他说，“第二句。”\n")
            append(paragraphCommentImage)
            append("后面的段落。")
        }

        val result = ContentHelp.reSegment(content, "第一章")

        assertEquals(2, result.split(paragraphCommentImage).size - 1)
        assertTrue(result.contains(""",{"style":"TEXT","type":"qd","click":"showQdCmt('1','2','3','4')"}"""))
        val source = AppPattern.imgPattern.find(result)!!.groupValues[1]
        val options = LegacyReaderImageOptionsResolver.resolve(source, htmlParagraph = false)!!
        assertTrue(options.paragraphCommentIcon)
        assertEquals("showQdCmt('1','2','3','4')", options.action)
    }

    @Test
    fun keepsParagraphCommentImageOptionsWithTextProjection() {
        val content = "$paragraphCommentImage\n他说：“你好。”\n尾段。"
        val projection = TextProjection(content.length)

        val result = ContentHelp.reSegmentMapped(content, "第一章", projection)

        assertTrue(result.contains(paragraphCommentImage))
        assertEquals(result.length, projection.length)
    }

    /** 占位字要避开正文已有的扩展 A 汉字，否则还原时会把原文误当成图片标签。 */
    @Test
    fun imagePlaceholderSkipsCharactersAlreadyPresentInContent() {
        val rare = "\u3400\u3401"
        val content = "$rare$paragraphCommentImage$rare"
        val projection = TextProjection(content.length)

        val protected = ContentHelp.protectImageTags(content, projection)
        assertEquals(1, protected.tags.size)
        assertTrue(protected.content.contains(rare))

        val restored = ContentHelp.restoreImageTags(
            protected.content,
            protected.tags,
            protected.placeholderStart,
            projection,
        )
        assertEquals(content, restored)
        assertEquals(content.length, projection.length)
    }
}
