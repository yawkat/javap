package at.yawk.javap

import org.zeroturnaround.exec.ProcessExecutor
import org.zeroturnaround.exec.ProcessResult
import java.nio.file.Path

class Bubblewrap {
    fun executeCommand(
            command: List<String>,
            workingDir: Path,
            writable: Set<Path> = emptySet(),
            readable: Set<Path> = setOf(workingDir),
            env: Map<String, String> = emptyMap(),
            runInJail: Boolean = true
    ): ProcessResult {
        if (!workingDir.startsWith("/tmp"))
            throw UnsupportedOperationException("Currently only /tmp is supported, verify security before allowing other paths")

        val combinedCommand: List<String>
        if (runInJail) {
            val bubblewrapCommand = listOf(
                    "bwrap",
                    "--unshare-all",
                    "--die-with-parent",
                    // basic runtime
                    "--ro-bind", "/usr", "/usr",
                    "--symlink", "/usr/lib", "/lib",
                    "--symlink", "/usr/lib64", "/lib64",
                    "--symlink", "/usr/bin", "/bin",
                    "--symlink", "/usr/sbin", "/sbin"
            ) + writable.flatMap {
                val abs = it.toAbsolutePath().toString()
                listOf("--bind", abs, abs)
            } + (readable - writable).flatMap {
                val abs = it.toAbsolutePath().toString()
                listOf("--ro-bind", abs, abs)
            } + env.flatMap { (k, v) ->
                listOf("--setenv", k, v)
            } + listOf("--chdir", workingDir.toString())

            combinedCommand = bubblewrapCommand + "--" + command
            println(combinedCommand.map { "'$it'" }.joinToString(" "))
        } else {
            combinedCommand = command
        }
        return ProcessExecutor()
                .command(combinedCommand)
                .directory(workingDir.toFile())
                .readOutput(true)
                .destroyOnExit()
                .execute()
    }
}