/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

import com.google.common.annotations.VisibleForTesting
import org.slf4j.LoggerFactory
import org.zeroturnaround.exec.ProcessExecutor
import org.zeroturnaround.exec.ProcessResult
import java.nio.file.Path
import java.util.*

/**
 * @author yawkat
 */
private val log = LoggerFactory.getLogger(Firejail::class.java)

class Firejail {
    @VisibleForTesting
    internal val enableJail = System.getProperty("enableFirejail", "true").toBoolean()

    fun executeCommand(
            command: List<String>,
            workingDir: Path,
            whitelist: List<Path> = listOf(workingDir),
            readOnlyWhitelist: List<Path> = emptyList()
    ): ProcessResult {
        if (!workingDir.startsWith("/tmp"))
            throw UnsupportedOperationException("Currently only /tmp is supported, verify security before allowing other paths")

        val combinedCommand: List<String>
        if (enableJail) {
            val firejailCommand = listOf(
                    "firejail",
                    "--force",
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

            combinedCommand = firejailCommand + "--" + command
        } else {
            combinedCommand = command
            log.warn("Firejail is disabled. Command execution is not sandboxed!")
        }
        return ProcessExecutor()
                .command(combinedCommand)
                .directory(workingDir.toFile())
                .readOutput(true)
                .destroyOnExit()
                .execute()
    }
}