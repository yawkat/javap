/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

import at.yawk.javap.model.HttpException
import at.yawk.javap.model.PasteDao
import at.yawk.javap.model.PasteDto
import at.yawk.javap.model.ProcessingInput
import at.yawk.javap.model.ProcessingOutput
import com.google.common.net.MediaType
import io.undertow.server.HttpHandler
import io.undertow.server.HttpServerExchange
import io.undertow.server.handlers.PathTemplateHandler
import io.undertow.util.Headers
import io.undertow.util.Methods
import io.undertow.util.StatusCodes
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.util.concurrent.ThreadLocalRandom

/**
 * @author yawkat
 */
fun generateId(length: Int): String {
    val possibilities = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    return String(CharArray(length) { possibilities[ThreadLocalRandom.current().nextInt(possibilities.length)] })
}

class PasteResource constructor(
        private val json: Json,
        private val pasteDao: PasteDao,
        private val processor: Processor,
        private val defaultPaste: DefaultPaste
) {
    @Suppress("UnstableApiUsage")
    private fun <T> parse(xhg: HttpServerExchange, deserializer: DeserializationStrategy<T>, callback: (T) -> Unit) {
        if (xhg.contentType.withoutParameters() == MediaType.JSON_UTF_8.withoutParameters()) {
            xhg.requestReceiver.receiveFullString({ _, s ->
                handleExceptions(xhg) {
                    callback(json.parse(deserializer, s))
                }
            }, StandardCharsets.UTF_8)
        } else {
            throw HttpException(StatusCodes.UNSUPPORTED_MEDIA_TYPE, "Unsupported Content-Type")
        }
    }

    @Suppress("UnstableApiUsage")
    private fun <T> write(xhg: HttpServerExchange, serializer: SerializationStrategy<T>, value: T) {
        val accept = xhg.accept?.withoutParameters()
        if (accept == null || MediaType.JSON_UTF_8.`is`(accept)) {
            xhg.responseHeaders.put(Headers.CONTENT_TYPE, MediaType.JSON_UTF_8.toString())
            xhg.responseSender.send(json.stringify(serializer, value))
        } else {
            throw HttpException(StatusCodes.NOT_ACCEPTABLE, "Unsupported Accept")
        }
    }

    fun buildHandler(next: HttpHandler): HttpHandler {
        val handler = PathTemplateHandler(next)
        handler.add("/api/paste/{id}") { xhg ->
            val id = xhg.queryParameters["id"]?.peekFirst()
                    ?: throw HttpException(StatusCodes.NOT_FOUND, "id not given")
            val userToken: String? = xhg.requestHeaders.getFirst("X-User-Token")
            when (xhg.requestMethod) {
                Methods.PUT -> {
                    parse(xhg, PasteDto.Update.serializer()) {
                        write(xhg, PasteDto.serializer(), updatePaste(userToken, id, it))
                    }
                }
                Methods.GET -> {
                    write(xhg, PasteDto.serializer(), getPaste(userToken, id))
                }
                else -> next.handleRequest(xhg)
            }
        }
        handler.add("/api/paste") { xhg ->
            val userToken: String? = xhg.requestHeaders.getFirst("X-User-Token")
            when (xhg.requestMethod) {
                Methods.POST -> {
                    parse(xhg, PasteDto.Create.serializer()) {
                        write(xhg, PasteDto.serializer(), createPaste(userToken, it))
                    }
                }
                else -> next.handleRequest(xhg)
            }
        }
        return handler
    }

    fun getPaste(userToken: String?, id: String): PasteDto {
        val paste = defaultPaste.defaultPastes[id]
                ?: pasteDao.getPasteById(id)
                ?: throw HttpException(StatusCodes.NOT_FOUND, "No such paste")
        return PasteDto(
                id = id,
                editable = paste.ownerToken == userToken,
                input = paste.input,
                output = paste.output
        )
    }

    fun createPaste(userToken: String?, body: PasteDto.Create): PasteDto {
        if (userToken == null || !userToken.matches("[a-zA-Z0-9]+".toRegex())) {
            throw HttpException(StatusCodes.BAD_REQUEST, "Illegal user token")
        }

        val input = sanitizeInput(body.input)
        val output = sanitizeOutput(processor.process(body.input))

        while (true) {
            val id = generateId(6)
            if (defaultPaste.defaultPastes.containsKey(id)) continue
            // todo: handle PK violation and retry with different ID
            pasteDao.createPaste(userToken, id, input, output)
            return PasteDto(
                    id = id,
                    editable = true,
                    input = input,
                    output = output
            )
        }
    }

    // postgres does not like 0 bytes
    private fun String.sanitizeText() = replace("\u0000", "\\u0000")

    private fun sanitizeInput(input: ProcessingInput) = ProcessingInput(
            code = input.code.sanitizeText(),
            compilerName = input.compilerName,
            compilerConfiguration = input.compilerConfiguration
    )

    private fun sanitizeOutput(output: ProcessingOutput): ProcessingOutput {
        return ProcessingOutput(
                compilerLog = output.compilerLog.sanitizeText(),
                javap = output.javap?.sanitizeText(),
                procyon = output.procyon?.sanitizeText()
        )
    }

    fun updatePaste(userToken: String?,
                    id: String,
                    body: PasteDto.Update): PasteDto {
        if (userToken == null || !userToken.matches("[a-zA-Z0-9]+".toRegex())) {
            throw HttpException(StatusCodes.BAD_REQUEST, "Illegal user token")
        }

        var paste = pasteDao.getPasteById(id) ?: throw HttpException(StatusCodes.NOT_FOUND, "No such paste")
        if (paste.ownerToken != userToken) {
            throw HttpException(StatusCodes.UNAUTHORIZED, "Not your paste")
        }
        if (body.input != null) {
            val newInput = sanitizeInput(body.input!!)
            if (newInput != paste.input) {
                val output = sanitizeOutput(processor.process(newInput))
                paste = paste.copy(input = newInput, output = output)
            }
        }
        pasteDao.updatePaste(userToken, paste.id, paste.input, paste.output)
        return PasteDto(
                id = id,
                editable = paste.ownerToken == userToken,
                input = paste.input,
                output = paste.output
        )
    }
}