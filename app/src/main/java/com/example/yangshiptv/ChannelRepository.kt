package com.example.yangshiptv

/**
 * The 48 channels: CCTV-1 .. CCTV-17 (17) + 31 provincial satellite TVs = 48.
 *
 * [aliases] are best-effort text fragments used by the injected script to find each
 * channel's clickable control on the yangshipin page. If the live page uses different
 * wording, edit these; the script matches by normalized visible text, so small
 * differences (spaces, "CCTV-1" vs "CCTV1") are handled.
 */
object ChannelRepository {

    val channels: List<Channel> = build()

    private fun build(): List<Channel> {
        val list = mutableListOf<Channel>()

        // ---- CCTV 1..17 ----
        val cctv = listOf(
            "综合" to listOf("CCTV-1", "CCTV1", "央视一套", "综合频道", "综合"),
            "财经" to listOf("CCTV-2", "CCTV2", "央视二套", "财经频道", "财经"),
            "综艺" to listOf("CCTV-3", "CCTV3", "央视三套", "综艺频道", "综艺"),
            "中文国际" to listOf("CCTV-4", "CCTV4", "中文国际", "国际频道"),
            "体育" to listOf("CCTV-5", "CCTV5", "央视五套", "体育频道", "体育"),
            "电影" to listOf("CCTV-6", "CCTV6", "央视六套", "电影频道", "电影"),
            "国防军事" to listOf("CCTV-7", "CCTV7", "国防军事", "军事频道"),
            "电视剧" to listOf("CCTV-8", "CCTV8", "央视八套", "电视剧频道", "电视剧"),
            "纪录" to listOf("CCTV-9", "CCTV9", "央视九套", "纪录频道", "纪录片"),
            "科教" to listOf("CCTV-10", "CCTV10", "央视十套", "科教频道", "科教"),
            "戏曲" to listOf("CCTV-11", "CCTV11", "央视十一套", "戏曲频道", "戏曲"),
            "社会与法" to listOf("CCTV-12", "CCTV12", "社会与法", "法治频道"),
            "新闻" to listOf("CCTV-13", "CCTV13", "新闻频道", "央视新闻"),
            "少儿" to listOf("CCTV-14", "CCTV14", "少儿频道", "央视少儿"),
            "音乐" to listOf("CCTV-15", "CCTV15", "音乐频道", "央视音乐"),
            "奥林匹克" to listOf("CCTV-16", "CCTV16", "奥林匹克频道", "奥运频道"),
            "农业农村" to listOf("CCTV-17", "CCTV17", "农业农村频道", "农业频道", "乡村频道")
        )
        cctv.forEachIndexed { idx, (cn, aliases) ->
            val n = idx + 1
            list.add(
                Channel(
                    number = n,
                    id = "cctv$n",
                    name = "CCTV-$n $cn",
                    shortName = "CCTV-$n",
                    aliases = aliases,
                    fallbackUrl = "https://tv.cctv.com/live/cctv$n/"
                )
            )
        }

        // ---- 31 provincial satellite TVs (numbers 18..48) ----
        val provincial = listOf(
            "北京" to listOf("北京卫视", "北京", "BTV"),
            "天津" to listOf("天津卫视", "天津"),
            "河北" to listOf("河北卫视", "河北"),
            "山西" to listOf("山西卫视", "山西"),
            "内蒙古" to listOf("内蒙古卫视", "内蒙古"),
            "辽宁" to listOf("辽宁卫视", "辽宁"),
            "吉林" to listOf("吉林卫视", "吉林"),
            "黑龙江" to listOf("黑龙江卫视", "黑龙江"),
            "上海东方" to listOf("东方卫视", "上海卫视", "上海东方卫视", "东方", "上海"),
            "江苏" to listOf("江苏卫视", "江苏"),
            "浙江" to listOf("浙江卫视", "浙江"),
            "安徽" to listOf("安徽卫视", "安徽"),
            "福建" to listOf("福建卫视", "福建"),
            "江西" to listOf("江西卫视", "江西"),
            "山东" to listOf("山东卫视", "山东"),
            "河南" to listOf("河南卫视", "河南"),
            "湖北" to listOf("湖北卫视", "湖北"),
            "湖南" to listOf("湖南卫视", "湖南"),
            "广东" to listOf("广东卫视", "广东", "大湾区卫视"),
            "广西" to listOf("广西卫视", "广西"),
            "海南" to listOf("海南卫视", "海南"),
            "重庆" to listOf("重庆卫视", "重庆"),
            "四川" to listOf("四川卫视", "四川"),
            "贵州" to listOf("贵州卫视", "贵州"),
            "云南" to listOf("云南卫视", "云南"),
            "西藏" to listOf("西藏卫视", "西藏"),
            "陕西" to listOf("陕西卫视", "陕西"),
            "甘肃" to listOf("甘肃卫视", "甘肃"),
            "青海" to listOf("青海卫视", "青海"),
            "宁夏" to listOf("宁夏卫视", "宁夏"),
            "新疆" to listOf("新疆卫视", "新疆")
        )
        provincial.forEachIndexed { idx, (cn, aliases) ->
            val n = 18 + idx
            val display = if (cn == "上海东方") "上海东方卫视" else "${cn}卫视"
            list.add(
                Channel(
                    number = n,
                    id = "ws_$cn",
                    name = display,
                    shortName = display,
                    aliases = aliases,
                    fallbackUrl = "https://tv.cctv.com/live/"
                )
            )
        }

        return list
    }
}
