/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

import at.yawk.javap.model.CompilerConfiguration
import at.yawk.javap.model.HttpException
import at.yawk.javap.model.PasteDao
import at.yawk.javap.model.PasteDto
import at.yawk.javap.model.ProcessingInput
import at.yawk.javap.model.ProcessingOutput
import io.undertow.Undertow
import io.undertow.UndertowOptions
import io.undertow.server.HttpHandler
import io.undertow.util.StatusCodes
import kotlinx.serialization.json.Json
import org.flywaydb.core.Flyway
import org.h2.jdbcx.JdbcConnectionPool
import org.jdbi.v3.core.Jdbi
import org.testng.Assert
import org.testng.annotations.AfterClass
import org.testng.annotations.AfterTest
import org.testng.annotations.BeforeClass
import org.testng.annotations.Test
import java.lang.Exception
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.atomic.AtomicInteger
import javax.sql.DataSource

/**
 * @author yawkat
 */
class PasteResourceTest {
    private val processCount = AtomicInteger()
    private val processor = object : Processor {
        override fun process(input: ProcessingInput): ProcessingOutput {
            processCount.incrementAndGet()
            return ProcessingOutput("compiler log " + input.code, "javap " + input.code, "procyon " + input.code)
        }
    }
    private val dataSource: DataSource = JdbcConnectionPool.create("jdbc:h2:mem:test", "", "")
    private val dbi = Jdbi.create(dataSource).installPlugins()
    private val defaultPaste = DefaultPaste(processor)
    private val pasteResource: PasteResource = PasteResource(
            Json(builderAction = jsonConfiguration),
            dbi.onDemand(PasteDao::class.java),
            processor,
            defaultPaste)

    private lateinit var undertow: Undertow
    private lateinit var baseUri: URI
    private val httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()

    /**
     * Resource whose processor fails, to check that requests are rejected before any processing happens.
     */
    private val failingPasteResource = PasteResource(
            Json(builderAction = jsonConfiguration),
            dbi.onDemand(PasteDao::class.java),
            object : Processor {
                override fun process(input: ProcessingInput): ProcessingOutput {
                    throw AssertionError("Should not process input")
                }
            },
            defaultPaste)

    @BeforeClass
    fun setupDb() {
        val flyway = Flyway.configure()
                .dataSource(dataSource)
                .load()
        flyway.migrate()
    }

    @BeforeClass
    fun startServer() {
        // mirrors the setup in JavapApplication
        val notFound = handleExceptions(HttpHandler { throw HttpException(StatusCodes.NOT_FOUND, "Not found") })
        undertow = Undertow.builder()
                .addHttpListener(0, "127.0.0.1")
                .setServerOption(UndertowOptions.MAX_ENTITY_SIZE, SERVER_MAX_ENTITY_SIZE)
                .setHandler(handleExceptions(pasteResource.buildHandler(notFound)))
                .build()
        undertow.start()
        val address = undertow.listenerInfo.single().address as InetSocketAddress
        baseUri = URI("http://127.0.0.1:${address.port}")
    }

    @AfterClass
    fun stopServer() {
        undertow.stop()
    }

    @AfterTest
    fun clearDb() {
        dbi.useHandle<Exception> { it.createUpdate("DELETE FROM paste").execute() }
    }

    @Test
    fun `create get update cycle`() {
        val token = "abcdef"
        val input1 = ProcessingInput("test code 1", Sdks.defaultJava.name, emptyMap())
        val input2 = ProcessingInput("test code 2", Sdks.defaultJava.name, emptyMap())

        val created = pasteResource.createPaste(token, PasteDto.Create(input1))
        Assert.assertEquals(created, PasteDto(created.id, true, input1, processor.process(input1)))

        Assert.assertEquals(pasteResource.getPaste(token, created.id), created)

        val updated = pasteResource.updatePaste(token, created.id, PasteDto.Update(input2))
        Assert.assertEquals(updated, created.copy(input = input2, output = processor.process(input2)))

        Assert.assertEquals(pasteResource.getPaste(token, created.id), updated)
    }

    @Test(expectedExceptions = [HttpException::class])
    fun `paste get not found`() {
        pasteResource.getPaste(null, "xyz")
    }

    @Test(expectedExceptions = [HttpException::class])
    fun `paste update not found`() {
        pasteResource.updatePaste("abcdef", "xyz", PasteDto.Update())
    }

    @Test(expectedExceptions = [HttpException::class])
    fun `paste create invalid user token`() {
        pasteResource.createPaste("#", PasteDto.Create(
                ProcessingInput("abc", Sdks.defaultJava.name, emptyMap())))
    }

    @Test(expectedExceptions = [HttpException::class])
    fun `paste create no user token`() {
        pasteResource.createPaste(null, PasteDto.Create(
                ProcessingInput("abc", Sdks.defaultJava.name, emptyMap())))
    }

    @Test(expectedExceptions = [HttpException::class])
    fun `paste create empty user token`() {
        pasteResource.createPaste("", PasteDto.Create(
                ProcessingInput("abc", Sdks.defaultJava.name, emptyMap())))
    }

    @Test(expectedExceptions = [HttpException::class])
    fun `paste update invalid user token`() {
        pasteResource.updatePaste("#", "xyz", PasteDto.Update(
                ProcessingInput("abc", Sdks.defaultJava.name, emptyMap())))
    }

    @Test(expectedExceptions = [HttpException::class])
    fun `paste update no user token`() {
        pasteResource.updatePaste(null, "xyz", PasteDto.Update(
                ProcessingInput("abc", Sdks.defaultJava.name, emptyMap())))
    }

