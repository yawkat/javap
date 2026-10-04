/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

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

    @Test
    fun `too many class files are skipped`() {
        val root = Jimfs.newFileSystem().rootDirectories.first()
        for (i in 0..MAX_DECOMPILE_CLASS_FILES) {
            Files.createFile(root.resolve("C$i.class"))
        }

        assertTrue(Decompiler.PROCYON.decompile(root).startsWith("Too many class files"))
    }

    @Test
    fun `too large class files are skipped`() {
        val root = Jimfs.newFileSystem().rootDirectories.first()
        Files.write(root.resolve("A.class"), ByteArray(MAX_DECOMPILE_CLASS_BYTES.toInt() / 2 + 1))
        Files.write(root.resolve("B.class"), ByteArray(MAX_DECOMPILE_CLASS_BYTES.toInt() / 2 + 1))

        assertTrue(Decompiler.PROCYON.decompile(root).startsWith("Class files too large"))
    }

    @Test
    fun `links are not followed`() {
        val fs = Jimfs.newFileSystem()
        val root = fs.getPath("/classes")
        Files.createDirectory(root)
        val className = X::class.java.name.replace('.', '/') + ".class"
        Files.copy(X::class.java.getResourceAsStream("/$className"), root.resolve("X.class"))
        // would be skipped as too large if the link were followed
        val outside = fs.getPath("/outside.class")
        Files.write(outside, ByteArray(MAX_DECOMPILE_CLASS_BYTES.toInt() + 1))
        Files.createSymbolicLink(root.resolve("Y.class"), outside)
        Files.createDirectory(root.resolve("Z.class"))

        assertTrue(Decompiler.PROCYON.decompile(root).contains("public static final class X"))
    }

    class X
}