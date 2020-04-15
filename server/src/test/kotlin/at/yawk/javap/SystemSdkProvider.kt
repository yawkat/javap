/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

import at.yawk.javap.model.CompilerConfiguration
import at.yawk.javap.model.ConfigProperties
import java.nio.file.Path
import java.nio.file.Paths

/**
 * @author yawkat
 */
object SystemSdkProvider : SdkProvider {
    override fun lookupSdk(sdk: Sdk): RunnableSdk {
        require(sdk is Sdk.Java)
        return object : RunnableSdk(sdk) {
            override val jdkHome: Path
                get() {
                    var path = Paths.get(
                            System.getenv("JAVA_HOME") ?: System.getProperty("java.home") ?: "/usr/lib/jvm/default")
                    if (path.endsWith("jre")) path = path.parent
                    return path
                }
            override val readable: Set<Path>
                get() = setOf(Paths.get("/etc"), jdkHome)
            override val libraryPath: List<Path>
                get() = listOf(
                        Paths.get("/usr/lib/jvm/default/lib/amd64"),
                        Paths.get("/usr/lib/jvm/default/lib/amd64/jli"),
                        Paths.get("/usr/lib/jvm/default/lib")
                )

            override fun compilerCommand(inputFile: Path, outputDir: Path, config: CompilerConfiguration) = listOf(
                    jdkHome.resolve("bin/javac").toAbsolutePath().toString(),
                    "-encoding", "utf-8",
                    "-d", outputDir.toString()
            ) + ConfigProperties.validateAndBuildCommandLine(sdk, config) + inputFile.toString()
        }
    }
}