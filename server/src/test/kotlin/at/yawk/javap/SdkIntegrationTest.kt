/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

import at.yawk.javap.model.CompilerConfiguration
import at.yawk.javap.model.ProcessingInput
import org.testng.Assert
import org.testng.annotations.BeforeClass
import org.testng.annotations.DataProvider
import org.testng.annotations.Test
import java.nio.file.Paths

class SdkIntegrationTest {
    private lateinit var sdkProvider: SdkProviderImpl

    @BeforeClass
    fun setup() {
        // built with `nix build .#sdks -o sdk` in the repository root
        sdkProvider = SdkProviderImpl(Paths.get(System.getenv("JAVAP_SDK_MANIFEST") ?: "../sdk/sdks.json"))
        sdkProvider.start()
    }

    @DataProvider
    fun sdks(): Array<Array<Any>> {
        return Sdks.sdksByName.values
                .distinctBy { it.name }
                .map { arrayOf<Any>(it.name, it) }
                .toTypedArray()
    }

    @Test(dataProvider = "sdks")
    fun testSdk(name: String, sdk: Sdk) {
        Assert.assertEquals(name, sdk.name)
        val processor = LocalProcessor(sdkProvider, Bubblewrap())
        val testCode = when (sdk.language) {
            SdkLanguage.JAVA -> """
                ${if ((sdk as Sdk.Java).hasLombok) "@lombok.Data" else ""}
                class Test {
                    int a;
                    String b;
                }
            """
            SdkLanguage.KOTLIN -> """
                ${if ((sdk as Sdk.Kotlin).release >= KotlinVersion(1, 2)) "import kotlinx.coroutines.*" else ""}
                
                data class A(val a: Int, val s: String)
            """.trimIndent()
            SdkLanguage.SCALA -> """
                object Main {
                    def test(i: Int) = i + 1
                }
            """.trimIndent()
        }
        var compilerConfiguration: CompilerConfiguration = emptyMap()
        if (sdk is Sdk.Ecj) {
            compilerConfiguration = compilerConfiguration + ("source" to 8)
        }
        val input = ProcessingInput(testCode, sdk.name, compilerConfiguration)
        var output = processor.process(input)
        // javac 10 sometimes crashes inside its own runtime image (a ConcurrentModificationException from
        // jdk.internal.jrtfs in JRTIndex.getEntry), on CI runners only. That's a JDK bug, not ours, so retry it.
        for (attempt in 1..2) {
            if (!(output.compilerLog.contains("An exception has occurred in the compiler") &&
                        output.compilerLog.contains("java.util.ConcurrentModificationException"))) {
                break
            }
            output = processor.process(input)
        }
        Assert.assertTrue(output.compilerLog.isEmpty(), "${sdk.name}: ${output.compilerLog}")
        Assert.assertNotNull(output.procyon, sdk.name)
        Assert.assertFalse(output.procyon!!.contains("Exception: "), sdk.name)
        Assert.assertNotNull(output.javap, sdk.name)
    }
}
