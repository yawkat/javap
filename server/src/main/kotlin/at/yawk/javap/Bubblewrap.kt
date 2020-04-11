package at.yawk.javap

import org.zeroturnaround.exec.ProcessExecutor
import org.zeroturnaround.exec.ProcessResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class Bubblewrap {
    private val basicCommand: List<String>

    init {
        val bindLocations = listOf("/usr", "/lib", "/lib64", "/bin", "/sbin")
                .map { Paths.get(it) }
                .filter { Files.exists(it) }
        basicCommand = listOf(
                "bwrap",
                "--unshare-all",
                "--die-with-parent",
                "--proc", "/proc"
        ) +
                // bind the normal files
                bindLocations.filter { !Files.isSymbolicLink(it) }
                        .flatMap { listOf("--ro-bind", it.toString(), it.toString()) } +
                // copy the links
                bindLocations.filter { Files.isSymbolicLink(it) }
                        .flatMap { listOf("--symlink", Files.readSymbolicLink(it).toString(), it.toString()) }
    }

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
            val bubblewrapCommand = basicCommand + writable.flatMap {
                val abs = it.toAbsolutePath().toString()
                listOf("--bind", abs, abs)
            } + (readable - writable).flatMap {
                val abs = it.toAbsolutePath().toString()
                listOf("--ro-bind", abs, abs)
            } + env.flatMap { (k, v) ->
                listOf("--setenv", k, v)
            } + listOf("--chdir", workingDir.toString())

            // would like to add -- before the command but that's not available on all versions
            combinedCommand = bubblewrapCommand + command
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