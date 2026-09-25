/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import org.w3c.dom.get
import org.w3c.dom.set
import org.w3c.xhr.JSON
import org.w3c.xhr.XMLHttpRequest
import org.w3c.xhr.XMLHttpRequestResponseType
import kotlinx.browser.document
import kotlinx.browser.localStorage
import kotlinx.browser.window
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get

/**
 * @author yawkat
 */
private const val ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
private const val USER_TOKEN_LENGTH = 64

/**
 * Generate a random token from [ALPHABET] using the Web Crypto CSPRNG. Bytes are mapped onto the alphabet with
 * rejection sampling so that every character is equally likely.
 */
private fun generateUserToken(): String {
    // largest multiple of ALPHABET.length that fits in a byte; bytes at or above it are discarded
    val limit = 256 - 256 % ALPHABET.length
    val builder = StringBuilder(USER_TOKEN_LENGTH)
    val bytes = Uint8Array(USER_TOKEN_LENGTH * 2)
    while (builder.length < USER_TOKEN_LENGTH) {
        window.asDynamic().crypto.getRandomValues(bytes)
        for (i in 0 until bytes.length) {
            val b = bytes[i].toInt() and 0xff
            if (b < limit && builder.length < USER_TOKEN_LENGTH) {
                builder.append(ALPHABET[b % ALPHABET.length])
            }
        }
    }
    return builder.toString()
}

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

    val generated = generateUserToken()
    localStorage["userToken"] = generated
    return generated
}

object Ajax {
    private val json = Json {}

    fun <O> get(
            url: String,
            outStrategy: DeserializationStrategy<O>,
            onSuccess: (O) -> Unit,
            always: () -> Unit
    ) = ajax(
            method = "GET",
            url = url,
            onSuccess = { onSuccess(json.decodeFromString(outStrategy, it)) },
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
            data = json.encodeToString(inStrategy, data),
            onSuccess = { onSuccess(json.decodeFromString(outStrategy, it)) },
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
                    Dialog.show("Error", message)
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