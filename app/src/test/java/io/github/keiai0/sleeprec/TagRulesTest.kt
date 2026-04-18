package io.github.keiai0.sleeprec

import io.github.keiai0.sleeprec.TagRules.Error
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TagRulesTest {
    @Test fun validTagIsAccepted() = assertNull(TagRules.validate("お酒", emptyList()))

    @Test fun blankIsEmpty() {
        assertEquals(Error.EMPTY, TagRules.validate("", emptyList()))
        assertEquals(Error.EMPTY, TagRules.validate("   ", emptyList()))
    }

    @Test fun twentyCharactersIsOkTwentyOneIsNot() {
        assertNull(TagRules.validate("あ".repeat(20), emptyList()))
        assertEquals(Error.TOO_LONG, TagRules.validate("あ".repeat(21), emptyList()))
    }

    @Test fun surrogatePairCountsAsOneCharacter() {
        // 絵文字は UTF-16 では 2 文字分だが、見た目は 1 文字。20 個までは許可する
        assertNull(TagRules.validate("😀".repeat(20), emptyList()))
        assertEquals(Error.TOO_LONG, TagRules.validate("😀".repeat(21), emptyList()))
    }

    @Test fun surroundingSpacesAreIgnoredForLength() =
        assertNull(TagRules.validate("  " + "あ".repeat(20) + "  ", emptyList()))

    @Test fun duplicateIsRejected() = assertEquals(Error.DUPLICATE, TagRules.validate("お酒", listOf("お酒")))

    @Test fun duplicateIgnoresCaseAndWidth() {
        assertEquals(Error.DUPLICATE, TagRules.validate("coffee", listOf("Coffee")))
        assertEquals(Error.DUPLICATE, TagRules.validate("ｃｏｆｆｅｅ", listOf("coffee")))
        assertEquals(Error.DUPLICATE, TagRules.validate("ﾗﾝﾆﾝｸﾞ", listOf("ランニング")))
    }

    @Test fun duplicateIgnoresSurroundingSpaces() =
        assertEquals(Error.DUPLICATE, TagRules.validate(" お酒 ", listOf("お酒")))

    @Test fun differentTagsAreFine() = assertNull(TagRules.validate("運動", listOf("お酒", "コーヒー")))

    @Test fun tooManyTags() {
        val tags = (1..TagRules.MAX_TAGS).map { "タグ$it" }
        assertEquals(Error.TOO_MANY, TagRules.validate("新しいタグ", tags))
    }

    @Test fun cleanCollapsesWhitespace() = assertEquals("お 酒 です", TagRules.clean("  お   酒\tです "))
}
