package at.yawk.javap

import org.testng.Assert
import org.testng.annotations.Test
import org.zeroturnaround.exec.ProcessExecutor
import java.nio.file.Paths

/**
 * @author yawkat
 */
class SdkProviderImplTest {
    @Test
    fun testDownload() {
        val provider = SdkProviderImpl()
        provider.downloadMissing()
        val version = ProcessExecutor()
                .command(provider.defaultSdk.compilerPath, "-version")
                .readOutput(true)
                .exitValueNormal()
                .execute()
                .outputUTF8()
        Assert.assertTrue(version.trim().matches("javac 1\\..*".toRegex()))
        //deleteRecursively(Paths.get("sdk"))
    }
}