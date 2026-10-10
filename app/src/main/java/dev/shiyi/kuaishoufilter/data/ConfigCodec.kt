package dev.shiyi.kuaishoufilter.data

import dev.shiyi.kuaishoufilter.core.*
import org.json.JSONArray
import org.json.JSONObject

object ConfigCodec {
    fun encode(config: RuleConfig): String = JSONObject().apply {
        put("schemaVersion", config.schemaVersion)
        put("revision", config.revision)
        put("enabled", config.enabled)
        put("selectedList", config.selectedList.name)
        put("action", config.action.name)
        put("blacklist", encodeList(config.blacklist))
        put("whitelist", encodeList(config.whitelist))
    }.toString()

    fun decode(text: String): RuleConfig {
        require(text.length <= 128_000) { "Configuration too large" }
        val obj = JSONObject(text)
        val schema = obj.getInt("schemaVersion")
        require(schema in 1..2) { "Unsupported configuration schema" }
        return RuleConfig(
            revision = obj.getLong("revision").also { require(it >= 0) },
            enabled = obj.getBoolean("enabled"),
            selectedList = ListKind.valueOf(obj.getString("selectedList")),
            action = MatchAction.valueOf(obj.getString("action")),
            blacklist = decodeList(obj.getJSONObject("blacklist"), schema),
            whitelist = decodeList(obj.getJSONObject("whitelist"), schema),
        )
    }

    private fun encodeList(list: RuleList) = JSONObject().apply {
        put("locations", JSONArray(list.locations.sorted()))
        put("keywords", JSONArray(list.keywords.sorted()))
        put("genders", JSONArray(list.genders.sortedBy { it.ordinal }.map { it.name }))
        put("users", JSONArray().apply {
            list.users.forEach { put(JSONObject().put("id", it.id).put("note", it.note)) }
        })
    }

    private fun strings(array: JSONArray): Set<String> = buildSet {
        for (i in 0 until array.length()) {
            val value = array.getString(i).trim()
            require(value.isNotEmpty()) { "Empty rule" }
            add(value)
        }
    }

    private fun decodeList(obj: JSONObject, schema: Int): RuleList {
        val users = obj.getJSONArray("users")
        return RuleList(
            locations = strings(obj.getJSONArray("locations")).mapNotNull(LocationNames::normalize).toSet(),
            keywords = strings(obj.getJSONArray("keywords")),
            genders = if (schema == 1) emptySet() else strings(obj.getJSONArray("genders")).map(Gender::valueOf).toSet(),
            users = (0 until users.length()).map {
                val user = users.getJSONObject(it)
                UserRule(user.getString("id").trim().also { id -> require(id.isNotEmpty()) }, user.optString("note").trim())
            }.distinctBy { it.id },
        )
    }
}
