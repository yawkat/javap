package at.yawk.javap

import javax.inject.Inject
import javax.ws.rs.Consumes
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType

/**
 * @author yawkat
 */
@Path("/compiler") // /api/compiler
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class CompilerResource @Inject constructor(val jdkProvider: JdkProvider) {
    @GET
    fun listJdks() = jdkProvider.jdks.map { mapOf("name" to it.name) }
}