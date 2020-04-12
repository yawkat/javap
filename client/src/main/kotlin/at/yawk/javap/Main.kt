/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

import at.yawk.javap.model.Paste
import org.w3c.dom.HTMLSelectElement
import kotlin.Pair
import kotlin.browser.document
import kotlin.browser.window

/**
 * @author yawkat
 */
@Suppress("unused")
fun start() {
    Editors.start()

    SdkManager.loadSdks {
        loadPasteFromHash()
    }

    document.getElementById("compile")!!.addEventListener("click", { context?.triggerCompile() })
    document.getElementById("fork")!!.addEventListener("click", {
        context?.fork()
        context?.triggerCompile()
    })
    val outputType = document.getElementById("output-type")!! as HTMLSelectElement
    outputType.addEventListener("click", {
        context?.showCurrentPasteOutput(OutputType.valueOf(outputType.value))
    })

    window.addEventListener("hashchange", {
        loadPasteFromHash()
    }, false)

    js("$(document).tooltip()")
}

fun setEditorValue(editor: dynamic, text: String) {
    val selection = editor.selection.getRange()
    editor.setValue(text)
    editor.selection.setRange(selection)
}

private fun loadPasteFromHash() {
    val hash = window.location.hash
    var pasteId = (if (hash.isNotEmpty()) hash else "#default:JAVA").substring(1)
    val outputType: OutputType?
    if (pasteId.contains('/')) {
        outputType = OutputType.valueOf(pasteId.substring(pasteId.indexOf('/') + 1))
        pasteId = pasteId.substring(0, pasteId.indexOf('/'))
    } else {
        outputType = null
    }
    loadPaste(pasteId, outputType)
}

fun loadPaste(name: String, outputType: OutputType?, forceCompiler: String? = null) {
    Editors.codeEditor.setReadOnly(true)
    ajax(Request(
            method = "GET",
            url = "/api/paste/" + name
    )).then({
        val data = Paste.fromJson(it)
        val s = PasteContext(if (forceCompiler != null) {
            data.copy(input = data.input.copy(compilerName = forceCompiler))
        } else data)
        context = s
        s.displayPaste(outputType)
    }, handleError).always {
        Editors.codeEditor.setReadOnly(false)
    }
}

object Editors {
    lateinit var codeEditor: Editor

    fun setLanguage(sdkLanguage: SdkLanguage) {
        codeEditor.getSession().setMode(when (sdkLanguage) {
            SdkLanguage.JAVA -> "ace/mode/java"
            SdkLanguage.KOTLIN -> "ace/mode/kotlin"
            SdkLanguage.SCALA -> "ace/mode/scala"
        })
    }

    fun start() {
        codeEditor = Editor("code-editor")

        codeEditor.commands.addCommand(Command(
                name = "trigger compile ctrl-enter",
                bindKey = jsMap(
                        "win" to "Ctrl-Enter",
                        "mac" to "Command-Enter"
                ),
                exec = { context?.triggerCompile() },
                readOnly = false
        ))
        codeEditor.commands.addCommand(Command(
                name = "trigger compile ctrl-s",
                bindKey = jsMap(
                        "win" to "Ctrl-S",
                        "mac" to "Command-S"
                ),
                exec = { context?.triggerCompile() },
                readOnly = false
        ))
    }
}

fun jsMap(vararg elements: Pair<String, Any>) {
    val o = js("Object()")
    for ((k, v) in elements) {
        o[k] = v
    }
    return o
}