    @Test(expectedExceptions = [HttpException::class])
    fun `paste update empty user token`() {
        pasteResource.updatePaste("", "xyz", PasteDto.Update(
                ProcessingInput("abc", Sdks.defaultJava.name, emptyMap())))
    }

    @Test
    fun `paste create max length user token`() {
        val token = "a".repeat(64)
        val created = pasteResource.createPaste(token, PasteDto.Create(
                ProcessingInput("abc", Sdks.defaultJava.name, emptyMap())))
        Assert.assertTrue(pasteResource.getPaste(token, created.id).editable)
    }

    @Test
    fun `paste create too long user token`() {
        val e = Assert.expectThrows(HttpException::class.java) {
            failingPasteResource.createPaste("a".repeat(65), PasteDto.Create(
                    ProcessingInput("abc", Sdks.defaultJava.name, emptyMap())))
        }
        Assert.assertEquals(e.code, StatusCodes.BAD_REQUEST)
    }

    @Test
    fun `paste update too long user token`() {
        val token = "a".repeat(65)
        val created = pasteResource.createPaste("abc",
                PasteDto.Create(ProcessingInput("abc", Sdks.defaultJava.name, emptyMap())))
        val e = Assert.expectThrows(HttpException::class.java) {
            failingPasteResource.updatePaste(token, created.id, PasteDto.Update(
                    ProcessingInput("def", Sdks.defaultJava.name, emptyMap())))
        }
        Assert.assertEquals(e.code, StatusCodes.BAD_REQUEST)
    }

    @Test(expectedExceptions = [HttpException::class])
    fun `deny paste update for other user`() {
        val created = pasteResource.createPaste("abc",
                PasteDto.Create(ProcessingInput("abc", Sdks.defaultJava.name, emptyMap())))
        pasteResource.updatePaste("def",
                created.id,
                PasteDto.Update(ProcessingInput("def", Sdks.defaultJava.name, emptyMap())))
    }

    @Test
    fun `paste dto serialization`() {
        val input = ProcessingInput("in", Sdks.defaultJava.name, emptyMap())
        Assert.assertEquals(
                Json(builderAction = jsonConfiguration).encodeToString(PasteDto.serializer(),
                        PasteDto("a", false, input, processor.process(input))),
                """{"id":"a","editable":false,"input":{"code":"in","compilerName":"${Sdks.defaultJava.name}","compilerConfiguration":{}},"output":{"compilerLog":"compiler log in","javap":"javap in","procyon":"procyon in"}}"""
        )
    }

    @Test
    fun `get default paste`() {
        Assert.assertEquals(
                pasteResource.getPaste(null, "default:JAVA").input,
                defaultPaste.defaultPastes.getValue("default:JAVA").input
        )
        Assert.assertEquals(
                pasteResource.getPaste(null, "default:JAVA").output,
                defaultPaste.defaultPastes.getValue("default:JAVA").output
        )
    }

    private fun createBody(code: String) = Json(builderAction = jsonConfiguration).encodeToString(
            PasteDto.Create.serializer(),
            PasteDto.Create(ProcessingInput(code, Sdks.defaultJava.name, emptyMap())))

    /**
     * Create body that is just above [MAX_REQUEST_BODY_SIZE].
     */
    private fun oversizedBody(): String {
        val body = createBody("x".repeat(MAX_REQUEST_BODY_SIZE))
        Assert.assertTrue(body.length in MAX_REQUEST_BODY_SIZE + 1..MAX_REQUEST_BODY_SIZE + 1024)
        return body
    }

    private fun send(method: String, path: String, body: HttpRequest.BodyPublisher): HttpResponse<String> {
        val request = HttpRequest.newBuilder(baseUri.resolve(path))
                .method(method, body)
                .header("Content-Type", "application/json")
                .header("X-User-Token", "abcdef")
                .build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    @Test
    fun `http create paste within size limit`() {
        val before = processCount.get()
        val response = send("POST", "/api/paste", HttpRequest.BodyPublishers.ofString(createBody("test code")))
        Assert.assertEquals(response.statusCode(), StatusCodes.OK)
        Assert.assertEquals(processCount.get(), before + 1)
    }

    @Test
    fun `http create paste over size limit`() {
        val before = processCount.get()
        val response = send("POST", "/api/paste",
                HttpRequest.BodyPublishers.ofString(oversizedBody()))
        Assert.assertEquals(response.statusCode(), StatusCodes.REQUEST_ENTITY_TOO_LARGE)
        Assert.assertEquals(processCount.get(), before)
    }

    @Test
    fun `http create paste over size limit chunked`() {
        val before = processCount.get()
        // no Content-Length, so the limit has to be enforced while reading
        val body = HttpRequest.BodyPublishers.fromPublisher(
                HttpRequest.BodyPublishers.ofString(oversizedBody()))
        val response = send("POST", "/api/paste", body)
        Assert.assertEquals(response.statusCode(), StatusCodes.REQUEST_ENTITY_TOO_LARGE)
        Assert.assertEquals(processCount.get(), before)
    }

    @Test
    fun `http update paste over size limit`() {
        val created = pasteResource.createPaste("abcdef",
                PasteDto.Create(ProcessingInput("abc", Sdks.defaultJava.name, emptyMap())))
        val before = processCount.get()
        val response = send("PUT", "/api/paste/${created.id}",
                HttpRequest.BodyPublishers.ofString(oversizedBody()))
        Assert.assertEquals(response.statusCode(), StatusCodes.REQUEST_ENTITY_TOO_LARGE)
        Assert.assertEquals(processCount.get(), before)
    }
}
