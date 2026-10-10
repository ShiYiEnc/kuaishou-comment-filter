package dev.shiyi.kuaishoufilter.core

data class DisplayRow<T>(val item: T, val rootComment: CommentRecord?, val parentRootId: String?)
data class Projection<T>(
    val items: List<T>, val seen: Int, val hidden: Int, val missingLocation: Int,
    val male: Int = 0, val female: Int = 0, val unknownGender: Int = 0,
)

object DisplayProjector {
    fun <T> project(rows: List<DisplayRow<T>>, engine: RuleEngine): Projection<T> {
        val decisions = rows.mapNotNull { it.rootComment }.associate {
            it.commentId to engine.evaluate(it)
        }
        val hiddenIds = decisions.filterValues { !it.visible }.keys
        val visible = rows.filter {
            it.rootComment?.commentId !in hiddenIds && it.parentRootId !in hiddenIds
        }.map { it.item }
        val comments = rows.mapNotNull { it.rootComment }.distinctBy { it.commentId }
        return Projection(
            visible, decisions.size, hiddenIds.size, comments.count { it.normalizedLocation == null },
            comments.count { it.gender == Gender.MALE }, comments.count { it.gender == Gender.FEMALE },
            comments.count { it.gender == Gender.UNKNOWN },
        )
    }
}
