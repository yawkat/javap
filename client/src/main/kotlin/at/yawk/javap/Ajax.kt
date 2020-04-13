/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import org.w3c.dom.get
import org.w3c.dom.set
import org.w3c.xhr.JSON
import org.w3c.xhr.XMLHttpRequest
import org.w3c.xhr.XMLHttpRequestResponseType
import kotlin.browser.document
import kotlin.browser.localStorage
import kotlin.random.Random

/**
 * @author yawkat
 */
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

object Ajax {
    private val json = Json(JsonConfiguration.Stable)

    fun <O> get(
            url: String,
            outStrategy: DeserializationStrategy<O>,
            onSuccess: (O) -> Unit,
            always: () -> Unit
    ) = ajax(
            method = "GET",
            url = url,
            onSuccess = { onSuccess(json.parse(outStrategy, it)) },
            always = always
    )

    fun <I, O> postPut(
            method: String,
            url: String,
            data: I,
            inStrategy: SerializationStrategy<I>,
            outStrategy: DeserializationStrategy<O>,
            onSuccess: (O) -> Unit,
            always: () -> Unit
    ) = ajax(
            method = method,
            url = url,
            contentType = "application/json; charset=utf-8",
            data = json.stringify(inStrategy, data),
            onSuccess = { onSuccess(json.parse(outStrategy, it)) },
            always = always
    )

    private fun ajax(
            method: String,
            url: String,
            contentType: String? = null,
            data: String? = null,
            onSuccess: (String) -> Unit,
            always: () -> Unit
    ) {
        val xhr = XMLHttpRequest()
        xhr.open(method, url)
        xhr.onreadystatechange = {
            if (xhr.readyState == XMLHttpRequest.DONE) {
                always()

                val body = xhr.responseText
                if (xhr.status in 200..399) {
                    onSuccess(body)
                } else {
                    var message = xhr.responseText
                    if (xhr.responseType == XMLHttpRequestResponseType.JSON) {
                        @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
                        val error = xhr.response as kotlin.js.Json
                        error["message"]?.let { message = it as String }
                    }
                    showDialog("Error", message)
                }
            }
        }
        if (contentType != null) {
            xhr.setRequestHeader("Content-Type", contentType)
        }
        xhr.setRequestHeader("X-User-Token", getOrCreateUserToken())
        xhr.send(data)
    }
}