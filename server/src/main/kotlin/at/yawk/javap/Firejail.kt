/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

import org.zeroturnaround.exec.ProcessExecutor
import org.zeroturnaround.exec.ProcessResult
import java.nio.file.Path
import java.util.*

/**
 * @author yawkat
 */
class Firejail {
    fun executeCommand(
            command: List<String>,
            workingDir: Path,
            whitelist: List<Path> = listOf(workingDir),
            readOnlyWhitelist: List<Path> = emptyList()
    ): ProcessResult {
        if (!workingDir.startsWith("/tmp"))
            throw UnsupportedOperationException("Currently only /tmp is supported, verify security before allowing other paths")
        val firejailCommand = listOf(
                "firejail",
                "--noprofile",
                "--quiet",
                "--caps",
                "--caps.drop=all",
                "--ipc-namespace",
                "--netfilter",
                "--net=none",
                "--nonewprivs",
                "--seccomp",
                "--nosound",
                "--name=${UUID.randomUUID()}",
                "--shell=none",
                "--blacklist=/var",
                "--blacklist=/opt",
                // make sure ~ and /tmp are blacklisted, need at least one whitelist entry for that
                "--whitelist=/tmp/.doesNotExist",
                "--whitelist=~/.doesNotExist",
                "--private-etc=/java-8-openjdk"
        ) + whitelist.map { it.toAbsolutePath() }.map { "--whitelist=$it" } +
                readOnlyWhitelist.map { it.toAbsolutePath() }.flatMap { listOf("--whitelist=$it", "--read-only=$it") }

        println((firejailCommand + "--" + command).map { if (it.contains(" ")) "'$it'" else it }.joinToString(" "))

        return ProcessExecutor()
                .command(firejailCommand + "--" + command)
                .directory(workingDir.toFile())
                .readOutput(true)
                .destroyOnExit()
                .execute()
    }
}