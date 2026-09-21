/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * [SdkProvider] backed by the SDK manifest built by nix (`nix build .#sdks -o sdk`).
 */
class SdkProviderImpl(
    private val manifest: Path = Paths.get("sdk/sdks.json")
) : SdkProvider {
    private lateinit var sdks: Map<Sdk, RunnableSdk>

    @Serializable
    private class ManifestEntry(
        val compiler: List<String>,
        val compilerWithLombok: List<String>? = null,
        val javap: List<String>,
        val env: Map<String, String> = emptyMap(),
        val readable: List<String>
    )

    fun start() {
        val entries = Json.decodeFromString(
            MapSerializer(String.serializer(), ManifestEntry.serializer()),
            Files.readString(manifest)
        )
        sdks = Sdks.sdksByName.values.distinct().associateWith { sdk ->
            val entry = entries[sdk.name]
                ?: throw IllegalStateException("SDK ${sdk.name} missing from manifest $manifest, rebuild it with nix")
            RunnableSdk(
                sdk,
                compiler = entry.compiler,
                compilerWithLombok = entry.compilerWithLombok,
                javap = entry.javap,
                env = entry.env,
                readable = entry.readable.mapTo(mutableSetOf()) { Paths.get(it) }
            )
        }
    }

    override fun lookupSdk(sdk: Sdk) = sdks.getValue(sdk)
}
