package at.yawk.javap.model

import org.skife.jdbi.v2.StatementContext
import org.skife.jdbi.v2.sqlobject.Bind
import org.skife.jdbi.v2.sqlobject.BindBean
import org.skife.jdbi.v2.sqlobject.SqlQuery
import org.skife.jdbi.v2.sqlobject.SqlUpdate
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper
import org.skife.jdbi.v2.tweak.ResultSetMapper
import java.sql.ResultSet

/**
 * @author yawkat
 */
@RegisterMapper(PasteDao.PasteMapper::class)
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

    class PasteMapper : ResultSetMapper<Paste> {
        override fun map(index: Int, r: ResultSet, ctx: StatementContext) = Paste(
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