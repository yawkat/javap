package at.yawk.javap

/**
 * @author yawkat
 */
object SystemJdkProvider : JdkProvider {
    val JDK = "SYSTEM"

    override val defaultJdk = JdkProvider.Jdk(JDK, "javac")
    override val jdks = listOf(defaultJdk)
}