/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */


import org.supercsv.io.CsvMapWriter
import org.supercsv.prefs.CsvPreference
import org.xml.sax.Attributes
import org.xml.sax.SAXParseException
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import java.io.FileNotFoundException
import javax.xml.parsers.SAXParserFactory

class ExtractLocalizationToCSV(private val resourceDir: File, private val exportDir: File) : Runnable {
    companion object {
        const val ENGLISH = "en"
    }

    private val allKeys = sortedSetOf<String>()
    private val parsedMaps = mutableMapOf<String, Map<String, String>>()

    override fun run() {
        val frenchFiles = arrayOf("values-fr/strings.xml")
        val germanFiles = arrayOf("values-de/strings.xml")
        val englishFiles = arrayOf("values/strings.xml", "values/prefs.xml", "values/tips.xml", "values/exception_strings.xml", "values/about_strings.xml")
        val languageFileMap = mapOf(ENGLISH to englishFiles, "fr" to frenchFiles, "de" to germanFiles)

        exportDir.mkdirs()

        val factory = SAXParserFactory.newInstance()
        val parser = factory.newSAXParser()
        languageFileMap.entries.forEach { (languageKey, languageFileArray) ->
            val languageMap = mutableMapOf<String, String>()
            languageFileArray.forEach { filename ->
                val file = File(resourceDir, filename)
                if (!file.exists()) throw FileNotFoundException(file.toString())

                val handler = SAXLocalStringResource()
                parser.parse(file, handler)
                allKeys.addAll(handler.strings.keys)
                languageMap.putAll(handler.strings)
            }
            parsedMaps[languageKey] = languageMap
        }

        languageFileMap.keys.filter { it != ENGLISH }.forEach {
            writeLanguage(it)
        }
    }

    private fun writeLanguage(languageKey: String) {
        val englishLanguageMap = checkNotNull(parsedMaps[ENGLISH])
        val languageMap = checkNotNull(parsedMaps[languageKey])

        val csvExport = File(exportDir, "localization-$languageKey.csv")

        csvExport.writer().use {
            val row = mutableMapOf<String, String>()
            val prefs = CsvPreference.Builder(CsvPreference.STANDARD_PREFERENCE)

            CsvMapWriter(it, prefs.build()).use { csvWriter ->
                // string key, english, translation
                allKeys.forEach { stringKey ->
                    row["key"] = stringKey
                    row["english"] = getText(englishLanguageMap, stringKey)
                    row["text"] = getText(languageMap, stringKey)
                    csvWriter.write(row, *row.keys.toTypedArray())
                }

            }
        }
    }

    private fun getText(languageMap: Map<String, String>, stringKey: String): String {
        var text = languageMap[stringKey] ?: ""
        text = text.replace("\\'", "'")
        return text
    }

    class SAXLocalStringResource : DefaultHandler() {
        enum class ElementType { STRING, STRING_ARRAY, STRING_ARRAY_ITEM }

        val strings: MutableMap<String, String> = mutableMapOf()
        private val data = StringBuffer()
        private var name: String? = null
        private var inElementType: ElementType? = null
        private var dataIndex = 0

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
            if (qName == "string") {
                name = checkNotNull(attributes?.getValue("name")) {
                    "expected name attribute in string"
                }

                val translatable = (attributes?.getValue("translatable") ?: "true").toBoolean()
                if (!translatable) return

                data.setLength(0)
                inElementType = ElementType.STRING
            } else if (qName == "string-array") {
                name = checkNotNull(attributes?.getValue("name")) {
                    "expected name attribute in string-array"
                }

                val translatable = (attributes?.getValue("translatable") ?: "true").toBoolean()
                if (!translatable) return

                dataIndex = 0
                inElementType = ElementType.STRING_ARRAY
            } else if (qName == "item" && inElementType == ElementType.STRING_ARRAY) {
                data.setLength(0)
                inElementType = ElementType.STRING_ARRAY_ITEM
                dataIndex++
            }
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            if (qName == "string" && inElementType == ElementType.STRING && name != null) {
                strings[name!!] = data.toString()
                data.setLength(0)
                inElementType = null
            } else if (qName == "string-array" && inElementType == ElementType.STRING_ARRAY && name != null) {
                inElementType = null
            } else if (qName == "item" && inElementType == ElementType.STRING_ARRAY_ITEM) {
                strings["${name}_$dataIndex"] = data.toString()
                data.setLength(0)
                inElementType = ElementType.STRING_ARRAY
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (inElementType != null) {
                data.append(ch, start, length)
            }
        }

        override fun skippedEntity(name: String?) {
        }

        override fun warning(e: SAXParseException?) {
            e?.printStackTrace()
        }

        override fun error(e: SAXParseException?) {
            e?.printStackTrace()
        }

        override fun fatalError(e: SAXParseException?) {
            e?.printStackTrace()
        }
    }
}