package at.yawk.javap.model

import org.testng.Assert
import org.testng.annotations.Test
import at.yawk.javap.Sdks

class CompilerConfigurationTest {
    @Test
    fun legacyJavaCommandLine() {
        val legacy = emptyMap<String, Any?>()
        Assert.assertTrue(ConfigProperties.lombok.get(legacy))
        val options = ConfigProperties.validateAndBuildCommandLine(Sdks.defaultJava, legacy)
        Assert.assertEquals(options, listOf("-g"))
    }
}