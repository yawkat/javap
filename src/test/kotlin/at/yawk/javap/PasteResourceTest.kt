package at.yawk.javap

import at.yawk.javap.model.Paste
import at.yawk.javap.model.PasteDao
import at.yawk.javap.model.ProcessingInput
import at.yawk.javap.model.ProcessingOutput
import com.fasterxml.jackson.databind.ObjectMapper
import org.flywaydb.core.Flyway
import org.h2.jdbcx.JdbcConnectionPool
import org.skife.jdbi.v2.DBI
import org.testng.Assert
import org.testng.annotations.AfterTest
import org.testng.annotations.BeforeClass
import org.testng.annotations.Test
import javax.sql.DataSource
import javax.ws.rs.BadRequestException
import javax.ws.rs.NotAuthorizedException
import javax.ws.rs.NotFoundException

/**
 * @author yawkat
 */
class PasteResourceTest {
    val processor = object : Processor {
        override fun process(input: ProcessingInput): ProcessingOutput {
            return ProcessingOutput("compiler log " + input.code, "javap " + input.code)
        }
    }
    val dataSource: DataSource = JdbcConnectionPool.create("jdbc:h2:mem:test", "", "")
    val dbi = DBI(dataSource)
    val pasteResource: PasteResource = PasteResource(dbi, dbi.onDemand(PasteDao::class.java), processor,
            DefaultPaste(SystemSdkProvider, processor))

    @BeforeClass
    fun setupDb() {
        val flyway = Flyway()
        flyway.dataSource = dataSource
        flyway.migrate()
    }

    @AfterTest
    fun clearDb() {
        dbi.useHandle { it.update("DELETE FROM paste") }
    }

    @Test
    fun `create get update cycle`() {
        val token = "abcdef"
        val input1 = ProcessingInput("test code 1", SystemSdkProvider.JDK)
        val input2 = ProcessingInput("test code 2", SystemSdkProvider.JDK)

        val created = pasteResource.createPaste(token, PasteResource.Create(input1)).paste
        Assert.assertEquals(created, Paste(created.id, token, input1, processor.process(input1)))

        Assert.assertEquals(pasteResource.getPaste(token, created.id).paste, created)

        val updated = pasteResource.updatePaste(token, created.id, PasteResource.Update(input2)).paste
        Assert.assertEquals(updated, created.copy(input = input2, output = processor.process(input2)))

        Assert.assertEquals(pasteResource.getPaste(token, created.id).paste, updated)
    }

    @Test(expectedExceptions = arrayOf(NotFoundException::class))
    fun `paste get not found`() {
        pasteResource.getPaste(null, "xyz")
    }

    @Test(expectedExceptions = arrayOf(NotFoundException::class))
    fun `paste update not found`() {
        pasteResource.updatePaste("abcdef", "xyz", PasteResource.Update())
    }

    @Test(expectedExceptions = arrayOf(BadRequestException::class))
    fun `paste create invalid user token`() {
        pasteResource.createPaste("#", PasteResource.Create(ProcessingInput("abc", SystemSdkProvider.JDK)))
    }

    @Test(expectedExceptions = arrayOf(BadRequestException::class))
    fun `paste create no user token`() {
        pasteResource.createPaste(null, PasteResource.Create(ProcessingInput("abc", SystemSdkProvider.JDK)))
    }

    @Test(expectedExceptions = arrayOf(BadRequestException::class))
    fun `paste create empty user token`() {
        pasteResource.createPaste("", PasteResource.Create(ProcessingInput("abc", SystemSdkProvider.JDK)))
    }

    @Test(expectedExceptions = arrayOf(BadRequestException::class))
    fun `paste update invalid user token`() {
        pasteResource.updatePaste("#", "xyz", PasteResource.Update(ProcessingInput("abc", SystemSdkProvider.JDK)))
    }

    @Test(expectedExceptions = arrayOf(BadRequestException::class))
    fun `paste update no user token`() {
        pasteResource.updatePaste(null, "xyz", PasteResource.Update(ProcessingInput("abc", SystemSdkProvider.JDK)))
    }

    @Test(expectedExceptions = arrayOf(BadRequestException::class))
    fun `paste update empty user token`() {
        pasteResource.updatePaste("", "xyz", PasteResource.Update(ProcessingInput("abc", SystemSdkProvider.JDK)))
    }

    @Test(expectedExceptions = arrayOf(NotAuthorizedException::class))
    fun `deny paste update for other user`() {
        val created = pasteResource.createPaste("abc", PasteResource.Create(ProcessingInput("abc", SystemSdkProvider.JDK))).paste
        pasteResource.updatePaste("def", created.id, PasteResource.Update(ProcessingInput("def", SystemSdkProvider.JDK)))
    }

    @Test
    fun `paste dto serialization`() {
        val objectMapper = ObjectMapper().findAndRegisterModules()
        val input = ProcessingInput("in", SystemSdkProvider.JDK)
        Assert.assertEquals(
                objectMapper.writeValueAsString(PasteResource.PasteDto(Paste("a", "b", input, processor.process(input)), "a")),
                """{"id":"a","input":{"code":"in","compilerName":"SYSTEM"},"output":{"compilerLog":"compiler log in","javap":"javap in"},"editable":false}"""
        )
    }

    @Test
    fun `get default paste`() {
        Assert.assertEquals(
                pasteResource.getPaste(null, "default:JAVA").paste,
                pasteResource.defaultPaste.defaultPastes[0]
        )
    }
}