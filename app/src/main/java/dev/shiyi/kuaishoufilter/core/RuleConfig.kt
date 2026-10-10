package dev.shiyi.kuaishoufilter.core

enum class ListKind { BLACKLIST, WHITELIST }
enum class MatchAction { HIDE_MATCHED, SHOW_MATCHED }
enum class MatchReason { LOCATION, KEYWORD, USER, GENDER }
enum class Gender {
    MALE, FEMALE, UNKNOWN;

    companion object {
        fun fromKuaishou(raw: String?): Gender = when (raw) {
            "M" -> MALE
            "F" -> FEMALE
            else -> UNKNOWN
        }
    }
}

data class UserRule(val id: String, val note: String = "")

data class RuleList(
    val locations: Set<String> = emptySet(),
    val keywords: Set<String> = emptySet(),
    val users: List<UserRule> = emptyList(),
    val genders: Set<Gender> = emptySet(),
) {
    val isEmpty: Boolean get() = locations.isEmpty() && keywords.isEmpty() && users.isEmpty() && genders.isEmpty()
}

data class RuleConfig(
    val schemaVersion: Int = 2,
    val revision: Long = 0,
    val enabled: Boolean = false,
    val selectedList: ListKind = ListKind.BLACKLIST,
    val action: MatchAction = MatchAction.HIDE_MATCHED,
    val blacklist: RuleList = RuleList(),
    val whitelist: RuleList = RuleList(),
) {
    val activeRules: RuleList get() = if (selectedList == ListKind.BLACKLIST) blacklist else whitelist

    fun withActiveRules(rules: RuleList): RuleConfig =
        if (selectedList == ListKind.BLACKLIST) copy(blacklist = rules) else copy(whitelist = rules)

    fun select(kind: ListKind): RuleConfig = copy(
        selectedList = kind,
        action = if (kind == ListKind.BLACKLIST) MatchAction.HIDE_MATCHED else MatchAction.SHOW_MATCHED,
    )
}

data class CommentRecord(
    val commentId: String,
    val userId: String?,
    val text: String,
    val rawLocation: String?,
    val normalizedLocation: String? = LocationNames.normalize(rawLocation),
    val gender: Gender = Gender.UNKNOWN,
)

data class FilterDecision(val visible: Boolean, val reasons: Set<MatchReason>)
