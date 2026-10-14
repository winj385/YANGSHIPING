package com.example.yangshiptv

/**
 * One TV channel.
 *
 * @param number       1..48, shown big on the channel-switch mask.
 * @param id           stable id (not shown to user).
 * @param name         full display name, e.g. "CCTV-5 体育".
 * @param shortName    short label, e.g. "CCTV-5".
 * @param aliases      text fragments used to locate the channel's switch control
 *                     inside the loaded web page (matched by normalized text, not by
 *                     class name, because the page's markup changes often).
 * @param fallbackUrl  backup source loaded if the primary source shows no picture
 *                     within 20 seconds.
 */
data class Channel(
    val number: Int,
    val id: String,
    val name: String,
    val shortName: String,
    val aliases: List<String>,
    val fallbackUrl: String
) {
    /** Current program title, scraped from the page. Empty until known. */
    var nowPlaying: String = ""
}
