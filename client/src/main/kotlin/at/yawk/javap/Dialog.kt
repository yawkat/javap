/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

import kotlinx.browser.document
import kotlin.js.json

/**
 * @author yawkat
 */
fun showDialog(title: String, message: String) {
    val dialog = document.getElementById("dialog-message")!!
    dialog.setAttribute("title", title)
    dialog.textContent = message
    js("$").dialog(json(
            "modal" to true,
            "buttons" to json(
                    "Ok" to {
                        js("this").dialog("close")
                    }
            )
    ))
}