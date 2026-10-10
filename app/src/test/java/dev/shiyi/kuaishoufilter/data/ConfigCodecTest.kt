package dev.shiyi.kuaishoufilter.data

import dev.shiyi.kuaishoufilter.core.*
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import org.json.JSONArray

class ConfigCodecTest {
    @Test fun bothListsAndCustomActionSurviveRoundTrip() {
        val config = RuleConfig(
            revision = 8,
            enabled = true,
            selectedList = ListKind.WHITELIST,
            action = MatchAction.HIDE_MATCHED,
            blacklist = RuleList(setOf("广东", "日本"), setOf("广告"), listOf(UserRule("42", "昵称")), setOf(Gender.MALE)),
            whitelist = RuleList(setOf("北京"), setOf("Hello"), listOf(UserRule("43")), setOf(Gender.FEMALE, Gender.UNKNOWN)),
        )
        assertEquals(config, ConfigCodec.decode(ConfigCodec.encode(config)))
    }

    @Test fun inactiveListIsPreservedWhenEditing() {
        val config = RuleConfig(whitelist = RuleList(keywords = setOf("保留")))
        val changed = config.withActiveRules(RuleList(locations = setOf("上海")))
        assertEquals(config.whitelist, ConfigCodec.decode(ConfigCodec.encode(changed)).whitelist)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownSchemaDoesNotEnableFiltering() {
        ConfigCodec.decode(ConfigCodec.encode(RuleConfig()).replace("\"schemaVersion\":2", "\"schemaVersion\":3"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyKeywordIsRejected() {
        ConfigCodec.decode(ConfigCodec.encode(RuleConfig(blacklist = RuleList(keywords = setOf(" ")))))
    }

    @Test fun startupDefaultsAreDisabled() {
        assertFalse(ConfigCodec.decode(ConfigCodec.encode(RuleConfig())).enabled)
    }

    @Test fun oldConfigurationRetainsAllRulesWithGenderFilteringUnselected() {
        val original = RuleConfig(enabled = true, revision = 9, selectedList = ListKind.WHITELIST,
            blacklist = RuleList(keywords = setOf("广告")), whitelist = RuleList(locations = setOf("辽宁")))
        val json = JSONObject(ConfigCodec.encode(original)).put("schemaVersion", 1)
        json.getJSONObject("blacklist").remove("genders")
        json.getJSONObject("whitelist").remove("genders")
        assertEquals(original, ConfigCodec.decode(json.toString()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownGenderRuleIsRejected() {
        val json = JSONObject(ConfigCodec.encode(RuleConfig()))
        json.getJSONObject("blacklist").put("genders", JSONArray(listOf("INVALID")))
        ConfigCodec.decode(json.toString())
    }
}
