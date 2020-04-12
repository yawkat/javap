/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

import org.w3c.dom.get
import org.w3c.dom.set
import kotlin.browser.document
import kotlin.browser.localStorage
import kotlin.browser.window
import kotlin.random.Random

/**
 * @author yawkat
 */
data class Request(
        val method: String = "GET",
        val url: String,
        val contentType: String? = undefined,
        val data: String? = undefined
)

external class RequestFuture {
    fun then(success: (dynamic) -> Unit, failure: (dynamic, Int, String) -> Unit): RequestFuture

    fun always(success: (dynamic) -> Unit): RequestFuture
}

private const val ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

private fun getOrCreateUserToken(): String {
    // check local storage first
    localStorage["userToken"]?.let { return it }

    val cookieUserTokenMatch = "userToken=(\\w+)".toRegex().find(document.cookie)
    if (cookieUserTokenMatch != null) {
        val userToken = cookieUserTokenMatch.groupValues[1]
        localStorage["userToken"] = userToken
        // clear cookie
        document.cookie = "userToken=; expires=Thu, 01 Jan 1970 00:00:00 UTC"
        return userToken
    }

    val generated = (0..63).map {
        ALPHABET[Random.nextInt(ALPHABET.length)]
    }.joinToString("")
    localStorage["userToken"] = generated
    return generated
}

fun ajax(request: Request): RequestFuture {
    request.asDynamic().beforeSend = { xhr: dynamic ->
        xhr.setRequestHeader("X-User-Token", getOrCreateUserToken())
    }

    return window["$"].ajax(request)
}

val handleError: dynamic = { xhr: dynamic, status: Int, msg: String ->
    val message: String
    if (xhr.responseJSON && xhr.responseJSON.message) {
        message = xhr.responseJSON.message as String
    } else {
        message = msg
    }
    showDialog("Error", message)
}