package dev.shiyi.kuaishoufilter.core

object LocationNames {
    val provinces = listOf(
        "北京", "天津", "河北", "山西", "内蒙古", "辽宁", "吉林", "黑龙江", "上海", "江苏", "浙江",
        "安徽", "福建", "江西", "山东", "河南", "湖北", "湖南", "广东", "广西", "海南", "重庆", "四川",
        "贵州", "云南", "西藏", "陕西", "甘肃", "青海", "宁夏", "新疆", "台湾", "香港", "澳门",
    )

    // Administrative names are equivalent labels; no city-to-province or IP inference is performed.
    private val aliases = buildMap {
        provinces.filterNot { it in setOf("北京", "天津", "上海", "重庆", "内蒙古", "广西", "西藏", "宁夏", "新疆", "香港", "澳门") }
            .forEach { put("${it}省", it) }
        listOf("北京", "天津", "上海", "重庆").forEach { put("${it}市", it) }
        put("内蒙古自治区", "内蒙古")
        put("广西壮族自治区", "广西")
        put("西藏自治区", "西藏")
        put("宁夏回族自治区", "宁夏")
        put("新疆维吾尔自治区", "新疆")
        put("香港特别行政区", "香港")
        put("澳门特别行政区", "澳门")
    }

    fun normalize(raw: String?): String? {
        val name = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return aliases[name] ?: name
    }
}
