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
class JdkProviderImplTest {
    @Test(enabled = false)
    fun testDownload() {
        val provider = JdkProviderImpl()
        provider.downloadMissing()
        val version = ProcessExecutor()
                .command(provider.defaultJdk.javacPath, "-version")
                .readOutput(true)
                .exitValueNormal()
                .execute()
                .outputUTF8()
        Assert.assertTrue(version.trim().matches("javac 1\\..*".toRegex()))
    }
}