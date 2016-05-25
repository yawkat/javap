package at.yawk.javap

import at.yawk.javap.model.Paste
import at.yawk.javap.model.ProcessingInput
import javax.inject.Inject

/**
 * @author yawkat
 */
class DefaultPaste @Inject constructor(processor: Processor) {
    private val input = ProcessingInput("public class Main {\n    public Main() {\n        int i = 0;\n        i++;\n    }\n}")
    val defaultPaste = Paste(DEFAULT_PASTE_NAME, "", input, processor.process(input))
}