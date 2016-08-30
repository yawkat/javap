/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

/**
 * @author yawkat
 */
@native
private val ace: dynamic = noImpl

fun Editor(id: String): Editor = ace.edit(id)

@native
class Editor private constructor(DO_NOT_CALL: Nothing) {
    @native
    val commands: Commands = noImpl

    @native
    fun getSession(): Session = noImpl

    @native
    fun setReadOnly(mode: Boolean): Unit = noImpl

    @native
    fun getValue(): String = noImpl

    @native
    class Session {
        @native
        fun setMode(mode: String): Unit = noImpl

        @native
        fun setUseWrapMode(mode: Boolean): Unit = noImpl

        @native
        fun addGutterDecoration(line: Int, cssClass: String): Unit = noImpl

        @native
        fun removeGutterDecoration(line: Int, cssClass: String): Unit = noImpl
    }

    @native
    class Commands {
        @native
        fun addCommand(command: dynamic): Unit = noImpl
    }
}

private val Editor.gutterDecorations: MutableMap<Int, String>
    get() {
        if (this.asDynamic()._gutterDecorations == null) {
            this.asDynamic()._gutterDecorations = mutableMapOf<Int, String>()
        }
        return this.asDynamic()._gutterDecorations
    }


fun Editor.addGutter(line: Int, cssClass: String) {
    val old = gutterDecorations[line]
    if (old != null) {
        getSession().removeGutterDecoration(line, old)
    }

    gutterDecorations[line] = cssClass
    getSession().addGutterDecoration(line, cssClass)
}

fun Editor.clearGutter() {
    for ((line, cssClass) in gutterDecorations) {
        getSession().removeGutterDecoration(line, cssClass)
    }
    gutterDecorations.clear()
}

data class Command(
        val name: String,
        val bindKey: dynamic,
        val exec: () -> Unit,
        val readOnly: Boolean
)