

package com.mugames.vidsnapkit




interface HtmlDecoder{
    fun decodeHtml(string: String): String
}

expect object HtmlDecoderFactory{
    fun createDecoderFactory(): HtmlDecoder
}