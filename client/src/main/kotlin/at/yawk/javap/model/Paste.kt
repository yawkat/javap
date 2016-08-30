package at.yawk.javap.model

/**
 * @author yawkat
 */
data class Paste(
        val id: String,
        val ownerToken: String,
        val editable: Boolean,

        val input: ProcessingInput,
        val output: ProcessingOutput
) {
    companion object {
        fun fromJson(it: dynamic) = Paste(
                id = it.id,
                ownerToken = it.ownerToken,
                editable = it.editable,
                input = ProcessingInput(code = it.input.code, compilerName = it.input.compilerName),
                output = ProcessingOutput(
                        compilerLog = it.output.compilerLog,
                        javap = it.output.javap,
                        procyon = it.output.procyon
                )
        )
    }
}