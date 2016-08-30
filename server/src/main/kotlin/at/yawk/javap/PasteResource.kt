/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

import at.yawk.javap.model.Paste
import at.yawk.javap.model.PasteDao
import at.yawk.javap.model.ProcessingInput
import com.fasterxml.jackson.annotation.JsonUnwrapped
import org.skife.jdbi.v2.DBI
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
        val dbi: DBI,
        val pasteDao: PasteDao,
        val processor: Processor,
        val defaultPaste: DefaultPaste
) {
    class PasteDto(@JsonUnwrapped val paste: Paste, requestUserToken: String?) {
        @Suppress("unused")
        val editable = requestUserToken == paste.ownerToken
    }

    @GET
    @Path("/{id}")
    fun getPaste(@HeaderParam("X-User-Token") userToken: String?, @PathParam("id") id: String): PasteDto {
        val paste = defaultPaste.defaultPastes.find { it.id == id }
                ?: pasteDao.getPasteById(id) ?: throw NotFoundException()
        return PasteDto(paste, userToken)
    }

    @POST
    fun createPaste(@HeaderParam("X-User-Token") userToken: String?, body: Create): PasteDto {
        if (userToken == null || !userToken.matches("[a-zA-Z0-9]+".toRegex())) {
            throw BadRequestException("Illegal user token")
        }

        val input = body.input
        val output = processor.process(body.input)

        while (true) {
            val id = generateId(6)
            if (defaultPaste.defaultPastes.any { it.id == id }) continue
            // todo: handle PK violation and retry with different ID
            pasteDao.createPaste(userToken, id, input, output)
            return PasteDto(Paste(id, userToken, input, output), userToken)
        }
    }

    data class Create(
            val input: ProcessingInput
    )

    @PUT
    @Path("/{id}")
    fun updatePaste(@HeaderParam("X-User-Token") userToken: String?, @PathParam("id") id: String, body: Update): PasteDto {
        if (userToken == null || !userToken.matches("[a-zA-Z0-9]+".toRegex())) {
            throw BadRequestException("Illegal user token")
        }

        var paste = pasteDao.getPasteById(id) ?: throw NotFoundException()
        if (paste.ownerToken != userToken) {
            throw NotAuthorizedException("Not your paste")
        }
        if (body.input != null && body.input != paste.input) {
            val output = processor.process(body.input)
            paste = paste.copy(input = body.input, output = output)
        }
        pasteDao.updatePaste(userToken, paste.id, paste.input, paste.output)
        return PasteDto(paste, userToken)
    }

    data class Update(
            val input: ProcessingInput? = null
    )
}