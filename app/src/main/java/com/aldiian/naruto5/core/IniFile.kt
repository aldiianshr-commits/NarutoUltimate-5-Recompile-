package com.aldiian.naruto5.core

/**
 * Minimal editor for the core's SimpleIni-format settings file (PCSX2-Android.ini), used to
 * place folder paths before the core reads them in NativeApp.initialize. Everything not touched
 * is preserved byte-for-byte (line-wise).
 */
object IniFile {
    fun update(text: String, section: String, values: Map<String, String>): String {
        val lines = text.lines().toMutableList()
        if (lines.isNotEmpty() && lines.last().isEmpty()) lines.removeAt(lines.lastIndex)
        val header = "[$section]"
        var start = lines.indexOfFirst { it.trim().equals(header, ignoreCase = true) }
        if (start < 0) {
            if (lines.isNotEmpty() && lines.last().isNotBlank()) lines += ""
            lines += header
            start = lines.lastIndex
        }
        var end = start + 1
        while (end < lines.size && !lines[end].trim().startsWith("[")) end++
        val pending = values.toMutableMap()
        for (i in start + 1 until end) {
            val l = lines[i]
            val eq = l.indexOf('=')
            if (eq <= 0 || l.trimStart().startsWith(";") || l.trimStart().startsWith("#")) continue
            val key = l.substring(0, eq).trim()
            pending.remove(key)?.let { v -> lines[i] = "$key = $v" }
        }
        // Insert missing keys right after the last non-blank line of the section.
        var insertAt = end
        while (insertAt > start + 1 && lines[insertAt - 1].isBlank()) insertAt--
        pending.forEach { (k, v) -> lines.add(insertAt++, "$k = $v") }
        return lines.joinToString("\n", postfix = "\n")
    }
}
