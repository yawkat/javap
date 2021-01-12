/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

import at.yawk.javap.model.CompilerConfiguration
import at.yawk.javap.model.ConfigProperties
import at.yawk.javap.model.ConfigProperty
import org.w3c.dom.Element
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLLabelElement
import org.w3c.dom.HTMLOptionElement
import org.w3c.dom.HTMLSelectElement
import kotlinx.browser.document
import kotlinx.dom.addClass
import kotlinx.dom.appendElement
import kotlinx.dom.appendText
import kotlinx.dom.clear
import kotlinx.dom.removeClass

object CompilerConfigUi {
    private val compilerCommandLine = document.getElementById("compiler-command-line")!!

    private lateinit var handlers: Map<ConfigProperty<*>, PropertyHandler<*, *>>
    private var rebuildCommandLine = false

    private val sdk: Sdk
        get() = SdkSelector.selectedSdk

    fun init() {
        val allProperties = ConfigProperties.properties.values.flatten()
        val handlers = mutableMapOf<ConfigProperty<*>, PropertyHandler<*, *>>()
        for (property in allProperties) {
            when (property) {
                is ConfigProperty.Special -> {
                }
                is ConfigProperty.RangeChoice ->
                    handlers[property] = NumberHandler(property)
                is ConfigProperty.Choice ->
                    handlers[property] = SelectHandler(property)
                is ConfigProperty.Flag ->
                    handlers[property] = FlagHandler(property)
            }
        }
        handlers[ConfigProperties.lint] = LintHandler()
        this.handlers = handlers

        val grid = document.getElementById("compiler-options-grid")!!
        for (value in handlers.values) {
            value.init(grid)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T, P : ConfigProperty<T>> getHandler(property: P): PropertyHandler<T, out P> =
            handlers.getValue(property) as PropertyHandler<T, out P>

    fun buildConfig(): CompilerConfiguration {
        val map = mutableMapOf<String, Any?>()
        for (handler in handlers.values) {
            if (handler.visibleForSdk) {
                map[handler.property.id] = handler.finalValue
            }
        }
        return map
    }

    fun updateSdk() {
        for (handler in handlers.values) {
            handler.updateSdk()
        }
        updateCompilerCommandLine()
    }

    fun updatePaste() {
        context?.let {
            val currentPaste = it.currentPaste
            for (handler in handlers.values) {
                if (handler.visibleForSdk) {
                    handler.load(currentPaste.input.compilerConfiguration)
                }
            }
        }
        updateCompilerCommandLine()
    }

    private fun afterEvent() {
        if (rebuildCommandLine) {
            updateCompilerCommandLine()
        }
    }

    private fun updateCompilerCommandLine() {
        rebuildCommandLine = false
        var s = when (sdk) {
            is Sdk.OpenJdk -> "javac"
            is Sdk.Ecj -> "java"
            is Sdk.KotlinJar, is Sdk.KotlinDistribution -> "kotlinc"
            is Sdk.Scala -> "scalac"
        }
        if (sdk is Sdk.OpenJdk) {
            if (getHandler(ConfigProperties.lombok).finalValue) {
                s += " -cp lombok.jar"
            }
        } else if (sdk is Sdk.Ecj) {
            val lombok = getHandler(ConfigProperties.lombok).finalValue
            if (lombok) { s += " -javaagent:lombok.jar=ECJ" }
            s += " -jar ecj.jar"
            if (lombok) { s += " -cp lombok.jar" }
        }
        val options = ConfigProperties.validateAndBuildCommandLine(sdk, buildConfig())
        if (options.isNotEmpty()) {
            s += options.joinToString(" ", prefix = " ")
        }
        s += " "
        s += sdk.language.fileName
        compilerCommandLine.textContent = s
    }

    private inline fun <T, R> addInterdependenceListener(
            interdependency: ConfigProperty.Interdependency<T, R>,
            crossinline f: (R) -> Unit) {
        val handler = getHandler(interdependency.dependsOn)
        handler.addListener {
            val r = interdependency.function(sdk, handler.finalValue)
            f(r)
        }
    }

    private abstract class PropertyHandler<T, P : ConfigProperty<T>>(val property: P) {
        protected var value: T = property.default
        protected var overrideWithDefault = false

        var visibleForSdk: Boolean = false
            private set

        private var directListeners: List<(T) -> Unit> = emptyList()

        val finalValue: T
            get() =
                if (overrideWithDefault) property.default
                else value

        open fun init(wrapper: Element) {
        }

        open fun updateSdk() {
            visibleForSdk = property.canApplyTo(sdk)
            applyVisibleToDisplay()
        }

        fun load(config: CompilerConfiguration) {
            if (visibleForSdk) {
                value = property.get(config)
                applyValueToDisplay()
                invokeDirectListeners()
            }
        }

        fun addListener(listener: (T) -> Unit) {
            directListeners += listener
        }

        protected open fun applyValueToDisplay() {}
        protected open fun applyVisibleToDisplay() {}

        protected fun changedInDisplay() {
            rebuildCommandLine = true
            invokeDirectListeners()
        }

        private fun invokeDirectListeners() {
            for (directListener in directListeners) {
                directListener(value)
            }
        }
    }

    private abstract class SingleElementPropertyHandler<T, P : ConfigProperty<T>>(property: P)
        : PropertyHandler<T, P>(property) {
        lateinit var label: HTMLLabelElement

        override fun applyVisibleToDisplay() {
            super.applyVisibleToDisplay()
            if (visibleForSdk) {
                label.removeClass("hide")
            } else {
                label.addClass("hide")
            }
        }
    }

    private abstract class InputHandler<T, P : ConfigProperty<T>>(property: P) :
            SingleElementPropertyHandler<T, P>(property) {
        lateinit var input: HTMLInputElement

        override fun init(wrapper: Element) {
            label = wrapper.appendElement("label") {
                require(this is HTMLLabelElement)
                input = appendElement("input") {
                    require(this is HTMLInputElement)
                    addEventListener("change", {
                        valueFromDisplay() // this@FlagHandler.value = checkbox.checked
                        changedInDisplay()
                        afterEvent()
                    })
                } as HTMLInputElement
                appendText(displayName())
            } as HTMLLabelElement

            val enableDependsOn = property.enableDependsOn
            if (enableDependsOn != null) {
                addInterdependenceListener(enableDependsOn) { enable ->
                    overrideWithDefault = !enable
                    input.disabled = overrideWithDefault
                    changedInDisplay()
                }
            }
        }

        abstract fun valueFromDisplay()
        abstract fun displayName(): String
    }

    private class FlagHandler(property: ConfigProperty.Flag)
        : InputHandler<Boolean, ConfigProperty.Flag>(property) {
        override fun init(wrapper: Element) {
            super.init(wrapper)
            input.type = "checkbox"
        }

        override fun applyValueToDisplay() {
            input.checked = value
        }

        override fun valueFromDisplay() {
            this.value = input.checked
        }

        override fun displayName() = property.displayName
    }

    private class NumberHandler(property: ConfigProperty.RangeChoice)
        : InputHandler<Int?, ConfigProperty.RangeChoice>(property) {
        override fun init(wrapper: Element) {
            super.init(wrapper)
            input.type = "number"
        }

        override fun applyValueToDisplay() {
            input.value = value?.toString() ?: ""
        }

        override fun valueFromDisplay() {
            val trimmed = input.value.trim()
            this.value = if (trimmed.isEmpty()) null else trimmed.toInt()
        }

        override fun displayName() = property.name

        override fun updateSdk() {
            super.updateSdk()
            if (visibleForSdk) {
                val range = property.getRange(sdk)
                input.min = range.first.toString()
                input.max = range.last.toString()
                input.size = input.max.length
            }
        }
    }

    private class SelectHandler<T>(property: ConfigProperty.Choice<T>) :
            SingleElementPropertyHandler<T, ConfigProperty.Choice<T>>(property) {
        lateinit var select: HTMLSelectElement

        private lateinit var choices: Map<String, T>

        override fun init(wrapper: Element) {
            super.init(wrapper)

            label = wrapper.appendElement("label") {
                require(this is HTMLLabelElement)
                select = appendElement("select") {
                    require(this is HTMLSelectElement)
                    addEventListener("change", {
                        this@SelectHandler.value = choices.getValue(value)
                        changedInDisplay()
                        afterEvent()
                    })
                } as HTMLSelectElement
                appendText(property.id)
            } as HTMLLabelElement

            val choicesDependOn = property.choicesDependOn
            if (choicesDependOn != null) {
                addInterdependenceListener(choicesDependOn) {
                    this.choices = it
                    updateChoices()
                }
            }

            val enableDependsOn = property.enableDependsOn
            if (enableDependsOn != null) {
                addInterdependenceListener(enableDependsOn) { enable ->
                    overrideWithDefault = !enable
                    select.disabled = overrideWithDefault
                    changedInDisplay()
                }
            }
        }

        override fun updateSdk() {
            super.updateSdk()
            if (property.choicesDependOn == null && visibleForSdk) {
                choices = property.getChoices(sdk)
                updateChoices()
            }
        }

        private fun updateChoices() {
            select.clear()
            for (name in choices.keys) {
                select.appendElement("option") {
                    require(this is HTMLOptionElement)
                    textContent = name
                    value = name
                }
            }
            applyValueToDisplay()
        }

        override fun applyValueToDisplay() {
            val selected = choices.entries.find { it.value == this.value }
            if (selected != null) {
                this.select.value = selected.key
            }
        }
    }

    private class LintHandler : PropertyHandler<Set<String>?, ConfigProperty<Set<String>?>>(
            ConfigProperties.lint) {

        private lateinit var label: HTMLLabelElement
        private lateinit var customWarningsCheckbox: HTMLInputElement
        private lateinit var warningsWrapper: Element

        private lateinit var warningElements: List<WarningElement>

        private fun changeMainToggle() {
            overrideWithDefault = !customWarningsCheckbox.checked
            if (value == null) value = emptySet()
        }

        override fun updateSdk() {
            super.updateSdk()
            if (this.visibleForSdk) {
                val supportedWarnings = (sdk as Sdk.HasLint).supportedWarnings
                for (warningElement in warningElements) {
                    warningElement.applicableToSdk = warningElement.warning in supportedWarnings
                    if (warningElement.applicableToSdk) {
                        warningElement.label.removeClass("hide")
                    } else {
                        warningElement.label.addClass("hide")
                    }
                }
            }
        }

        override fun applyValueToDisplay() {
            super.applyValueToDisplay()
            updateVisibility()
            val value = value
            if (value != null) {
                for (warningElement in warningElements) {
                    if (warningElement.applicableToSdk) {
                        warningElement.checkbox.checked = warningElement.warning in value
                    }
                }
            }
        }

        override fun applyVisibleToDisplay() {
            super.applyVisibleToDisplay()
            updateVisibility()
        }

        private fun updateVisibility() {
            if (!visibleForSdk || finalValue == null) {
                warningsWrapper.addClass("hide")
            } else {
                warningsWrapper.removeClass("hide")
            }
            if (visibleForSdk) {
                label.removeClass("hide")
            } else {
                label.addClass("hide")
            }
        }

        override fun init(wrapper: Element) {
            super.init(wrapper)
            warningsWrapper = document.getElementById("compiler-options-lint")!!
            label = wrapper.appendElement("label") {
                require(this is HTMLLabelElement)
                customWarningsCheckbox = appendElement("input") {
                    require(this is HTMLInputElement)
                    type = "checkbox"
                    addEventListener("change", {
                        changeMainToggle()
                        updateVisibility()
                        changedInDisplay()
                        afterEvent()
                    })
                } as HTMLInputElement
                appendText("Custom Warnings (")
                appendElement("a") {
                    require(this is HTMLAnchorElement)
                    textContent = "all"
                    href = "#"
                    onclick = {
                        value = Sdks.allSupportedWarnings
                        customWarningsCheckbox.checked = true
                        changeMainToggle()
                        applyValueToDisplay()
                        changedInDisplay()
                        afterEvent()
                        false
                    }
                }
                appendText(", ")
                appendElement("a") {
                    require(this is HTMLAnchorElement)
                    textContent = "none"
                    href = "#"
                    onclick = {
                        value = emptySet()
                        customWarningsCheckbox.checked = true
                        changeMainToggle()
                        applyValueToDisplay()
                        changedInDisplay()
                        afterEvent()
                        false
                    }
                }
                appendText(")")
            } as HTMLLabelElement
            warningElements = Sdks.allSupportedWarnings.map { WarningElement(it) }
            for (warningElement in warningElements) {
                warningElement.init(warningsWrapper)
            }
        }

        private inner class WarningElement(val warning: String) {
            var applicableToSdk = false

            lateinit var label: HTMLLabelElement
            lateinit var checkbox: HTMLInputElement

            fun init(wrapper: Element) {
                label = wrapper.appendElement("label") {
                    checkbox = appendElement("input") {} as HTMLInputElement
                    checkbox.type = "checkbox"
                    checkbox.addEventListener("change", {
                        value = if (checkbox.checked) value!! + warning else value!! - warning
                        changedInDisplay()
                        afterEvent()
                    })
                    appendText(warning)
                } as HTMLLabelElement
            }
        }
    }
}