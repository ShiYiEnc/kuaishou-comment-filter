package dev.shiyi.kuaishoufilter.data

import android.content.Context
import dev.shiyi.kuaishoufilter.core.RuleConfig

class ConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("rules", Context.MODE_PRIVATE)

    fun load(): RuleConfig = prefs.getString("config", null)?.let(ConfigCodec::decode) ?: RuleConfig()

    @Synchronized
    fun save(config: RuleConfig): RuleConfig {
        val next = config.copy(revision = load().revision + 1)
        val encoded = ConfigCodec.encode(next)
        ConfigCodec.decode(encoded)
        check(prefs.edit().putString("config", encoded).commit()) { "Configuration write failed" }
        return next
    }
}
