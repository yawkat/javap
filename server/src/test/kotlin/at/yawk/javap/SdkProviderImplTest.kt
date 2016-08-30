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