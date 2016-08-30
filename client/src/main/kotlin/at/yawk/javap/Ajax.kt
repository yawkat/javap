/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

import kotlin.browser.document
import kotlin.browser.window
import kotlin.js.Date
import kotlin.js.native

/**
 * @author yawkat
 */
data class Request(
        val method: String = "GET",
        val url: String,
        val contentType: String? = undefined,
        val data: String? = undefined
)

@native
class RequestFuture {
    @native
    fun then(success: (dynamic) -> Unit, failure: (dynamic, Int, String) -> Unit): RequestFuture

    @native
    fun always(success: (dynamic) -> Unit): RequestFuture
}

fun ajax(request: Request): RequestFuture {
    val userToken: String

    val userTokenMatch = "userToken=(\\w+)".toRegex().find(document.cookie)
    if (userTokenMatch == null) {
        userToken = (0..63).map {
            val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            alphabet[Math.floor(Math.random() * alphabet.length)]
        }.joinToString("")
        val expiryTime: dynamic = Date()
        expiryTime.setTime(expiryTime.getTime() + (120.toLong() * 24 * 60 * 60 * 1000)) // 4 months
        document.cookie = "userToken=" + userToken + "; expires=" + expiryTime.toUTCString()
    } else {
        userToken = userTokenMatch.groupValues[1]
    }

    request.asDynamic().beforeSend = { xhr: dynamic ->
        xhr.setRequestHeader("X-User-Token", userToken)
    }

    return window["$"].ajax(request)
}

val handleError: dynamic = { xhr: dynamic, status: Int, msg: String ->
    val message: String
    if (xhr.responseJSON && xhr.responseJSON.message) {
        message = xhr.responseJSON.message
    } else {
        message = msg
    }
    showDialog("Error", message)
}