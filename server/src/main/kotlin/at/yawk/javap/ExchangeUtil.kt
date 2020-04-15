/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

@file:Suppress("UnstableApiUsage")

package at.yawk.javap

import at.yawk.javap.model.HttpException
import com.google.common.net.MediaType
import io.undertow.server.HttpHandler
import io.undertow.server.HttpServerExchange
import io.undertow.server.handlers.ExceptionHandler
import io.undertow.util.Headers
import io.undertow.util.StatusCodes
import kotlinx.serialization.json.Json
import java.lang.IllegalArgumentException

val HttpServerExchange.accept: MediaType?
    get() = requestHeaders.getLast(Headers.ACCEPT)?.let {
        try {
            MediaType.parse(it)
        } catch (fail: IllegalArgumentException) {
            null
        }
    }

val HttpServerExchange.contentType: MediaType
    @Throws(HttpException::class)
    get() {
        val contentTypeHeader = requestHeaders.getFirst(Headers.CONTENT_TYPE)
                ?: throw HttpException(StatusCodes.BAD_REQUEST, "Missing Content-Type")
        try {
            return MediaType.parse(contentTypeHeader)
        } catch (e: IllegalArgumentException) {
            throw HttpException(StatusCodes.UNSUPPORTED_MEDIA_TYPE, "Bad Content-Type")
        }
    }

private val json = Json(jsonConfiguration)

fun handleHttpException(xhg: HttpServerExchange, exception: HttpException) {
    xhg.statusCode = exception.code
    if (xhg.accept?.withoutParameters() == MediaType.JSON_UTF_8.withoutParameters()) {
        val serialized = json.stringify(HttpException.serializer(), exception)
        xhg.responseHeaders.put(Headers.CONTENT_TYPE, MediaType.JSON_UTF_8.toString())
        xhg.responseSender.send(serialized)
    } else {
        xhg.responseHeaders.put(Headers.CONTENT_TYPE, MediaType.PLAIN_TEXT_UTF_8.toString())
        xhg.responseSender.send(exception.message)
    }
}

inline fun handleExceptions(xhg: HttpServerExchange, f: () -> Unit) {
    try {
        f()
    } catch (e: HttpException) {
        handleHttpException(xhg, e)
    }
}

fun handleExceptions(handler: HttpHandler): HttpHandler = HttpHandler { xhg ->
    handleExceptions(xhg) { handler.handleRequest(xhg) }
}