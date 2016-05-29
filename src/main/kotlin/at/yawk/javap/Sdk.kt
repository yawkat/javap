package at.yawk.javap

/**
 * @author yawkat
 */
data class Sdk(
        val name: String,
        val compilerPath: String,
        val language: SdkLanguage
)