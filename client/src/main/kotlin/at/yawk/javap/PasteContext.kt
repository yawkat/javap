/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

import at.yawk.javap.model.PasteDto
import at.yawk.javap.model.ProcessingInput
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import org.w3c.dom.Element
import org.w3c.dom.HTMLOptionElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.asList
import org.w3c.dom.get
import kotlin.browser.document
import kotlin.browser.window
import kotlin.dom.addClass
import kotlin.dom.appendElement
import kotlin.dom.appendText
import kotlin.dom.clear
import kotlin.dom.createElement
import kotlin.dom.removeClass

/**
 * @author yawkat
 */
var context: PasteContext? = null

private const val COLOR_COUNT = 8

class PasteContext(var currentPaste: PasteDto) {
    fun fork() {
        currentPaste = currentPaste.copy(editable = false)
    }

    fun showCurrentPasteOutput(type: OutputType) {
        if (!currentPaste.id.matches("^default:.*$")) {
            var path = currentPaste.id
            if (type != OutputType.javap) {
                path += "/$type"
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
                                var cssClass = "line-color-${currentLineColorIndex % COLOR_COUNT}"
                                for (sourceLine in mapping.sourceLines) {
                                    val existingGutter = Editors.codeEditor.getGutter(sourceLine)
                                    // if the source line already has a gutter color, use that.
                                    // this isn't perfect, but it should work in most cases.
                                    // see [javap#7]
                                    if (existingGutter != null) {
                                        cssClass = existingGutter
                                    }
                                    Editors.codeEditor.addGutter(sourceLine, cssClass)
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

    private fun hasOutputOfType(outputType: OutputType) =
            when (outputType) {
                OutputType.javap -> currentPaste.output.javap
                OutputType.procyon -> currentPaste.output.procyon
            } != null

    fun displayPaste(requestedOutputType: OutputType? = null) {
        setEditorValue(Editors.codeEditor, currentPaste.input.code)
        SdkSelector.selectedSdkName = currentPaste.input.compilerName
        val outputType = document.getElementById("output-type") as HTMLSelectElement

        for (option in outputType.options.asList()) {
            require(option is HTMLOptionElement)
            val type = OutputType.valueOf(option.value)
            if (type == requestedOutputType) option.selected = true
            option.disabled = !hasOutputOfType(type)
        }

        // show tool output iff there are any outputs to select
        val toolOutputWrapper = document.getElementById("tool-output-wrapper")!!
        if (OutputType.values().any { hasOutputOfType(it) }) {
            toolOutputWrapper.removeClass("hide")
        } else {
            toolOutputWrapper.addClass("hide")
        }

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

        val selected = outputType.selectedOptions[0] as HTMLOptionElement
        showCurrentPasteOutput(OutputType.valueOf(selected.value))
    }

    fun triggerCompile() {
        document.body!!.addClass("compiling")
        Editors.codeEditor.setReadOnly(true)
        Ajax.postPut(
                method = if (currentPaste.editable) "PUT" else "POST",
                url = "/api/paste" + (if (currentPaste.editable) "/${currentPaste.id}" else ""),
                // technically we should send a create or update here, but they have the same structure
                data = PasteDto.Create(ProcessingInput(
                        code = Editors.codeEditor.getValue(),
                        compilerName = SdkSelector.selectedSdkName
                )),
                inStrategy = PasteDto.Create.serializer(),
                outStrategy = PasteDto.serializer(),
                onSuccess = {
                    val s = PasteContext(it)
                    context = s
                    s.displayPaste()
                },
                always = {
                    document.body!!.removeClass("compiling")
                    Editors.codeEditor.setReadOnly(false)
                }
        )
    }
}