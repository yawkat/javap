/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.events.Event

/**
 * @author yawkat
 */
@JsName("$")
external fun jq(doc: Document): JQuery
@JsName("$")
external fun jq(doc: String): JQuery
@JsName("$")
external fun jq(doc: Element): JQuery

external class JQuery {
    fun find(selector: String): JQuery

    fun each(action: (Element) -> Unit): JQuery

    fun empty(): Unit

    fun first(): JQuery

    fun `val`(string: String?): Unit

    fun `val`(): String?

    fun data(key: String, value: Any?): Unit

    fun <T> data(key: String): T

    fun attr(key: String, value: Any?): Unit

    @JsName("attr")
    fun <T> attrGen(key: String): T

    fun append(jQuery: JQuery): Unit

    fun toggleClass(clazz: String, value: Boolean? = definedExternally): Unit

    fun text(text: String): JQuery
    fun addClass(text: String): JQuery
    fun removeClass(text: String): JQuery

    fun click(action: (Event) -> Unit): JQuery
    fun change(action: (Event) -> Unit): JQuery
}