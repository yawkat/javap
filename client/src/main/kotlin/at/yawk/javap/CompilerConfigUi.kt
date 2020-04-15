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
import kotlin.browser.document
import kotlin.dom.addClass
import kotlin.dom.appendElement
import kotlin.dom.appendText
import kotlin.dom.removeClass

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
                is ConfigProperty.Choice -> {
                }
                is ConfigProperty.Flag -> {
                    handlers[property] = FlagHandler(property)
                }
            }
        }
        handlers[ConfigProperties.javaLint] = JavaLintHandler()
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
            is Sdk.Ecj -> "java -jar ecj.jar"
            is Sdk.KotlinJar, is Sdk.KotlinDistribution -> "kotlinc"
            is Sdk.Scala -> "scalac"
        }
        if (sdk is Sdk.OpenJdk) {
            if (getHandler(ConfigProperties.lombok).finalValue) {
                s += " -cp lombok.jar"
            }
        } else if (sdk is Sdk.Ecj) {
            if (getHandler(ConfigProperties.lombok).finalValue) {
                s += " -javaagent:lombok.jar=ECJ"
            }
        }
        s += ConfigProperties.validateAndBuildCommandLine(sdk, buildConfig()).joinToString(" ",
                prefix = " ",
                postfix = " ")
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

    private class FlagHandler(property: ConfigProperty.Flag)
        : SingleElementPropertyHandler<Boolean, ConfigProperty.Flag>(property) {

        lateinit var checkbox: HTMLInputElement

        override fun init(wrapper: Element) {
            label = wrapper.appendElement("label") {
                require(this is HTMLLabelElement)
                checkbox = appendElement("input") {
                    require(this is HTMLInputElement)
                    type = "checkbox"
                    addEventListener("change", {
                        this@FlagHandler.value = checkbox.checked
                        changedInDisplay()
                        afterEvent()
                    })
                } as HTMLInputElement
                appendText(property.displayName)
            } as HTMLLabelElement

            val enableDependsOn = property.enableDependsOn
            if (enableDependsOn != null) {
                addInterdependenceListener(enableDependsOn) { enable ->
                    overrideWithDefault = !enable
                    checkbox.disabled = overrideWithDefault
                    changedInDisplay()
                }
            }
        }

        override fun applyValueToDisplay() {
            checkbox.checked = value
        }
    }

    private class JavaLintHandler : PropertyHandler<Set<String>?, ConfigProperty<Set<String>?>>(
            ConfigProperties.javaLint) {

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
                val supportedWarnings = (sdk as Sdk.Java).supportedWarnings
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