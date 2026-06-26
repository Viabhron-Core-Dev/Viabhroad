package com.example.keyboard

import android.content.Context
import org.xmlpull.v1.XmlPullParser

data class Key(
    val codes: String,
    var label: String = codes,
    var sublabel: String? = null,
    var sublabel2: String? = null,
    var longPress: String? = null,
    var widthWeight: Float = 1.0f,
    var isFunctional: Boolean = false,
    var x: Float = 0f,
    var y: Float = 0f,
    var width: Float = 0f,
    var height: Float = 0f
)

data class Row(val keys: List<Key>)

data class Keyboard(val rows: List<Row>)

class KeyboardParser(private val context: Context) {
    fun parse(xmlResId: Int): Keyboard {
        val parser = context.resources.getXml(xmlResId)
        val rows = mutableListOf<Row>()
        var currentKeys = mutableListOf<Key>()
        
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "Row" -> {
                        currentKeys = mutableListOf()
                    }
                    "Key" -> {
                        val codes = parser.getAttributeValue(null, "codes") ?: ""
                        val label = parser.getAttributeValue(null, "label") ?: codes
                        val sublabel = parser.getAttributeValue(null, "sublabel")
                        val sublabel2 = parser.getAttributeValue(null, "sublabel2")
                        val longPress = parser.getAttributeValue(null, "longPress")
                        val weightStr = parser.getAttributeValue(null, "weight")
                        val weight = weightStr?.toFloatOrNull() ?: 1.0f
                        val isFunctionalStr = parser.getAttributeValue(null, "isFunctional")
                        val isFunctional = isFunctionalStr?.toBoolean() ?: false
                        
                        currentKeys.add(Key(codes, label, sublabel, sublabel2, longPress, weight, isFunctional))
                    }
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                if (parser.name == "Row") {
                    rows.add(Row(currentKeys.toList()))
                }
            }
            eventType = parser.next()
        }
        return Keyboard(rows)
    }
}
