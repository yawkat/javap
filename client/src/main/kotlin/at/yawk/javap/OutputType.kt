package at.yawk.javap

import at.yawk.javap.model.ProcessingOutput

/**
 * @author yawkat
 */
enum class OutputType(val getter: (ProcessingOutput) -> String?) {
    compilerLog({ it.compilerLog }),
    javap({ it.javap }),
    procyon({ it.procyon }),
}