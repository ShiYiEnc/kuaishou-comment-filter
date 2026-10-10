package dev.shiyi.kuaishoufilter.hook

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import dev.shiyi.kuaishoufilter.core.*
import dev.shiyi.kuaishoufilter.data.ConfigCodec
import dev.shiyi.kuaishoufilter.data.ProviderContract
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal class HostRuntime(private val context: Context) {
    private val worker = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "ks-filter-config").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())
    private val installed = ConcurrentHashMap.newKeySet<Class<*>>()
    private val sessions = Collections.synchronizedMap(WeakHashMap<Any, Session>())
    private val adapters = Collections.synchronizedMap(WeakHashMap<Any, WeakReference<Any>>())
    private val failed = AtomicBoolean()
    private var hostVersion = ""

    private class Session {
        @Volatile var config: RuleConfig? = null
        @Volatile var error = false
        @Volatile var lastRaw: List<Any?>? = null
        var allFiltered = false
        var overlay: View? = null
    }

    fun start(loader: ClassLoader) {
        val info = context.packageManager.getPackageInfo(TargetProfile.PACKAGE, 0)
        hostVersion = info.versionName.orEmpty()
        if (hostVersion != TargetProfile.VERSION_NAME || info.longVersionCode != TargetProfile.VERSION_CODE) {
            report("VERSION_MISMATCH")
            return
        }
        report("INSTALLED")
        val pluginLoader = XposedHelpers.findClass(TargetProfile.PLUGIN_LOADER, loader)
        val activation = pluginLoader.declaredMethods.single {
            it.name == "b" && it.parameterTypes.map(Class<*>::getName) == listOf(
                "com.kwai.plugin.dva.repository.model.PluginInfo", "android.app.Application",
                "java.lang.ClassLoader", "com.kwai.plugin.dva.repository.model.PluginConfig", "lhd.d",
            )
        }
        XposedBridge.hookMethod(activation, object : XC_MethodHook() {
            override fun beforeHookedMethod(call: MethodHookParam) {
                try {
                    val name = XposedHelpers.getObjectField(call.args[3], "name") as? String
                    if (name != "comment_detail") return
                    val pluginClassLoader = call.args[2] as ClassLoader
                    install(XposedHelpers.findClass(TargetProfile.FRAGMENT, pluginClassLoader))
                    install(XposedHelpers.findClass(TargetProfile.ADAPTER, pluginClassLoader))
                } catch (error: Throwable) { stopFiltering(error) }
            }
        })
    }

    @Synchronized
    private fun install(klass: Class<*>) {
        if (!installed.add(klass) || failed.get()) return
        try {
            if (klass.name == TargetProfile.ADAPTER) installAdapter(klass) else installFragment(klass)
            if (installed.any { it.name == TargetProfile.ADAPTER } && installed.any { it.name == TargetProfile.FRAGMENT }) {
                report("READY")
            }
        } catch (error: Throwable) { stopFiltering(error) }
    }

    private fun installFragment(klass: Class<*>) {
        XposedHelpers.findAndHookMethod(klass, "qs", Bundle::class.java, object : XC_MethodHook() {
            override fun beforeHookedMethod(call: MethodHookParam) { begin(call.thisObject, force = true) }
        })
        XposedHelpers.findAndHookMethod(klass, "onPageSelect", object : XC_MethodHook() {
            override fun beforeHookedMethod(call: MethodHookParam) { begin(call.thisObject) }
        })
        for (method in listOf("onPageUnSelect", "onDestroyView")) {
            XposedHelpers.findAndHookMethod(klass, method, object : XC_MethodHook() {
                override fun afterHookedMethod(call: MethodHookParam) { end(call.thisObject) }
            })
        }
    }

    private fun installAdapter(klass: Class<*>) {
        val reader = KuaishouAdapter(XposedHelpers.findClass(TargetProfile.COMMENT, klass.classLoader))
        val pageInterface = XposedHelpers.findClass(TargetProfile.PAGE_INTERFACE, klass.classLoader)
        val method = klass.getDeclaredMethod("a3", List::class.java, pageInterface)
        require(method.returnType == List::class.java)
        XposedBridge.hookAllConstructors(klass, object : XC_MethodHook() {
            override fun afterHookedMethod(call: MethodHookParam) {
                val fragment = call.args.getOrNull(2) ?: return
                adapters[call.thisObject] = WeakReference(fragment)
                begin(fragment)
            }
        })
        XposedBridge.hookMethod(method, object : XC_MethodHook() {
            override fun afterHookedMethod(call: MethodHookParam) {
                if (call.hasThrowable() || failed.get()) return
                try {
                    val fragment = adapters[call.thisObject]?.get() ?: return
                    if (!eligible(fragment)) return
                    val session = sessions[fragment] ?: return
                    val config = session.config
                    val raw = call.args[0] as? List<*> ?: return
                    session.lastRaw = raw.toList()
                    if (config == null || session.error) return
                    val display = call.result as? List<*> ?: return
                    val projection = DisplayProjector.project(reader.readList(display), RuleEngine(config))
                    session.allFiltered = projection.seen > 0 && projection.seen == projection.hidden
                    call.result = ArrayList(projection.items)
                    report(if (config.enabled) "FILTERING" else "DISABLED", config, projection)
                    main.post { emptyState(fragment, session, projection, config) }
                } catch (error: Throwable) { stopFiltering(error) }
            }
        })
        XposedHelpers.findAndHookMethod(klass, "J", object : XC_MethodHook() {
            override fun afterHookedMethod(call: MethodHookParam) {
                val fragment = adapters[call.thisObject]?.get() ?: return
                val session = sessions[fragment] ?: return
                val config = session.config ?: return
                try {
                    if (!failed.get() && config.enabled && session.allFiltered && eligible(fragment)) call.result = true
                } catch (error: Throwable) { stopFiltering(error) }
            }
        })
    }

    private fun eligible(fragment: Any): Boolean {
        if (fragment.javaClass.name != TargetProfile.FRAGMENT) return false
        val params = XposedHelpers.callMethod(fragment, "Lq") ?: return false
        if (XposedHelpers.getObjectField(params, "mQPhoto") == null) return false
        val config = XposedHelpers.callMethod(fragment, "Tq") ?: return false
        val pageConfig = XposedHelpers.getObjectField(config, "mPageListConfig") ?: return false
        return XposedHelpers.callMethod(pageConfig, "getSecondaryPanelInfo") == null
    }

    private fun begin(fragment: Any, force: Boolean = false) {
        if (failed.get()) return
        if (!force && sessions.containsKey(fragment)) return
        end(fragment)
        val session = Session()
        sessions[fragment] = session
        val reference = WeakReference(fragment)
        worker.execute {
            try {
                val bundle = context.contentResolver.call(ProviderContract.URI, ProviderContract.GET_CONFIG, null, null)
                val config = ConfigCodec.decode(requireNotNull(bundle?.getString(ProviderContract.CONFIG)))
                session.config = config
                reportNow(if (config.enabled) "READY" else "DISABLED", config)
                main.post {
                    val target = reference.get() ?: return@post
                    if (sessions[target] === session && !failed.get()) resubmit(target, session)
                }
            } catch (error: Throwable) {
                session.error = true
                android.util.Log.e("KuaishouFilter", "config ${error.javaClass.simpleName}: ${if (error is IllegalArgumentException) error.message else "unavailable"}")
                reportNow("CONFIG_ERROR", error = error)
            }
        }
    }

    private fun end(fragment: Any) {
        sessions.remove(fragment)?.overlay?.let { (it.parent as? ViewGroup)?.removeView(it) }
    }

    private fun resubmit(fragment: Any, session: Session) {
        val raw = session.lastRaw ?: return
        try {
            val adapter = XposedHelpers.callMethod(fragment, "Ji") ?: return
            XposedHelpers.callMethod(adapter, "setList", ArrayList(raw))
        } catch (error: Throwable) { stopFiltering(error) }
    }

    // Only an empty filtered list needs an explicit load action: the host's scroll loader needs comment rows.
    private fun emptyState(fragment: Any, session: Session, projection: Projection<Any?>, config: RuleConfig) {
        if (sessions[fragment] !== session || failed.get()) return
        session.overlay?.let { (it.parent as? ViewGroup)?.removeView(it) }
        session.overlay = null
        if (!config.enabled || projection.seen == 0 || projection.hidden != projection.seen) return
        try {
            val recycler = XposedHelpers.callMethod(fragment, "j0") as? View ?: return
            val parent = recycler.parent as? ViewGroup ?: return
            val page = XposedHelpers.callMethod(fragment, "n") ?: return
            val panel = LinearLayout(recycler.context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(16, 16, 16, 16)
                setBackgroundColor(android.graphics.Color.WHITE)
                addView(TextView(context).apply {
                    text = "当前评论已过滤"
                    setTextColor(android.graphics.Color.DKGRAY)
                    gravity = Gravity.CENTER
                })
                if (XposedHelpers.callMethod(page, "hasMore") == true) {
                    addView(Button(context).apply {
                        text = "加载更多"
                        setOnClickListener {
                            isEnabled = false
                            try { XposedHelpers.callMethod(page, "load") }
                            catch (error: Throwable) { stopFiltering(error) }
                            main.postDelayed({ isEnabled = true }, 1500)
                        }
                    })
                }
            }
            val params = if (parent is android.widget.FrameLayout) {
                android.widget.FrameLayout.LayoutParams(-1, -2, Gravity.CENTER)
            } else ViewGroup.LayoutParams(-1, -2)
            parent.addView(panel, params)
            session.overlay = panel
        } catch (error: Throwable) {
            stopFiltering(error)
        }
    }

    private fun stopFiltering(error: Throwable) {
        if (!failed.compareAndSet(false, true)) return
        XposedBridge.log("KuaishouFilter: hook ${error.javaClass.simpleName}")
        report("HOOK_ERROR", error = error)
        main.post {
            synchronized(sessions) {
                sessions.entries.toList().forEach { (fragment, session) ->
                    session.overlay?.let { (it.parent as? ViewGroup)?.removeView(it) }
                    session.overlay = null
                    resubmit(fragment, session)
                }
            }
        }
    }

    private fun report(state: String, config: RuleConfig? = null, projection: Projection<*>? = null, error: Throwable? = null) {
        worker.execute { reportNow(state, config, projection, error) }
    }

    private fun reportNow(state: String, config: RuleConfig? = null, projection: Projection<*>? = null, error: Throwable? = null) {
        try {
            val extras = Bundle().apply {
                putString("state", state)
                putString("hostVersion", hostVersion)
                putString("error", error?.javaClass?.simpleName.orEmpty())
                putLong("revision", config?.revision ?: 0)
                putInt("seen", projection?.seen ?: 0)
                putInt("hidden", projection?.hidden ?: 0)
                putInt("missingLocation", projection?.missingLocation ?: 0)
                putInt("male", projection?.male ?: 0)
                putInt("female", projection?.female ?: 0)
                putInt("unknownGender", projection?.unknownGender ?: 0)
            }
            context.contentResolver.call(ProviderContract.URI, ProviderContract.REPORT_STATUS, null, extras)
        } catch (error: Throwable) {
            XposedBridge.log("KuaishouFilter: provider ${error.javaClass.simpleName}: ${if (error is IllegalArgumentException) error.message else "unavailable"}")
            android.util.Log.e("KuaishouFilter", "provider ${error.javaClass.simpleName}: ${if (error is IllegalArgumentException) error.message else "unavailable"}")
        }
    }
}
