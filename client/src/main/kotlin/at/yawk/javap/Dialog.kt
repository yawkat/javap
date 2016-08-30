package at.yawk.javap

import jquery.jq
import jquery.ui.dialog
import org.w3c.dom.Element

/**
 * @author yawkat
 */
@native("this")
private val jsThis: Element = noImpl

fun showDialog(title: String, message: String) {
    val dialog = jq("#dialog-message")
    dialog.attr("title", title)
    dialog.text(message)
    dialog.dialog(json(
            "modal" to true,
            "buttons" to json(
                    "Ok" to {
                        jq(jsThis).dialog("close")
                    }
            )
    ))
}