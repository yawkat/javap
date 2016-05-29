package at.yawk.javap

/**
 * @author yawkat
 */
object SystemSdkProvider : SdkProvider {
    val JDK = "SYSTEM"

    override val defaultSdk = Sdk(JDK, "javac", SdkLanguage.JAVA)
    override val sdks = listOf(defaultSdk)
}