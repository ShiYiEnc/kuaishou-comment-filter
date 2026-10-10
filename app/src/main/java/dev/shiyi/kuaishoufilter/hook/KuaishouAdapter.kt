package dev.shiyi.kuaishoufilter.hook

import dev.shiyi.kuaishoufilter.core.CommentRecord
import dev.shiyi.kuaishoufilter.core.DisplayRow
import dev.shiyi.kuaishoufilter.core.Gender
import java.lang.reflect.Field
import java.util.Collections
import java.util.IdentityHashMap

class KuaishouAdapter(private val commentClass: Class<*>) {
    private fun field(name: String, type: Class<*>): Field = commentClass.getField(name).apply {
        require(this.type == type) { "Unexpected field type: $name" }
        isAccessible = true
    }
    private val id = field("mId", String::class.java)
    private val userId = field("mAuthorId", String::class.java)
    private val text = field("mComment", String::class.java)
    private val location = field("mAuthorArea", String::class.java)
    private val type = field("mType", Int::class.javaPrimitiveType!!)
    private val parent = field("mParent", commentClass)
    private val author = commentClass.getField("mUser").apply { isAccessible = true }
    private val authorId = author.type.getField("mId").apply {
        require(type == String::class.java)
        isAccessible = true
    }
    private val sex = author.type.getField("mSex").apply {
        require(type == String::class.java)
        isAccessible = true
    }

    fun readList(items: List<*>): List<DisplayRow<Any?>> = items.map { item ->
        if (item == null || !commentClass.isInstance(item)) return@map DisplayRow(item, null, null)
        val commentId = id.get(item) as String?
        val parentItem = parent.get(item)
        val record = if (type.getInt(item) == 1 && parentItem == null && !commentId.isNullOrEmpty()) {
            val id = userId.get(item) as String?
            val user = author.get(item)
            val gender = if (user != null && !id.isNullOrEmpty() && authorId.get(user) == id) {
                Gender.fromKuaishou(sex.get(user) as String?)
            } else Gender.UNKNOWN
            CommentRecord(commentId, id, text.get(item) as String? ?: "", location.get(item) as String?, gender = gender)
        } else null
        DisplayRow(item, record, parentItem?.let(::rootId))
    }

    private fun rootId(firstParent: Any): String? {
        val visited = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        var current = firstParent
        while (true) {
            check(visited.add(current) && visited.size <= 64) { "Invalid comment parent chain" }
            val next = parent.get(current) ?: return id.get(current) as String?
            current = next
        }
    }
}
