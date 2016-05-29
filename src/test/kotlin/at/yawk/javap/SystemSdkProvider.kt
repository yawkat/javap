package at.yawk.javap

/**
 * @author yawkat
 */
object SystemSdkProvider : SdkProvider {
    val JDK = "SYSTEM"

    override val defaultSdkByLanguage = mapOf(
            SdkLanguage.JAVA to Sdk(JDK, "javac", SdkLanguage.JAVA)
    )
    override val sdks = defaultSdkByLanguage.values.toList()
}