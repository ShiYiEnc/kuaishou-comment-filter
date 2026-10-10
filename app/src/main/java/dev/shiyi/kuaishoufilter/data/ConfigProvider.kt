package dev.shiyi.kuaishoufilter.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process

object ProviderContract {
    const val AUTHORITY = "dev.shiyi.kuaishoufilter.config"
    val URI: Uri = Uri.parse("content://$AUTHORITY")
    const val TARGET = "com.smile.gifmaker"
    const val GET_CONFIG = "get_config"
    const val REPORT_STATUS = "report_status"
    const val CONFIG = "config"
    val states = setOf("INSTALLED", "READY", "FILTERING", "DISABLED", "VERSION_MISMATCH", "HOOK_ERROR", "CONFIG_ERROR")
}

class ConfigProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    private fun checkCaller() {
        val uid = Binder.getCallingUid()
        if (uid == Process.myUid()) return
        val packages = requireNotNull(context).packageManager.getPackagesForUid(uid).orEmpty()
        if (ProviderContract.TARGET !in packages) throw SecurityException("Caller not allowed")
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        checkCaller()
        val ctx = requireNotNull(context)
        return when (method) {
            ProviderContract.GET_CONFIG -> Bundle().apply {
                putString(ProviderContract.CONFIG, ConfigCodec.encode(ConfigStore(ctx).load()))
            }
            ProviderContract.REPORT_STATUS -> {
                val input = requireNotNull(extras)
                val state = requireNotNull(input.getString("state"))
                require(state in ProviderContract.states)
                val prefs = ctx.getSharedPreferences("diagnostics", 0)
                prefs.edit()
                    .putString("state", state)
                    .putString("hostVersion", input.getString("hostVersion").orEmpty().take(64))
                    .putString("error", input.getString("error").orEmpty().take(120))
                    .putLong("revision", input.getLong("revision").coerceAtLeast(0))
                    .putInt("seen", input.getInt("seen").coerceAtLeast(0))
                    .putInt("hidden", input.getInt("hidden").coerceAtLeast(0))
                    .putInt("missingLocation", input.getInt("missingLocation").coerceAtLeast(0))
                    .putInt("male", input.getInt("male").coerceAtLeast(0))
                    .putInt("female", input.getInt("female").coerceAtLeast(0))
                    .putInt("unknownGender", input.getInt("unknownGender").coerceAtLeast(0))
                    .putLong("updatedAt", System.currentTimeMillis())
                    .apply()
                Bundle()
            }
            else -> throw IllegalArgumentException("Unknown method")
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = throw UnsupportedOperationException()
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
}
