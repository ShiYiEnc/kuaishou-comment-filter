package dev.shiyi.kuaishoufilter.hook

import org.junit.Assert.*
import org.junit.Test
import dev.shiyi.kuaishoufilter.core.Gender

class FixtureUser {
    @JvmField var mId: String? = "user"
    @JvmField var mSex: String? = null
}

class FixtureComment {
    @JvmField var mId: String? = "root"
    @JvmField var mAuthorId: String? = "user"
    @JvmField var mComment: String? = "text"
    @JvmField var mAuthorArea: String? = "广东省"
    @JvmField var mType: Int = 1
    @JvmField var mParent: FixtureComment? = null
    @JvmField var mUser: FixtureUser? = null
}

class KuaishouAdapterTest {
    private val adapter = KuaishouAdapter(FixtureComment::class.java)

    @Test fun readsRootAndResolvesDescendantsWithoutMutatingThem() {
        val root = FixtureComment()
        val reply = FixtureComment().apply { mId = "reply"; mParent = root }
        val nested = FixtureComment().apply { mId = "nested"; mParent = reply }
        val rows = adapter.readList(listOf("header", root, reply, nested))
        assertNull(rows[0].rootComment)
        assertEquals("广东", rows[1].rootComment?.normalizedLocation)
        assertNull(rows[2].rootComment)
        assertEquals("root", rows[3].parentRootId)
        assertSame(root, reply.mParent)
    }

    @Test fun syntheticCommentRowsAreNotFilteredAsRoots() {
        val synthetic = FixtureComment().apply { mType = 5 }
        assertNull(adapter.readList(listOf(synthetic)).single().rootComment)
    }

    @Test(expected = IllegalStateException::class)
    fun cyclicParentsAbortProjection() {
        val item = FixtureComment().apply { mParent = this }
        adapter.readList(listOf(item))
    }

    @Test fun readsOnlyVerifiedGenderValuesOfTheCommentAuthor() {
        val values = listOf("M" to Gender.MALE, "F" to Gender.FEMALE, "U" to Gender.UNKNOWN,
            null to Gender.UNKNOWN, "" to Gender.UNKNOWN, "1" to Gender.UNKNOWN, "m" to Gender.UNKNOWN)
        for ((raw, expected) in values) {
            val comment = FixtureComment().apply { mUser = FixtureUser().apply { mSex = raw } }
            assertEquals(expected, adapter.readList(listOf(comment)).single().rootComment?.gender)
        }
        val wrongUser = FixtureComment().apply { mUser = FixtureUser().apply { mId = "other-user"; mSex = "M" } }
        assertEquals(Gender.UNKNOWN, adapter.readList(listOf(wrongUser)).single().rootComment?.gender)
        assertEquals(Gender.UNKNOWN, adapter.readList(listOf(FixtureComment())).single().rootComment?.gender)
    }
}
