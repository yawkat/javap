/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

import at.yawk.javap.model.CompilerConfiguration
import at.yawk.javap.model.ConfigProperties
import java.nio.file.Path

/**
 * @author yawkat
 */
interface SdkProvider {
    fun lookupSdk(sdk: Sdk): RunnableSdk
}

/**
 * How to invoke an SDK. See `nix/sdks.nix` for details.
 */
class RunnableSdk(
        val sdk: Sdk,
        /**
         * Base compiler command. Output directory, options and input file are appended.
         */
        val compiler: List<String>,
        /**
         * Alternative to [compiler] when lombok is enabled, or `null` if lombok is not supported.
         */
        private val compilerWithLombok: List<String>?,
        val javap: List<String>,
        val env: Map<String, String>,
        /**
         * Paths that need to be readable by the sandboxed compiler and javap.
         */
        val readable: Set<Path>
) {
    fun compilerCommand(inputFile: Path, outputDir: Path, config: CompilerConfiguration): List<String> {
        val base = if (compilerWithLombok != null && ConfigProperties.lombok.get(config)) compilerWithLombok else compiler
        return base + listOf("-d", outputDir.toString()) +
                ConfigProperties.validateAndBuildCommandLine(sdk, config) +
                inputFile.toString()
    }
}
