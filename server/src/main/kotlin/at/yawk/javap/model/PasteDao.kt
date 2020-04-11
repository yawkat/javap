/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap.model

import org.jdbi.v3.core.mapper.RowMapper
import org.jdbi.v3.core.statement.StatementContext
import org.jdbi.v3.sqlobject.config.RegisterRowMapper
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.customizer.BindBean
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate
import java.sql.ResultSet

/**
 * @author yawkat
 */
@RegisterRowMapper(PasteDao.PasteMapper::class)
interface PasteDao {
    @SqlQuery("select * from paste where id = :id")
    fun getPasteById(@Bind("id") id: String): Paste?

    @SqlUpdate("insert into paste (id, ownerToken, inputCode, inputCompilerName, outputCompilerLog, outputJavap, outputProcyon) values " +
            "(:id, :ownerToken, :input.code, :input.compilerName, :output.compilerLog, :output.javap, :output.procyon)")
    fun createPaste(@Bind("ownerToken") ownerToken: String, @Bind("id") id: String,
                    @BindBean("input") input: ProcessingInput, @BindBean("output") output: ProcessingOutput)

    @SqlUpdate("update paste set inputCode=:input.code, inputCompilerName=:input.compilerName, " +
            "outputCompilerLog=:output.compilerLog, outputJavap=:output.javap, outputProcyon=:output.procyon " +
            "where id=:id and ownerToken=:ownerToken")
    fun updatePaste(@Bind("ownerToken") ownerToken: String, @Bind("id") id: String,
                    @BindBean("input") input: ProcessingInput, @BindBean("output") output: ProcessingOutput)

    class PasteMapper : RowMapper<Paste> {
        override fun map(r: ResultSet, ctx: StatementContext) = Paste(
                id = r.getString("id"),
                ownerToken = r.getString("ownerToken"),
                input = ProcessingInput(
                        code = r.getString("inputCode"),
                        compilerName = r.getString("inputCompilerName")),
                output = ProcessingOutput(
                        compilerLog = r.getString("outputCompilerLog"),
                        javap = r.getString("outputJavap"),
                        procyon = r.getString("outputProcyon"))
        )
    }
}