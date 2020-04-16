/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

import org.zeroturnaround.exec.ProcessExecutor
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Collect help files for all SDKs
 */
fun main() {
    val root = Paths.get("help-files")
    Files.createDirectories(root)
    for (sdk in Sdks.sdksByName.values) {
        println(sdk.name)
        Files.newOutputStream(root.resolve(sdk.name)).use {
            fun runner() = ProcessExecutor()
                    .redirectOutput(it)
                    .redirectError(it)

            when (sdk) {
                is Sdk.OpenJdk -> {
                    runner().command("sdk/${sdk.name}/bin/javac", "-help").start().future.get()
                    runner().command("sdk/${sdk.name}/bin/javac", "-X").start().future.get()
                }
                is Sdk.Ecj -> {
                    runner().command("java", "-jar", "sdk/${sdk.name}/ecj.jar", "-X").start().future.get()
                    runner().command("java", "-jar", "sdk/${sdk.name}/ecj.jar", "-?:warn").start().future.get()
                }
                is Sdk.KotlinJar -> {
                    runner().command("java", "-jar", "sdk/${sdk.name}/kotlin-compiler.jar", "-help").start().future.get()
                    runner().command("java", "-jar", "sdk/${sdk.name}/kotlin-compiler.jar", "-X").start().future.get()
                }
                is Sdk.KotlinDistribution -> {
                    runner().command("sdk/${sdk.name}/bin/kotlinc", "-help").start().future.get()
                    runner().command("sdk/${sdk.name}/bin/kotlinc", "-X").start().future.get()
                }
                is Sdk.Scala -> {
                    runner().command("sdk/${sdk.name}/bin/scalac", "-help").start().future.get()
                    runner().command("sdk/${sdk.name}/bin/scalac", "-X").start().future.get()
                    runner().command("sdk/${sdk.name}/bin/scalac", "-Y").start().future.get()
                    runner().command("sdk/${sdk.name}/bin/scalac", "-language:help").start().future.get()
                    if (sdk.release >= KotlinVersion(2, 12)) {
                        runner().command("sdk/${sdk.name}/bin/scalac", "-opt:help").start().future.get()
                        runner().command("sdk/${sdk.name}/bin/scalac", "-Xmixin-force-forwarders:help").start().future.get()
                    }
                    runner().command("sdk/${sdk.name}/bin/scalac", "-Xlint:help").start().future.get()
                }
            }
        }
    }
}