package at.yawk.javap

/**
 * @author yawkat
 */
fun extractJavapLineMappings(javap: String): List<Pair<IntRange, IntRange>> {
    val parser = Parser(javap.split('\n'))
    parser.visit()
    return parser.mappings
}

// stdlib has this without proper nullity
@Suppress("unused")
@native
private fun String.match(regex: String): Array<String>? = noImpl

private class Parser(val lines: List<String>) {
    val mappings = mutableListOf<Pair<IntRange, IntRange>>()
    var currentLine = -1

    fun takeLine(): String? = lines.getOrNull(++currentLine)

    fun visit() {
        while (true) {
            val line = takeLine() ?: break
            if (line == "    Code:") {
                visitMethod()
            }
        }
    }

    fun visitMethod() {
        val bytecodeStart = currentLine + 1

        // javap line -> label id
        val labels = mutableMapOf<Int, Int>()

        var lntStartFound = false

        while (true) {
            val line = takeLine() ?: break

            lntStartFound = line == "      LineNumberTable:"
            if (lntStartFound || line == "      LocalVariableTable:") {
                break
            }

            val match = line.match("""^\s*(\d+): .*$""") ?: null
            if (match != null && match.size > 0) {
                labels[currentLine] = parseInt(match[1])
            }
        }

        val bytecodeEnd = currentLine

        while (!lntStartFound) {
            val line = takeLine() ?: break
            lntStartFound = line == "      LineNumberTable:"
        }

        // label id -> source line
        val lnt = mutableMapOf<Int, Int>()

        while (true) {
            val line = takeLine() ?: break

            val match = line.match("""^        line (\d+): (\d+)$""")
            if (match == null || match.size <= 0) break

            val label = parseInt(match[2])
            val sourceLine = parseInt(match[1]) - 1 // 1-indexed
            lnt[label] = sourceLine
        }

        fun flush(sourceLine: Int?, rangeStart: Int?, rangeEndExclusive: Int) {
            if (sourceLine != null && rangeStart != null) {
                mappings.add(sourceLine..sourceLine to rangeStart..rangeEndExclusive - 1)
            }
        }

        var currentRangeStart: Int? = null
        var currentSourceLine: Int? = null

        for (i in bytecodeStart..bytecodeEnd - 1) {
            val sourceLine = lnt[labels[i]]
            if (sourceLine != null) {
                flush(currentSourceLine, currentRangeStart, i)
                currentSourceLine = sourceLine
                currentRangeStart = i
            }
        }
        flush(currentSourceLine, currentRangeStart, bytecodeEnd)
    }
}