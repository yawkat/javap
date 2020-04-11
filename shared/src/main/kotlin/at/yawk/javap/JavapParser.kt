/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

import kotlin.math.exp

// In the shared module for easier testing - can just write testng and run on jvm

fun extractJavapMetadata(lines: List<String>): JavapMetadata {
    val parser = Parser(lines)
    parser.visit()
    return JavapMetadata(parser.mappings, parser.fileStarts, parser.regions)
}

data class JavapMetadata(
        val lineMappings: List<LineMapping>,
        val fileStarts: Map<String, Int>,
        val regions: List<ExpanderRegion>
)

data class LineMapping(
        val sourceLines: IntRange,
        val javapLines: IntRange
)

data class ExpanderRegion(
        val range: IntRange,
        val openByDefault: Boolean = true
)

private val jdk13ClassFilePattern = "Classfile .+/(.+\\.class)".toRegex()

private class Parser(val lines: List<String>) {
    val mappings = mutableListOf<LineMapping>()
    val fileStarts = mutableMapOf<String, Int>()
    val regions = mutableListOf<ExpanderRegion>()

    var currentLine = -1

    fun takeLine(): String? = lines.getOrNull(++currentLine)

    fun visit() {
        // first pass: file names, lines
        while (true) {
            val line = takeLine() ?: break
            if (line == "    Code:") {
                visitMethod()
            }
            // jdk 13+
            jdk13ClassFilePattern.matchEntire(line)?.let { result ->
                fileStarts[result.groups[1]!!.value] = currentLine
            }
            // pre jdk 13
            if (line.startsWith("Compiled from \"")) {
                val declarationLine = lines[currentLine + 1]
                val classFileName = declarationLine.substring(declarationLine.lastIndexOf(' ') + 1)
                fileStarts["$classFileName.class"] = currentLine
            }
        }
        // second pass: regions
        currentLine = -1
        // first: region indent, second: region data
        val regionStack = mutableListOf<Pair<Int, ExpanderRegion>>()
        while (true) {
            val line = takeLine() ?: break
            // count leading spaces
            val trimmed = line.trimStart()
            val indent = line.length - trimmed.length
            while (regionStack.isNotEmpty()) {
                val (expectedIndent, prototype) = regionStack.last()
                if (expectedIndent >= indent) {
                    // region done! fix the end index and then register it.
                    regionStack.removeAt(regionStack.lastIndex)
                    regions.add(prototype.copy(range = prototype.range.first until currentLine))
                } else {
                    break
                }
            }

            if (trimmed == "Code:" || trimmed == "LocalVariableTable:") {
                regionStack.add(indent to ExpanderRegion(currentLine..currentLine, openByDefault = true))
            } else if (trimmed == "LineNumberTable:") {
                regionStack.add(indent to ExpanderRegion(currentLine..currentLine, openByDefault = false))
            }

            if (line == "Constant pool:") {
                val start = currentLine
                @Suppress("ControlFlowWithEmptyBody")
                while ((takeLine() ?: break) != "{") {}
                regions.add(ExpanderRegion(start until currentLine, openByDefault = false))
            }
        }
    }

    private val labelPattern = """^\s*(\d+): .*$""".toRegex()
    private val linePattern = """^ {8}line (\d+): (\d+)$""".toRegex()

    private fun visitMethod() {
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

            val match = labelPattern.matchEntire(line)
            if (match != null) {
                labels[currentLine] = match.groupValues[1].toInt()
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

            val match = linePattern.matchEntire(line) ?: break

            val label = match.groupValues[2].toInt()
            val sourceLine = match.groupValues[1].toInt() - 1 // 1-indexed
            lnt[label] = sourceLine
        }

        fun flush(sourceLine: Int?, rangeStart: Int?, rangeEndExclusive: Int) {
            if (sourceLine != null && rangeStart != null) {
                mappings.add(LineMapping(
                        sourceLine..sourceLine,
                        rangeStart until rangeEndExclusive
                ))
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