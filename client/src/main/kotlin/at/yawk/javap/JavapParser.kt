/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

/**
 * @author yawkat
 */
fun extractJavapLineMappings(javap: String): List<Pair<IntRange, IntRange>> {
    val parser = Parser(javap.split('\n'))
    parser.visit()
    return parser.mappings
}

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
            if (line == "") {
                // no lnt nor lvt
                return
            }

            val match = line.match("""^\s*(\d+): .*$""")
            if (match != null && match.isNotEmpty()) {
                labels[currentLine] = match[1].toInt()
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
            if (match == null || match.isEmpty()) break

            val label = match[2].toInt()
            val sourceLine = match[1].toInt() - 1 // 1-indexed
            lnt[label] = sourceLine
        }

        fun flush(sourceLine: Int?, rangeStart: Int?, rangeEndExclusive: Int) {
            if (sourceLine != null && rangeStart != null) {
                mappings.add(sourceLine..sourceLine to (rangeStart until rangeEndExclusive))
            }
        }

        var currentRangeStart: Int? = null
        var currentSourceLine: Int? = null

        for (i in bytecodeStart until bytecodeEnd) {
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