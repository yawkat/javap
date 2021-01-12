package at.yawk.javap

import org.w3c.dom.Element
import kotlinx.dom.appendElement
import kotlinx.dom.appendText

class Highlighter(@Suppress("UNUSED_PARAMETER") mode: String) {
    companion object {
        const val WRAPPER_CLASS = "ace-tm"
    }

    @Suppress("JoinDeclarationAndAssignment")
    private val tokenizer: dynamic
    private var state = "start"

    init {
        tokenizer = js("new (window.require(mode).Mode)()").getTokenizer()
    }

    fun printLine(element: Element, line: String) {
        val data = tokenizer.getLineTokens(line, state)
        state = data.state
        for (token in data.tokens) {
            val type = token.type as String
            val value = token.value as String
            if (type == "text") {
                element.appendText(value)
            } else {
                element.appendElement("span") {
                    className = type.split('.').joinToString(" ") { "ace_$it" }
                    textContent = value
                }
            }
        }
    }


}