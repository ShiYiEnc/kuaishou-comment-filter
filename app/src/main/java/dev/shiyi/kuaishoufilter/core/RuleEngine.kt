package dev.shiyi.kuaishoufilter.core

import java.util.Locale

class RuleEngine(private val config: RuleConfig) {
    private val locations = config.activeRules.locations.mapNotNull(LocationNames::normalize).toSet()
    private val keywords = config.activeRules.keywords.map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }
    private val userIds = config.activeRules.users.map { it.id.trim() }.filter { it.isNotEmpty() }.toSet()

    fun evaluate(comment: CommentRecord): FilterDecision {
        if (!config.enabled) return FilterDecision(true, emptySet())
        val reasons = buildSet {
            if (comment.normalizedLocation != null && comment.normalizedLocation in locations) add(MatchReason.LOCATION)
            if (keywords.isNotEmpty()) {
                val text = comment.text.lowercase(Locale.ROOT)
                if (keywords.any { it in text }) add(MatchReason.KEYWORD)
            }
            if (comment.userId?.trim() in userIds) add(MatchReason.USER)
            if (comment.gender in config.activeRules.genders) add(MatchReason.GENDER)
        }
        val matched = reasons.isNotEmpty()
        return FilterDecision(
            visible = if (config.action == MatchAction.SHOW_MATCHED) matched else !matched,
            reasons = reasons,
        )
    }
}
