package at.yawk.javap

/**
 * @author yawkat
 */
interface SdkProvider {
    val defaultSdk: Sdk
    val sdks: List<Sdk>
}