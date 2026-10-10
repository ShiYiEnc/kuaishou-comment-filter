package dev.shiyi.kuaishoufilter.data

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.shiyi.kuaishoufilter.core.*
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConfigDeviceTest {
    @Test fun persistenceAndProviderRoundTrip() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = ConfigStore(context)
        val original = store.load()
        try {
            val next = store.save(RuleConfig(
                enabled = true,
                blacklist = RuleList(keywords = setOf("test-word"), genders = setOf(Gender.MALE)),
                whitelist = RuleList(users = listOf(UserRule("test-user", "test-note")), genders = setOf(Gender.FEMALE, Gender.UNKNOWN)),
            ))
            assertEquals(next, ConfigStore(context).load())
            val bundle = context.contentResolver.call(ProviderContract.URI, ProviderContract.GET_CONFIG, null, null)
            assertEquals(next, ConfigCodec.decode(requireNotNull(bundle?.getString(ProviderContract.CONFIG))))
        } finally {
            store.save(original)
        }
    }
}
