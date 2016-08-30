/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

import at.yawk.javap.model.Paste
import at.yawk.javap.model.ProcessingInput
import jquery.jq
import kotlin.browser.window

/**
 * @author yawkat
 */
var context: PasteContext? = null

class PasteContext(var currentPaste: Paste) {
    fun showCurrentPasteOutput(type: OutputType) {
        jq("body").toggleClass("compile-error", type == OutputType.compilerLog)
        val text = type.getter(currentPaste.output)!!
        setEditorValue(Editors.resultEditor, text)

        clearMappingColors()
        if (type == OutputType.javap) {
            colorizeJavap(text)
        }
        if (!currentPaste.id.matches("^default:.*$")) {
            var path = currentPaste.id
            if (type != OutputType.javap && (type != OutputType.compilerLog || currentPaste.output.javap != null)) {
                path += "/" + type
            }
            window.location.hash = path
        }
    }

    fun displayPaste(requestedOutputType: OutputType? = null) {
        setEditorValue(Editors.codeEditor, currentPaste.input.code)
        jq("#compiler-names").find("option").each {
            val tgt = jq(this)
            tgt.attr("selected", (tgt.`val`() == currentPaste.input.compilerName))
        }
        val outputType = jq("#output-type")
        outputType.find("option").each {
            val option = jq(this)
            val type = OutputType.valueOf(option.`val`()!!)
            val enabled = !type.getter(currentPaste.output).isNullOrBlank()
            option.attr("disabled", !enabled)
        }

        if (requestedOutputType != null) {
            outputType.find(":selected").attr("selected", false)
            outputType.find("[value=$requestedOutputType]").attr("selected", true)
        }
        var selected = outputType.find(":selected")
        if (selected.attrGen("disabled")) {
            selected.attr("selected", false)
            selected = outputType.find(":enabled").first()
            selected.attr("selected", true)
        }
        showCurrentPasteOutput(OutputType.valueOf(selected.`val`()!!))
    }

    fun colorizeJavap(text: String) {
        showMappingColors(extractJavapLineMappings(text))
    }

    fun showMappingColors(mappings: List<Pair<IntRange, IntRange>>) {
        var colorIndex = 0
        for ((codeLines, resultLines) in mappings) {
            val cssClass = "line-color-$colorIndex"
            codeLines.forEach { Editors.codeEditor.addGutter(it, cssClass) }
            resultLines.forEach { Editors.resultEditor.addGutter(it, cssClass) }

            colorIndex = (colorIndex + 1) % 8
        }
    }

    fun clearMappingColors() {
        Editors.codeEditor.clearGutter()
        Editors.resultEditor.clearGutter()
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