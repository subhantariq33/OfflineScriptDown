

package com.mugames.vidsnapkit

import org.json.JSONObject


/**
 * @author Udhaya
 * Created on 10-02-2023
 */

interface XMLParser{
    fun xmlToJsonObject(xmlString: String): JSONObject
}

expect object XMLParserFactory{
    fun createParserFactory(): XMLParser
}