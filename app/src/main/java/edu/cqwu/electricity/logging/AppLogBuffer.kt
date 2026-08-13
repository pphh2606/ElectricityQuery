package edu.cqwu.electricity.logging

/**
 * Bounded in-memory log buffer used by feedback export.
 */
object AppLogBuffer {
    private const val MAX_LINES = 1000
    private const val MAX_CHARS = 256 * 1024

    private val lock = Any()
    private val entries = ArrayDeque<String>()
    private var totalChars = 0

    fun append(line: String) {
        val bounded = if (line.length > MAX_CHARS) line.take(MAX_CHARS) else line
        synchronized(lock) {
            entries.addLast(bounded)
            totalChars += bounded.length
            while (entries.size > MAX_LINES || (entries.size > 1 && totalChars > MAX_CHARS)) {
                totalChars -= entries.removeFirst().length
            }
        }
    }

    fun dump(maxLines: Int = MAX_LINES): String = synchronized(lock) {
        entries.takeLast(maxLines.coerceIn(1, MAX_LINES)).joinToString("\n")
    }
}
