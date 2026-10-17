/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

import org.testng.Assert
import org.testng.Assert.assertEquals
import org.testng.SkipException
import org.testng.annotations.BeforeClass
import org.testng.annotations.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * @author yawkat
 */
class BubblewrapTest {
    private val bubblewrap = Bubblewrap()

    @Test
    fun `whitelist reading`() {
        val tempDirectory = Files.createTempDirectory(null)
        try {
            Files.write(tempDirectory.resolve("test"), "test".toByteArray())

            assertEquals(bubblewrap.executeCommand(listOf("/bin/ls"), tempDirectory, readable = setOf(tempDirectory)).outputUTF8(), "test\n")
            assertEquals(bubblewrap.executeCommand(listOf("/bin/cat", "test"), tempDirectory, readable = setOf(tempDirectory)).outputUTF8(), "test")
        } finally {
            deleteRecursively(tempDirectory)
        }
    }

    @Test
    fun `allow writing writable directory`() {
        val tempDirectory = Files.createTempDirectory(null)
        try {
            val command = bubblewrap.executeCommand(listOf("/bin/touch", "test"), tempDirectory, writable = setOf(tempDirectory))
            Assert.assertEquals(command.exitValue, 0, command.outputUTF8())
            Assert.assertTrue(Files.isRegularFile(tempDirectory.resolve("test")))
        } finally {
            deleteRecursively(tempDirectory)
        }
    }

    @Test
    fun `output is bounded`() {
        val tempDirectory = Files.createTempDirectory(null)
        try {
            val command = bubblewrap.executeCommand(
                    listOf("/bin/sh", "-c", "yes 0123456789 | head -c ${MAX_PROCESS_OUTPUT_BYTES * 2}"),
                    tempDirectory
            )
            assertEquals(command.exitValue, 0)
            val expected = "0123456789\n".repeat(MAX_PROCESS_OUTPUT_BYTES / 11 + 1).substring(0, MAX_PROCESS_OUTPUT_BYTES)
            assertEquals(command.outputUTF8(), expected + OUTPUT_TRUNCATED_MARKER)
        } finally {
            deleteRecursively(tempDirectory)
        }
    }

    @Test
    fun `cannot access files outside whitelist`() {
        if (!Bubblewrap.AVAILABLE) {
            throw SkipException("Bubblewrap is not available")
        }
        val tempDirectory = Files.createTempDirectory(null)
        try {
            val command = bubblewrap.executeCommand(listOf("/bin/ls", "/opt"), tempDirectory)
            Assert.assertNotEquals(command.exitValue, 0, command.outputUTF8())
        } finally {
            deleteRecursively(tempDirectory)
        }
    }
}