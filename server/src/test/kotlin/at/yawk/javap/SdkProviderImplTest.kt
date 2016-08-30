/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

import org.testng.Assert
import org.testng.annotations.Test
import org.zeroturnaround.exec.ProcessExecutor

/**
 * @author yawkat
 */
class SdkProviderImplTest {
    @Test(enabled = false)
    fun testDownload() {
        val provider = SdkProviderImpl()
        provider.downloadMissing()
        val version = ProcessExecutor()
                .command(*provider.defaultSdkByLanguage[SdkLanguage.JAVA]!!.compilerCommand.toTypedArray(), "-version")
                .readOutput(true)
                .exitValueNormal()
                .execute()
                .outputUTF8()
        Assert.assertTrue(version.trim().matches("javac 1\\..*".toRegex()))
    }

    @Test(enabled = false)
    fun `ecj`() {
        val provider = SdkProviderImpl()
        provider.downloadMissing()
        val version = ProcessExecutor()
                .command(*provider.sdks.find { it.name.contains("ECJ") }!!.compilerCommand.toTypedArray(), "-version")
                .readOutput(true)
                .exitValueNormal()
                .execute()
                .outputUTF8()
        Assert.assertTrue(version.trim().matches("Eclipse Compiler .*".toRegex()))
    }
}