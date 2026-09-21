package at.yawk.javap

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * SDK metadata. The SDKs are defined in `nix/sdks.nix`, see there for details.
 */
sealed class Sdk(
        val language: SdkLanguage
) {
    abstract val name: String

    /**
     * Previous SDK names that should now be treated as this SDK
     */
    abstract val aliases: Set<String>

    interface HasLint {
        val supportedWarnings: Set<String>
    }

    interface Java : HasLint {
        val release: Int
        val hasLombok: Boolean
    }

    data class OpenJdk(
            override val release: Int,
            override val name: String,
            override val hasLombok: Boolean,
            override val supportedWarnings: Set<String>,
            override val aliases: Set<String> = emptySet()
    ) : Sdk(SdkLanguage.JAVA), Java

    data class Ecj(
            override val release: Int,
            override val name: String,
            override val hasLombok: Boolean,
            override val supportedWarnings: Set<String>,
            override val aliases: Set<String> = emptySet()
    ) : Sdk(SdkLanguage.JAVA), Java

    data class Kotlin(
            val release: KotlinVersion,
            override val name: String,
            override val aliases: Set<String> = emptySet()
    ) : Sdk(SdkLanguage.KOTLIN)

    data class Scala(
            val release: KotlinVersion,
            override val name: String,
            override val supportedWarnings: Set<String>,
            override val aliases: Set<String> = emptySet()
    ) : Sdk(SdkLanguage.SCALA), HasLint
}

@Serializable
private class SdkMetadata(
        val groups: List<Group>,
        val defaults: Map<SdkLanguage, String>
) {
    @Serializable
    class Group(val label: String, val sdks: List<Entry>)

    @Serializable
    class Entry(
            val compiler: Compiler,
            val name: String,
            val aliases: Set<String>,
            val release: String,
            val hasLombok: Boolean,
            val supportedWarnings: Set<String> = emptySet()
    ) {
        fun toSdk(): Sdk = when (compiler) {
            Compiler.JAVAC -> Sdk.OpenJdk(release.toInt(), name, hasLombok, supportedWarnings, aliases)
            Compiler.ECJ -> Sdk.Ecj(release.toInt(), name, hasLombok, supportedWarnings, aliases)
            Compiler.KOTLINC -> Sdk.Kotlin(parseVersion(release), name, aliases)
            Compiler.SCALAC -> Sdk.Scala(parseVersion(release), name, supportedWarnings, aliases)
        }
    }

    @Serializable
    enum class Compiler {
        @SerialName("javac") JAVAC,
        @SerialName("ecj") ECJ,
        @SerialName("kotlinc") KOTLINC,
        @SerialName("scalac") SCALAC,
    }
}

private fun parseVersion(s: String): KotlinVersion {
    val parts = s.split('.').map { it.toInt() }
    return KotlinVersion(parts[0], parts[1], parts.getOrElse(2) { 0 })
}

object Sdks {
    val sdkByLabel: Map<String, List<Sdk>>
    val defaultSdks: Map<SdkLanguage, Sdk>
    val sdksByName: Map<String, Sdk>

    init {
        val metadata = Json.decodeFromString(SdkMetadata.serializer(), SDK_METADATA_JSON)
        sdkByLabel = metadata.groups.associate { group -> group.label to group.sdks.map { it.toSdk() } }

        val sdksByName = mutableMapOf<String, Sdk>()
        for (sdk in sdkByLabel.values.flatten()) {
            sdksByName[sdk.name] = sdk
            for (alias in sdk.aliases) {
                sdksByName[alias] = sdk
            }
        }
        this.sdksByName = sdksByName

        defaultSdks = metadata.defaults.mapValues { (_, name) -> sdksByName.getValue(name) }
    }

    val defaultJava = defaultSdks.getValue(SdkLanguage.JAVA)

    val allSupportedWarnings: Set<String> = sdkByLabel.values.flatten()
            .filterIsInstance<Sdk.HasLint>()
            .flatMapTo(mutableSetOf()) { it.supportedWarnings }
}
