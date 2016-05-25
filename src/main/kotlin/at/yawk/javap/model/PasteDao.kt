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

    @SqlUpdate("insert into paste (id, ownerToken, inputCode, outputCompilerLog, outputJavap) values " +
            "(:id, :ownerToken, :input.code, :output.compilerLog, :output.javap)")
    fun createPaste(@Bind("ownerToken") ownerToken: String, @Bind("id") id: String,
                    @BindBean("input") input: ProcessingInput, @BindBean("output") output: ProcessingOutput)

    @SqlUpdate("update paste set inputCode=:input.code, outputCompilerLog=:output.compilerLog, outputJavap=:output.javap " +
            "where id=:id and ownerToken=:ownerToken")
    fun updatePaste(@Bind("ownerToken") ownerToken: String, @Bind("id") id: String,
                    @BindBean("input") input: ProcessingInput, @BindBean("output") output: ProcessingOutput)

    class PasteMapper : ResultSetMapper<Paste> {
        override fun map(index: Int, r: ResultSet, ctx: StatementContext) = Paste(
                r.getString("id"),
                r.getString("ownerToken"),
                ProcessingInput(r.getString("inputCode")),
                ProcessingOutput(r.getString("outputCompilerLog"), r.getString("outputJavap"))
        )
    }
}