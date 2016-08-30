package at.yawk.javap

import jquery.JQuery
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * @author yawkat
 */
@native("$")
public fun jq(doc: Document): JQuery = JQuery();

@native
fun JQuery.find(selector: String): JQuery = noImpl

@native
fun JQuery.each(action: Element.() -> Unit): JQuery = noImpl

@native
fun JQuery.empty(): Unit = noImpl

@native
fun JQuery.first(): JQuery = noImpl

@native
fun JQuery.`val`(string: String?): Unit = noImpl

@native
fun JQuery.data(key: String, value: Any?): Unit = noImpl

@native
fun <T> JQuery.data(key: String): T = noImpl

@native
fun JQuery.attr(key: String, value: Any?): Unit = noImpl

@native("attr")
fun <T> JQuery.attrGen(key: String): T = noImpl

@native
fun JQuery.append(jQuery: JQuery): Unit = noImpl

@native
fun JQuery.toggleClass(clazz: String, value: Boolean? = null): Unit = noImpl