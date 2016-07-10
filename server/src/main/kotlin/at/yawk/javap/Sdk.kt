package at.yawk.javap

/**
 * @author yawkat
 */
data class Sdk(
        val name: String,
        val compilerCommand: List<String>,
        val language: SdkLanguage
)