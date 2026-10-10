package dev.shiyi.kuaishoufilter.core

import org.junit.Assert.*
import org.junit.Test

class RuleEngineTest {
    private val comment = CommentRecord("c1", "user-42", "Hello 快手", "广东省")

    @Test fun allFourModesHaveComplementaryResults() {
        for (kind in ListKind.entries) for (action in MatchAction.entries) {
            val config = RuleConfig(enabled = true, selectedList = kind, action = action)
                .withActiveRules(RuleList(locations = setOf("广东")))
            val engine = RuleEngine(config)
            assertEquals(action == MatchAction.SHOW_MATCHED, engine.evaluate(comment).visible)
            assertEquals(action == MatchAction.HIDE_MATCHED, engine.evaluate(comment.copy(rawLocation = "北京", normalizedLocation = "北京")).visible)
        }
    }

    @Test fun eachRuleCategoryMatchesIndependently() {
        val lists = listOf(
            RuleList(locations = setOf("广东")) to MatchReason.LOCATION,
            RuleList(keywords = setOf("  HELLO  ")) to MatchReason.KEYWORD,
            RuleList(users = listOf(UserRule("user-42", "不参与匹配"))) to MatchReason.USER,
        )
        for ((rules, reason) in lists) {
            val decision = RuleEngine(RuleConfig(enabled = true, blacklist = rules)).evaluate(comment)
            assertFalse(decision.visible)
            assertEquals(setOf(reason), decision.reasons)
        }
    }

    @Test fun emptyListAndDisabledHaveExplicitSemantics() {
        assertTrue(RuleEngine(RuleConfig(enabled = true)).evaluate(comment).visible)
        assertFalse(RuleEngine(RuleConfig(enabled = true, action = MatchAction.SHOW_MATCHED)).evaluate(comment).visible)
        assertTrue(RuleEngine(RuleConfig(enabled = false, action = MatchAction.SHOW_MATCHED)).evaluate(comment).visible)
    }

    @Test fun inactiveListNeverParticipates() {
        val config = RuleConfig(enabled = true, whitelist = RuleList(locations = setOf("广东")))
        assertTrue(RuleEngine(config).evaluate(comment).visible)
        assertTrue(RuleEngine(config.select(ListKind.WHITELIST)).evaluate(comment).visible)
    }

    @Test fun missingLocationStillMatchesOtherConditions() {
        val unknown = comment.copy(rawLocation = null, normalizedLocation = null)
        val rules = RuleList(locations = setOf("广东"), keywords = setOf("快手"))
        val result = RuleEngine(RuleConfig(enabled = true, blacklist = rules)).evaluate(unknown)
        assertEquals(setOf(MatchReason.KEYWORD), result.reasons)
        assertFalse(result.visible)
        assertTrue(RuleEngine(RuleConfig(enabled = true, blacklist = RuleList(locations = setOf("广东")))).evaluate(unknown).visible)
    }

    @Test fun nicknameIsOnlyANoteAndLocationIsExact() {
        val rules = RuleList(users = listOf(UserRule("another-id", "user-42")), locations = setOf("广"))
        assertTrue(RuleEngine(RuleConfig(enabled = true, blacklist = rules)).evaluate(comment).visible)
        assertEquals("广东 深圳", LocationNames.normalize("广东 深圳"))
        assertEquals("美国", LocationNames.normalize(" 美国 "))
        assertNull(LocationNames.normalize("   "))
    }

    @Test fun changingListsSelectsConventionalDefaultAction() {
        assertEquals(MatchAction.SHOW_MATCHED, RuleConfig().select(ListKind.WHITELIST).action)
        assertEquals(MatchAction.HIDE_MATCHED, RuleConfig().select(ListKind.BLACKLIST).action)
    }

    @Test fun genderMatchesInBothListsAndBothActions() {
        for (kind in ListKind.entries) for (action in MatchAction.entries) for (selected in Gender.entries) {
            val config = RuleConfig(enabled = true, selectedList = kind, action = action)
                .withActiveRules(RuleList(genders = setOf(selected)))
            assertFalse(config.activeRules.isEmpty)
            for (actual in Gender.entries) {
                val decision = RuleEngine(config).evaluate(comment.copy(gender = actual))
                val matched = selected == actual
                assertEquals(if (action == MatchAction.SHOW_MATCHED) matched else !matched, decision.visible)
                assertEquals(if (matched) setOf(MatchReason.GENDER) else emptySet<MatchReason>(), decision.reasons)
            }
        }
    }

    @Test fun genderIsAnIndependentOrConditionAndSupportsMultipleSelections() {
        val rules = RuleList(locations = setOf("北京"), genders = setOf(Gender.FEMALE, Gender.UNKNOWN))
        val engine = RuleEngine(RuleConfig(enabled = true, action = MatchAction.SHOW_MATCHED, blacklist = rules))
        assertTrue(engine.evaluate(comment.copy(gender = Gender.FEMALE)).visible)
        assertTrue(engine.evaluate(comment.copy(gender = Gender.UNKNOWN)).visible)
        assertFalse(engine.evaluate(comment.copy(gender = Gender.MALE)).visible)
        assertTrue(engine.evaluate(comment.copy(normalizedLocation = "北京", gender = Gender.MALE)).visible)
    }

    @Test fun inactiveAndDisabledGenderRulesNeverHideComments() {
        assertTrue(RuleEngine(RuleConfig(enabled = true, whitelist = RuleList(genders = setOf(Gender.UNKNOWN)))).evaluate(comment).visible)
        val disabled = RuleConfig(action = MatchAction.SHOW_MATCHED, blacklist = RuleList(genders = setOf(Gender.FEMALE)))
        assertTrue(RuleEngine(disabled).evaluate(comment.copy(gender = Gender.MALE)).visible)
    }
}
