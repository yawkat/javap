package at.yawk.javap

import at.yawk.javap.model.ProcessingInput
import at.yawk.javap.model.ProcessingOutput
import com.google.inject.ImplementedBy

/**
 * @author yawkat
 */
@ImplementedBy(LocalProcessor::class)
interface Processor {
    fun process(input: ProcessingInput): ProcessingOutput
}