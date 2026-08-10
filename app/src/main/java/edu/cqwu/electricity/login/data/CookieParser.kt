package edu.cqwu.electricity.login.data

/**
 * Shared parsing for `name=value; name2=value2` cookie strings.
 */
object CookieParser {

    fun parse(cookieString: String?): Map<String, String> {
        if (cookieString.isNullOrBlank()) return emptyMap()
        val cookies = linkedMapOf<String, String>()
        cookieString
            .split(";")
            .map { it.trim() }
            .filter { it.contains("=") }
            .forEach { pair ->
                val eqIndex = pair.indexOf("=")
                val name = pair.substring(0, eqIndex).trim()
                val value = pair.substring(eqIndex + 1).trim()
                cookies[name] = value
            }
        return cookies
    }

    fun getValue(cookieString: String?, name: String): String? {
        return parse(cookieString)[name]
    }
}
