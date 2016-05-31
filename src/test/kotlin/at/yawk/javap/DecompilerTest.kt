package at.yawk.javap

import com.google.common.jimfs.Jimfs
import org.testng.Assert.*
import org.testng.annotations.Test
import java.nio.file.Files

/**
 * @author yawkat
 */
class DecompilerTest {
    @Test
    fun `decompile`() {
        val root = Jimfs.newFileSystem().rootDirectories.first()
        val className = X::class.java.name.replace('.', '/') + ".class"
        Files.copy(X::class.java.getResourceAsStream("/$className"), root.resolve("X.class"))

        assertTrue(Decompiler.PROCYON.decompile(root).contains("public static final class X"))
    }

    class X
}