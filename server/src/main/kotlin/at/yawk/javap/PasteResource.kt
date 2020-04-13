/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

import at.yawk.javap.model.PasteDao
import at.yawk.javap.model.PasteDto
import at.yawk.javap.model.ProcessingInput
import at.yawk.javap.model.ProcessingOutput
import java.util.concurrent.ThreadLocalRandom
import javax.inject.Inject
import javax.ws.rs.*
import javax.ws.rs.core.MediaType

/**
 * @author yawkat
 */
fun generateId(length: Int): String {
    val possibilities = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    return String(CharArray(length) { possibilities[ThreadLocalRandom.current().nextInt(possibilities.length)] })
}

@Path("/paste") // /api/paste
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class PasteResource @Inject constructor(
        private val pasteDao: PasteDao,
        private val processor: Processor,
        private val defaultPaste: DefaultPaste
) {
    @GET
    @Path("/{id}")
    fun getPaste(@HeaderParam("X-User-Token") userToken: String?, @PathParam("id") id: String): PasteDto {
        val paste = defaultPaste.defaultPastes.find { it.id == id }
                ?: pasteDao.getPasteById(id) ?: throw NotFoundException()
        return PasteDto(
                id = id,
                editable = paste.ownerToken == userToken,
                input = paste.input,
                output = paste.output
        )
    }

    @POST
    fun createPaste(@HeaderParam("X-User-Token") userToken: String?, body: PasteDto.Create): PasteDto {
        if (userToken == null || !userToken.matches("[a-zA-Z0-9]+".toRegex())) {
            throw BadRequestException("Illegal user token")
        }

        val input = sanitizeInput(body.input)
        val output = sanitizeOutput(processor.process(body.input))

        while (true) {
            val id = generateId(6)
            if (defaultPaste.defaultPastes.any { it.id == id }) continue
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
            compilerName = input.compilerName
    )

    private fun sanitizeOutput(output: ProcessingOutput): ProcessingOutput {
        return ProcessingOutput(
                compilerLog = output.compilerLog.sanitizeText(),
                javap = output.javap?.sanitizeText(),
                procyon = output.procyon?.sanitizeText()
        )
    }

    @PUT
    @Path("/{id}")
    fun updatePaste(@HeaderParam("X-User-Token") userToken: String?, @PathParam("id") id: String, body: PasteDto.Update): PasteDto {
        if (userToken == null || !userToken.matches("[a-zA-Z0-9]+".toRegex())) {
            throw BadRequestException("Illegal user token")
        }

        var paste = pasteDao.getPasteById(id) ?: throw NotFoundException()
        if (paste.ownerToken != userToken) {
            throw NotAuthorizedException("Not your paste")
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