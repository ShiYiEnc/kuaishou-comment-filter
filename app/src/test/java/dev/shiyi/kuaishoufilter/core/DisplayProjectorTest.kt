package dev.shiyi.kuaishoufilter.core

import org.junit.Assert.*
import org.junit.Test

class DisplayProjectorTest {
    private fun engine(action: MatchAction = MatchAction.HIDE_MATCHED) = RuleEngine(RuleConfig(
        enabled = true, action = action, blacklist = RuleList(locations = setOf("广东")),
    ))
    private fun root(id: String, location: String) = CommentRecord(id, "user", "text", location)

    @Test fun removesHiddenRootAndItsAttachedRowsOnly() {
        val rows = listOf(
            DisplayRow("header", null, null),
            DisplayRow("root-a", root("a", "广东"), null),
            DisplayRow("reply-a", null, "a"),
            DisplayRow("expand-a", null, "a"),
            DisplayRow("root-b", root("b", "上海"), null),
            DisplayRow("reply-b-matches-keyword", null, "b"),
            DisplayRow("footer", null, null),
        )
        val result = DisplayProjector.project(rows, engine())
        assertEquals(listOf("header", "root-b", "reply-b-matches-keyword", "footer"), result.items)
        assertEquals(2, result.seen)
        assertEquals(1, result.hidden)
        assertEquals(7, rows.size)
    }

    @Test fun onlyShowKeepsMatchedRootAndAllItsReplies() {
        val rows = listOf(
            DisplayRow("a", root("a", "广东"), null),
            DisplayRow("reply-a", null, "a"),
            DisplayRow("b", root("b", "上海"), null),
            DisplayRow("reply-b", null, "b"),
        )
        assertEquals(listOf("a", "reply-a"), DisplayProjector.project(rows, engine(MatchAction.SHOW_MATCHED)).items)
    }

    @Test fun repliesWithoutTheirRootInThisListAreUntouched() {
        val rows = listOf(DisplayRow("standalone-reply", null, "a"))
        assertEquals(listOf("standalone-reply"), DisplayProjector.project(rows, engine(MatchAction.SHOW_MATCHED)).items)
    }

    @Test fun allHiddenPageDoesNotChangeInput() {
        val rows = listOf(DisplayRow("a", root("a", "广东"), null))
        assertTrue(DisplayProjector.project(rows, engine()).items.isEmpty())
        assertEquals(1, rows.size)
    }

    @Test fun genderFilteringRemovesAttachedRepliesAndCountsOnlyUniqueRoots() {
        val male = root("a", "广东").copy(gender = Gender.MALE)
        val female = root("b", "北京").copy(gender = Gender.FEMALE)
        val unknown = root("c", "上海")
        val rows = listOf(DisplayRow("header", null, null), DisplayRow("a", male, null),
            DisplayRow("reply-a", null, "a"), DisplayRow("a-copy", male, null),
            DisplayRow("b", female, null), DisplayRow("reply-b", null, "b"), DisplayRow("c", unknown, null))
        val engine = RuleEngine(RuleConfig(enabled = true, blacklist = RuleList(genders = setOf(Gender.MALE))))
        val result = DisplayProjector.project(rows, engine)
        assertEquals(listOf("header", "b", "reply-b", "c"), result.items)
        assertEquals(3, result.seen)
        assertEquals(1, result.hidden)
        assertEquals(1, result.male)
        assertEquals(1, result.female)
        assertEquals(1, result.unknownGender)
    }
}
