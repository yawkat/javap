/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

import kotlinx.browser.document
import kotlinx.dom.addClass
import kotlinx.dom.removeClass

object Dialog {
    private val dialogElement = document.getElementById("dialog")!!
    private val messageElement = document.getElementById("dialog-message")!!
    private val titleElement = document.getElementById("dialog-title")!!

    init {
        document.getElementById("dialog-button")!!.addEventListener("click", { hide() })

        dialogElement.addEventListener("click", { evt ->
            if (evt.target == dialogElement) {
                hide()
            }
        })
    }

    fun show(title: String, message: String) {
        messageElement.textContent = message
        titleElement.textContent = title
        dialogElement.addClass("visible")
    }

    private fun hide() {
        dialogElement.removeClass("visible")
    }
}