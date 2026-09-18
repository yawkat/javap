/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

import java.nio.file.Paths

/**
 * @author yawkat
 */
object SystemSdkProvider : SdkProvider {
    override fun lookupSdk(sdk: Sdk): RunnableSdk {
        require(sdk is Sdk.Java)
        var jdkHome = Paths.get(
                System.getenv("JAVA_HOME") ?: System.getProperty("java.home") ?: "/usr/lib/jvm/default")
        if (jdkHome.endsWith("jre")) jdkHome = jdkHome.parent
        return RunnableSdk(
                sdk,
                compiler = listOf(jdkHome.resolve("bin/javac").toAbsolutePath().toString(), "-encoding", "utf-8"),
                compilerWithLombok = null,
                javap = listOf(jdkHome.resolve("bin/javap").toAbsolutePath().toString()),
                env = mapOf(
                        "JAVA_HOME" to jdkHome.toAbsolutePath().toString(),
                        "LD_LIBRARY_PATH" to listOf(
                                "/usr/lib/jvm/default/lib/amd64",
                                "/usr/lib/jvm/default/lib/amd64/jli",
                                "/usr/lib/jvm/default/lib"
                        ).joinToString(":")
                ),
                readable = setOf(Paths.get("/etc"), jdkHome)
        )
    }
}
