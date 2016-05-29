package at.yawk.javap

import org.testng.Assert
import org.testng.annotations.Test
import org.zeroturnaround.exec.ProcessExecutor

/**
 * @author yawkat
 */
class JdkProviderImplTest {
    @Test
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