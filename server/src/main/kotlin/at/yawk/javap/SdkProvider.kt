/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

import at.yawk.javap.model.CompilerConfiguration
import java.nio.file.Path

/**
 * @author yawkat
 */
interface SdkProvider {
    fun lookupSdk(sdk: Sdk): RunnableSdk
}

abstract class RunnableSdk(val sdk: Sdk) {
    abstract val jdkHome: Path
    abstract val readable: Set<Path>
    abstract val libraryPath: List<Path>

    abstract fun compilerCommand(inputFile: Path, outputDir: Path, config: CompilerConfiguration): List<String>
}