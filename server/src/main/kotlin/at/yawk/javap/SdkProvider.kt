package at.yawk.javap

/**
 * @author yawkat
 */
interface SdkProvider {
    val defaultSdkByLanguage: Map<SdkLanguage, Sdk>
    val sdks: List<Sdk>
}