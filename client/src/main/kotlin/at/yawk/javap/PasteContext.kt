/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

import at.yawk.javap.model.Paste
import at.yawk.javap.model.ProcessingInput
import org.w3c.dom.Element
import kotlin.browser.document
import kotlin.browser.window
import kotlin.dom.addClass
import kotlin.dom.appendElement
import kotlin.dom.appendText
import kotlin.dom.clear
import kotlin.dom.createElement
import kotlin.dom.removeClass
import kotlin.js.json

/**
 * @author yawkat
 */
var context: PasteContext? = null

private const val COLOR_COUNT = 8

class PasteContext(var currentPaste: Paste) {
    fun fork() {
        currentPaste = currentPaste.copy(editable = false)
    }

    fun showCurrentPasteOutput(type: OutputType) {
        if (!currentPaste.id.matches("^default:.*$")) {
            var path = currentPaste.id
            if (type != OutputType.javap) {
                path += "/" + type
            }
            window.location.hash = path
        }

        Editors.codeEditor.clearGutter()
        val toolOutput = document.getElementById("tool-output")!!
        toolOutput.clear()
        when (type) {
            OutputType.javap -> {
                val lines = (currentPaste.output.javap ?: "").split('\n')
                val highlighter = Highlighter("ace/mode/java")
                val metadata = extractJavapMetadata(lines)

                var currentFile: Element = toolOutput
                val currentExpanders = mutableListOf<Pair<Element, ExpanderRegion>>()
                var currentContent: Element? = null

                var currentLineColorIndex = 0

                for ((i, line) in lines.withIndex()) {
                    var lineColorClass: String? = null
                    while (currentLineColorIndex < metadata.lineMappings.size) {
                        val mapping = metadata.lineMappings[currentLineColorIndex]
                        if (mapping.javapLines.last < i) {
                            currentLineColorIndex++
                        } else {
                            if (i in mapping.javapLines) {
                                val cssClass = "line-color-${currentLineColorIndex % COLOR_COUNT}"
                                if (i == mapping.javapLines.first) {
                                    mapping.sourceLines.forEach { Editors.codeEditor.addGutter(it, cssClass) }
                                }
                                lineColorClass = cssClass
                            }
                            break
                        }
                    }

                    // start file if necessary
                    metadata.fileStarts.entries.find { it.value == i }?.let { (fileName, _) ->
                        currentExpanders.clear()
                        currentFile = toolOutput.appendElement("details") {
                            className = "output-file"
                            setAttribute("open", "true")
                            appendElement("summary") { textContent = fileName }
                        }
                        currentContent = null
                    }
                    // end expander region if necessary
                    while (currentExpanders.isNotEmpty() && currentExpanders.last().second.range.last < i) {
                        currentExpanders.removeAt(currentExpanders.lastIndex)
                        currentContent = null
                    }
                    val regionStart = metadata.regions.find { it.range.first == i }
                    val lowest = currentExpanders.lastOrNull()?.first ?: currentFile
                    val writeTextTo: Element = if (regionStart != null) {
                        // start expander region and write the current line to the summary of the element
                        val details = lowest.appendElement("details") {
                            className = "output-expander-region"
                            if (regionStart.openByDefault) setAttribute("open", "true")
                        }
                        currentContent = null
                        currentExpanders.add(details to regionStart)
                        details.appendElement("summary") {}
                                .appendElement("code") {}
                                .appendElement("pre") { className = Highlighter.WRAPPER_CLASS }
                    } else {
                        if (currentContent == null) {
                            currentContent = lowest
                                    .appendElement("code") {}
                                    .appendElement("pre") { className = Highlighter.WRAPPER_CLASS }
                        } else {
                            currentContent!!.appendText("\n")
                        }
                        currentContent!!
                    }
                    writeTextTo.append(lineElement(i, additionalClass = lineColorClass))
                    highlighter.printLine(writeTextTo, line)
                }
            }
            OutputType.procyon -> {
                toolOutput.appendElement("code") {
                    appendElement("pre") {
                        className = Highlighter.WRAPPER_CLASS
                        val highlighter = Highlighter("ace/mode/java")
                        val lines: List<String> = (currentPaste.output.procyon ?: "").split('\n')
                        for ((index, line) in lines.withIndex()) {
                            appendChild(lineElement(index + 0))
                            highlighter.printLine(this, line)
                            appendText("\n")
                        }
                    }
                }
            }
        }
    }

    private fun lineElement(line: Int, additionalClass: String? = null) = document.createElement("span") {
        val lineString = (line + 1).toString()
        className = "line"
        setAttribute("data-line", lineString)
        if (additionalClass != null) addClass(additionalClass)
    }

    fun displayPaste(requestedOutputType: OutputType? = null) {
        setEditorValue(Editors.codeEditor, currentPaste.input.code)
        jq("#compiler-names").find("option").each {
            val tgt = jq(jsThis)
            tgt.attr("selected", (tgt.`val`() == currentPaste.input.compilerName))
        }
        val outputType = jq("#output-type")

        if (requestedOutputType != null) {
            outputType.find(":selected").attr("selected", false)
            outputType.find("[value=$requestedOutputType]").attr("selected", true)
        }
        val selected = outputType.find(":selected")
        val compilerLog = document.getElementById("compiler-log")!!
        val compilerLogContent = compilerLog.querySelector("pre")!!
        compilerLogContent.clear()
        if (currentPaste.output.compilerLog != "") {
            compilerLog.removeClass("hide")
            val lines: List<String> = currentPaste.output.compilerLog.split('\n')
            for ((index, line) in lines.withIndex()) {
                compilerLogContent.appendChild(lineElement(index + 0))
                compilerLogContent.appendText(line + '\n')
            }

            if (currentPaste.output.javap == null) {
                // compile error
                compilerLog.setAttribute("open", "true")
                compilerLog.setAttribute("data-log-type", "error")
            } else {
                compilerLog.setAttribute("data-log-type", "warning")
            }
        } else {
            compilerLog.addClass("hide")
        }
        showCurrentPasteOutput(OutputType.valueOf(selected.`val`()!!))
    }

    fun triggerCompile() {
        jq("body").addClass("compiling")
        ajax(Request(
                method = if (currentPaste.editable) "PUT" else "POST",
                url = "/api/paste" + (if (currentPaste.editable) "/${currentPaste.id}" else ""),
                contentType = "application/json; charset=utf-8",
                data = JSON.stringify(json("input" to ProcessingInput(
                        code = Editors.codeEditor.getValue(),
                        compilerName = jq("#compiler-names").`val`()!!
                )))
        )).then({
            val s = PasteContext(Paste.fromJson(it))
            context = s
            s.displayPaste()
        }, handleError).always {
            jq("body").removeClass("compiling")
        }
    }
}