/*
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

import org.zeroturnaround.exec.ProcessExecutor
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Collect help files for all SDKs. Requires the SDK manifest (`nix build .#sdks -o sdk`).
 */
fun main() {
    val sdkProvider = SdkProviderImpl()
    sdkProvider.start()
    val root = Paths.get("help-files")
    Files.createDirectories(root)
    for (sdk in Sdks.sdksByName.values.distinct()) {
        println(sdk.name)
        val runnable = sdkProvider.lookupSdk(sdk)
        val helpFlags = when (sdk) {
            is Sdk.OpenJdk -> listOf("-help", "-X")
            is Sdk.Ecj -> listOf("-X", "-?:warn")
            is Sdk.Kotlin -> listOf("-help", "-X")
            is Sdk.Scala -> listOf("-help", "-X", "-Y", "-language:help") +
                    (if (sdk.release >= KotlinVersion(2, 12)) listOf("-opt:help", "-Xmixin-force-forwarders:help") else emptyList()) +
                    "-Xlint:help"
        }
        Files.newOutputStream(root.resolve(sdk.name)).use {
            for (flag in helpFlags) {
                ProcessExecutor()
                        .redirectOutput(it)
                        .redirectError(it)
                        .environment(runnable.env)
                        .command(runnable.compiler + flag)
                        .start().future.get()
            }
        }
    }
}
