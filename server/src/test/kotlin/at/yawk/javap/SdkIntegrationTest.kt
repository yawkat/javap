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

@Test(enabled = false)
class SdkIntegrationTest {
    private lateinit var sdkProvider: SdkProviderImpl

    @BeforeClass
    fun setup() {
        sdkProvider = SdkProviderImpl()
        sdkProvider.start()
    }

    @DataProvider
    fun sdks(): Array<Array<Any>> {
        return Sdks.sdksByName.values.map { arrayOf<Any>(it) }.toTypedArray()
    }

    @Test(dataProvider = "sdks")
    fun testSdk(sdk: Sdk) {
        val processor = LocalProcessor(sdkProvider, Bubblewrap())
        val testCode = when (sdk.language) {
            SdkLanguage.JAVA -> """
                ${if (sdk !is Sdk.OpenJdk || sdk.lombok != null) "@lombok.Data" else ""}
                class Test {
                    int a;
                    String b;
                }
            """
            SdkLanguage.KOTLIN -> """
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
        val output = processor.process(ProcessingInput(testCode, sdk.name, compilerConfiguration))
        Assert.assertTrue(output.compilerLog.isEmpty(), output.compilerLog)
        Assert.assertNotNull(output.procyon)
        Assert.assertNotNull(output.javap)
    }
}