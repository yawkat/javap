/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

import at.yawk.javap.model.CompilerConfiguration
import at.yawk.javap.model.ProcessingInput
import at.yawk.javap.model.ProcessingOutput
import org.testng.Assert.assertEquals
import org.testng.annotations.Test

/**
 * @author yawkat
 */
class LocalProcessorTest {
    private val localProcessor = LocalProcessor(SystemSdkProvider, Bubblewrap())

    @Test
    fun `empty file`() {
        assertEquals(
                localProcessor.process(ProcessingInput("", Sdks.defaultJava.name, emptyMap())),
                ProcessingOutput("", NO_CLASSES_GENERATED, null)
        )
    }

    @Test
    fun `compile error`() {
        assertEquals(
                localProcessor.process(ProcessingInput(
                        "abc", Sdks.defaultJava.name, emptyMap())),
                ProcessingOutput("""Main.java:1: error: reached end of file while parsing
abc
^
1 error
""", null, null)
        )
    }
}