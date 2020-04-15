/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap.model

import at.yawk.javap.SdkLanguage
import at.yawk.javap.Sdks
import at.yawk.javap.jsonConfiguration
import kotlinx.serialization.json.Json
import org.jdbi.v3.core.mapper.RowMapper
import org.jdbi.v3.core.statement.StatementContext
import org.jdbi.v3.sqlobject.config.RegisterRowMapper
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.customizer.BindBean
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate
import java.sql.ResultSet

private val json = Json(jsonConfiguration)

@RegisterRowMapper(PasteDao.PasteMapper::class)
interface PasteDao {
    private fun serializeConfig(input: ProcessingInput): ByteArray {
        val sdk = Sdks.sdksByName.getValue(input.compilerName)
        return json.stringify(ConfigProperties.serializers.getValue(sdk.language), input.compilerConfiguration)
                .toByteArray()
    }

    @SqlQuery("select * from paste where id = :id")
    fun getPasteById(@Bind("id") id: String): Paste?

    @SqlUpdate("""insert into paste 
        (id, ownerToken, inputCode, inputCompilerName, outputCompilerLog, outputJavap, outputProcyon) 
        values (:id, :ownerToken, :input.code, :input.compilerName, :output.compilerLog, :output.javap, :output.procyon)""")
    fun createPaste(
            @Bind("ownerToken") ownerToken: String,
            @Bind("id") id: String,
            @BindBean("input") input: ProcessingInput,
            @Bind("inputCompilerConfig") compilerConfig: ByteArray,
            @BindBean("output") output: ProcessingOutput
    )

    @JvmDefault
    fun createPaste(
            ownerToken: String,
            id: String,
            input: ProcessingInput,
            output: ProcessingOutput
    ) {
        createPaste(
                ownerToken,
                id,
                input,
                serializeConfig(input),
                output
        )
    }

    @SqlUpdate("""
        update paste 
        set inputCode=:input.code, inputCompilerName=:input.compilerName, outputCompilerLog=:output.compilerLog, outputJavap=:output.javap, outputProcyon=:output.procyon, inputCompilerConfiguration=:inputCompilerConfig
        where id=:id and ownerToken=:ownerToken""")
    fun updatePaste(
            @Bind("ownerToken") ownerToken: String,
            @Bind("id") id: String,
            @BindBean("input") input: ProcessingInput,
            @Bind("inputCompilerConfig") compilerConfig: ByteArray,
            @BindBean("output") output: ProcessingOutput
    )

    @JvmDefault
    fun updatePaste(
            ownerToken: String,
            id: String,
            input: ProcessingInput,
            output: ProcessingOutput) {
        updatePaste(
                ownerToken,
                id,
                input,
                serializeConfig(input),
                output
        )
    }

    class PasteMapper : RowMapper<Paste> {
        override fun map(r: ResultSet, ctx: StatementContext): Paste {
            val configBytes = r.getBytes("inputCompilerConfiguration")
            val compilerName = r.getString("inputCompilerName")
            val configuration =
                    if (configBytes == null) {
                        emptyMap()
                    } else {
                        val language = Sdks.sdksByName.getValue(compilerName).language
                        json.parse(
                                ConfigProperties.serializers.getValue(language),
                                configBytes.toString(Charsets.UTF_8))
                    }
            return Paste(
                    id = r.getString("id"),
                    ownerToken = r.getString("ownerToken"),
                    input = ProcessingInput(
                            code = r.getString("inputCode"),
                            compilerName = compilerName,
                            compilerConfiguration = configuration
                    ),
                    output = ProcessingOutput(
                            compilerLog = r.getString("outputCompilerLog"),
                            javap = r.getString("outputJavap"),
                            procyon = r.getString("outputProcyon"))
            )
        }
    }
}