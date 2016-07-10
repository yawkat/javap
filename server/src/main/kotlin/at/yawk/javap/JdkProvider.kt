package at.yawk.javap

/**
 * @author yawkat
 */
interface JdkProvider {
    val defaultJdk: Jdk
    val jdks: List<Jdk>

    data class Jdk(
            val name: String,
            val javacPath: String
    )
}