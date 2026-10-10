package dev.shiyi.kuaishoufilter.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import dev.shiyi.kuaishoufilter.R
import dev.shiyi.kuaishoufilter.core.*
import dev.shiyi.kuaishoufilter.data.ConfigStore
import java.text.DateFormat
import java.util.Date

class MainActivity : Activity() {
    private lateinit var store: ConfigStore
    private var config = RuleConfig()
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private val ink = Color.rgb(34, 39, 44)
    private val muted = Color.rgb(99, 109, 118)
    private val accent = Color.rgb(0, 121, 107)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ConfigStore(this)
        try { config = store.load() } catch (error: Exception) {
            showError(error)
        }
        render()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) refreshStatus()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()

    private fun text(value: String, size: Float = 14f, color: Int = ink): TextView = TextView(this).apply {
        this.text = value
        textSize = size
        setTextColor(color)
        setPadding(0, dp(6), 0, dp(6))
    }

    private fun render() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(246, 247, 249))
            isFillViewport = true
        }
        val frame = FrameLayout(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(32))
        }
        val width = resources.displayMetrics.widthPixels.coerceAtMost(dp(720))
        frame.addView(content, FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
        scroll.addView(frame)
        setContentView(scroll)

        val heading = row()
        heading.addView(ImageView(this).apply { setImageResource(R.drawable.ic_filter) }, LinearLayout.LayoutParams(dp(40), dp(40)))
        heading.addView(text("快手评论过滤", 23f).apply { setPadding(dp(12), 0, 0, 0) }, LinearLayout.LayoutParams(0, dp(48), 1f))
        content.addView(heading)
        content.addView(text("14.8.40.50567  ·  视频一级评论", 13f, muted))

        section("过滤设置")
        content.addView(Switch(this).apply {
            text = "启用过滤"
            isChecked = config.enabled
            minHeight = dp(48)
            setOnCheckedChangeListener { _, enabled -> save(config.copy(enabled = enabled)) }
        })
        content.addView(text("使用名单", 13f, muted))
        content.addView(radioGroup(listOf("黑名单", "白名单"), config.selectedList.ordinal) { index ->
            val kind = ListKind.entries[index]
            if (kind != config.selectedList) save(config.select(kind))
        })
        content.addView(text("处理方式", 13f, muted))
        content.addView(radioGroup(listOf("隐藏命中项", "仅显示命中项"), config.action.ordinal) { index ->
            val action = MatchAction.entries[index]
            if (action != config.action) save(config.copy(action = action))
        })
        content.addView(text("生效时间：重新打开评论区", 13f, muted))
        if (config.activeRules.isEmpty) {
            val emptyState = if (config.action == MatchAction.SHOW_MATCHED) "名单为空：启用后将不显示一级评论" else "名单为空：所有一级评论均保留"
            content.addView(text(emptyState, 14f, if (config.action == MatchAction.SHOW_MATCHED) Color.rgb(167, 65, 36) else muted))
        }

        section("${if (config.selectedList == ListKind.BLACKLIST) "黑名单" else "白名单"}规则")
        listHeading("IP 属地", config.activeRules.locations.size) { locationDialog() }
        for (name in config.activeRules.locations.sorted()) {
            ruleRow(name, null, { locationInput(name) }) {
                updateRules { it.copy(locations = it.locations - name) }
            }
        }
        if (config.activeRules.locations.isEmpty()) content.addView(text("暂无属地", 13f, muted))

        listHeading("关键词", config.activeRules.keywords.size) { keywordDialog() }
        for (keyword in config.activeRules.keywords.sorted()) {
            ruleRow(keyword, null, { keywordDialog(keyword) }) {
                updateRules { it.copy(keywords = it.keywords - keyword) }
            }
        }
        if (config.activeRules.keywords.isEmpty()) content.addView(text("暂无关键词", 13f, muted))

        listHeading("用户", config.activeRules.users.size) { userDialog() }
        for (user in config.activeRules.users) {
            ruleRow(user.id, user.note.takeIf { it.isNotEmpty() }, { userDialog(user) }) {
                updateRules { it.copy(users = it.users.filterNot { entry -> entry.id == user.id }) }
            }
        }
        if (config.activeRules.users.isEmpty()) content.addView(text("暂无用户", 13f, muted))

        listHeading("作者性别", config.activeRules.genders.size) { genderDialog() }
        for (gender in config.activeRules.genders.sortedBy { it.ordinal }) {
            ruleRow(genderLabel(gender), null, { genderDialog() }) {
                updateRules { it.copy(genders = it.genders - gender) }
            }
        }
        if (config.activeRules.genders.isEmpty()) content.addView(text("暂无性别条件", 13f, muted))

        section("运行状态")
        status = text("", 14f, muted)
        content.addView(status)
        val statusRow = row()
        statusRow.addView(text("最近一次评论会话", 13f, muted), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        statusRow.addView(icon(android.R.drawable.ic_popup_sync, "刷新状态") { refreshStatus() })
        content.addView(statusRow)
        refreshStatus()
    }

    private fun row() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun section(title: String) {
        content.addView(View(this).apply { setBackgroundColor(Color.rgb(222, 226, 231)) }, LinearLayout.LayoutParams(-1, dp(1)).apply {
            topMargin = dp(20)
            bottomMargin = dp(12)
        })
        content.addView(text(title, 17f))
    }

    private fun radioGroup(labels: List<String>, selected: Int, onSelect: (Int) -> Unit): RadioGroup {
        val ids = labels.map { View.generateViewId() }
        return RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            labels.forEachIndexed { index, label ->
                addView(RadioButton(this@MainActivity).apply {
                    id = ids[index]
                    text = label
                    textSize = 14f
                    minHeight = dp(48)
                }, RadioGroup.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
            check(ids[selected])
            setOnCheckedChangeListener { _, id -> ids.indexOf(id).takeIf { it >= 0 }?.let(onSelect) }
        }
    }

    private fun icon(resource: Int, description: String, action: () -> Unit) = ImageButton(this).apply {
        setImageResource(resource)
        imageTintList = android.content.res.ColorStateList.valueOf(accent)
        contentDescription = description
        tooltipText = description
        val attrs = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackgroundBorderless))
        background = attrs.getDrawable(0)
        attrs.recycle()
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        setOnClickListener { action() }
    }

    private fun listHeading(title: String, count: Int, add: () -> Unit) {
        val row = row()
        row.addView(text("$title  ($count)", 15f), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(icon(android.R.drawable.ic_input_add, "添加$title", add))
        content.addView(row)
    }

    private fun ruleRow(value: String, note: String?, edit: () -> Unit, remove: () -> Unit) {
        val row = row()
        val label = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(value))
            note?.let { addView(text(it, 12f, muted)) }
        }
        row.addView(label, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(icon(android.R.drawable.ic_menu_edit, "编辑 $value", edit))
        row.addView(icon(android.R.drawable.ic_menu_delete, "删除 $value", remove))
        content.addView(row)
    }

    private fun input(hint: String, value: String = "") = EditText(this).apply {
        this.hint = hint
        setText(value)
        textSize = 16f
        inputType = InputType.TYPE_CLASS_TEXT
        isSingleLine = true
        selectAll()
    }

    private fun dialogBody(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), dp(8), dp(24), dp(8))
        views.forEach { addView(it, LinearLayout.LayoutParams(-1, -2)) }
    }

    private fun validatedDialog(title: String, body: View, primary: EditText, save: () -> Boolean) {
        val dialog = AlertDialog.Builder(this).setTitle(title).setView(body)
            .setNegativeButton("取消", null).setPositiveButton("保存", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (save()) dialog.dismiss() else primary.error = "请输入有效内容"
            }
        }
        dialog.show()
    }

    private fun locationDialog() {
        AlertDialog.Builder(this).setTitle("添加属地")
            .setItems(arrayOf("中国省级地区", "自定义国家／地区")) { _, index ->
                if (index == 0) provinceDialog() else locationInput()
            }.setNegativeButton("取消", null).show()
    }

    private fun provinceDialog() {
        val names = LocationNames.provinces
        val selected = names.map { it in config.activeRules.locations }.toBooleanArray()
        AlertDialog.Builder(this).setTitle("中国省级地区")
            .setMultiChoiceItems(names.toTypedArray(), selected) { _, index, checked -> selected[index] = checked }
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                val customs = config.activeRules.locations - names.toSet()
                updateRules { it.copy(locations = customs + names.filterIndexed { index, _ -> selected[index] }) }
            }.show()
    }

    private fun locationInput(old: String? = null) {
        val value = input("国家／地区名称", old.orEmpty())
        validatedDialog("属地", dialogBody(value), value) {
            val name = LocationNames.normalize(value.text.toString()) ?: return@validatedDialog false
            updateRules { it.copy(locations = (it.locations - setOfNotNull(old)) + name) }
        }
    }

    private fun keywordDialog(old: String? = null) {
        val value = input("关键词", old.orEmpty())
        validatedDialog("关键词", dialogBody(value), value) {
            val keyword = value.text.toString().trim().takeIf { it.isNotEmpty() } ?: return@validatedDialog false
            updateRules { it.copy(keywords = (it.keywords - setOfNotNull(old)) + keyword) }
        }
    }

    private fun genderLabel(gender: Gender): String = when (gender) {
        Gender.MALE -> "男"
        Gender.FEMALE -> "女"
        Gender.UNKNOWN -> "未知"
    }

    private fun genderDialog() {
        val genders = Gender.entries
        val selected = genders.map { it in config.activeRules.genders }.toBooleanArray()
        AlertDialog.Builder(this).setTitle("作者性别")
            .setMultiChoiceItems(genders.map(::genderLabel).toTypedArray(), selected) { _, index, checked -> selected[index] = checked }
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                updateRules { it.copy(genders = genders.filterIndexed { index, _ -> selected[index] }.toSet()) }
            }.show()
    }

    private fun userDialog(old: UserRule? = null) {
        val id = input("用户 ID", old?.id.orEmpty())
        val note = input("昵称备注（可选）", old?.note.orEmpty())
        validatedDialog("用户", dialogBody(id, note), id) {
            val userId = id.text.toString().trim().takeIf { it.isNotEmpty() } ?: return@validatedDialog false
            val entry = UserRule(userId, note.text.toString().trim())
            updateRules { rules -> rules.copy(users = rules.users.filterNot { it.id == old?.id || it.id == userId } + entry) }
        }
    }

    private fun updateRules(change: (RuleList) -> RuleList): Boolean = save(config.withActiveRules(change(config.activeRules)))

    private fun save(next: RuleConfig): Boolean = try {
        config = store.save(next)
        render()
        true
    } catch (error: Exception) {
        showError(error)
        render()
        false
    }

    private fun showError(error: Exception) {
        AlertDialog.Builder(this).setTitle("配置未保存")
            .setMessage(error.javaClass.simpleName).setPositiveButton("确定", null).show()
    }

    private fun refreshStatus() {
        val prefs = getSharedPreferences("diagnostics", Context.MODE_PRIVATE)
        val state = when (prefs.getString("state", "")) {
            "INSTALLED" -> "模块已加载，等待打开视频评论"
            "READY" -> "适配已加载，等待评论数据"
            "FILTERING" -> "过滤运行中"
            "DISABLED" -> "过滤已关闭"
            "VERSION_MISMATCH" -> "快手版本不匹配，已保留全部评论"
            "HOOK_ERROR" -> "适配失败，已停止过滤"
            "CONFIG_ERROR" -> "配置读取失败，已保留全部评论"
            else -> "尚未收到模块运行记录"
        }
        val time = prefs.getLong("updatedAt", 0)
        val detail = if (time == 0L) "" else "\n快手 ${prefs.getString("hostVersion", "")}\n读取 ${prefs.getInt("seen", 0)} 条 · 隐藏 ${prefs.getInt("hidden", 0)} 条 · 缺失属地 ${prefs.getInt("missingLocation", 0)} 条\n配置版本 ${prefs.getLong("revision", 0)} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(time))}"
        val error = prefs.getString("error", "").orEmpty()
        val genderCounts = when {
            time == 0L -> ""
            !prefs.contains("unknownGender") -> "\n性别统计尚无记录"
            else -> "\n性别：男 ${prefs.getInt("male", 0)} 条 · 女 ${prefs.getInt("female", 0)} 条 · 未知 ${prefs.getInt("unknownGender", 0)} 条"
        }
        status.text = state + detail + genderCounts + if (error.isEmpty()) "" else "\n$error"
    }
}
