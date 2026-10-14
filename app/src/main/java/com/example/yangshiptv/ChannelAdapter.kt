package com.example.yangshiptv

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView

/**
 * List adapter for the left channel panel. Highlights the currently playing channel.
 */
class ChannelAdapter(private val channels: List<Channel>) : BaseAdapter() {

    var currentIndex = 0

    override fun getCount(): Int = channels.size
    override fun getItem(position: Int): Any = channels[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val ctx = parent.context
        val tv = (convertView as? TextView) ?: TextView(ctx).apply {
            textSize = 22f
            setPadding(24, 20, 24, 20)
        }
        val ch = channels[position]
        val now = if (ch.nowPlaying.isNotBlank()) "\n${ch.nowPlaying}" else ""
        tv.text = String.format("%02d  %s%s", ch.number, ch.name, now)
        tv.setTextColor(Color.WHITE)
        tv.setBackgroundColor(
            if (position == currentIndex) Color.parseColor("#33FFFFFF")
            else Color.TRANSPARENT
        )
        return tv
    }
}
