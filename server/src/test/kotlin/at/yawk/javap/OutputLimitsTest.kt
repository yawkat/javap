/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

import org.testng.Assert.assertEquals
import org.testng.Assert.assertFalse
import org.testng.Assert.assertTrue
import org.testng.annotations.Test

class OutputLimitsTest {
    @Test
    fun `bounded stream below limit`() {
        val stream = BoundedOutputStream(10)
        stream.write("abc".toByteArray())
        stream.write('d'.code)
        assertFalse(stream.truncated)
        assertEquals(String(stream.toByteArray()), "abcd")
    }

    @Test
    fun `bounded stream exactly at limit`() {
        val stream = BoundedOutputStream(4)
        stream.write("abcd".toByteArray())
        assertFalse(stream.truncated)
        assertEquals(String(stream.toByteArray()), "abcd")
    }

    @Test
    fun `bounded stream over limit`() {
        val stream = BoundedOutputStream(5)
        stream.write("abc".toByteArray())
        stream.write("defgh".toByteArray())
        stream.write('i'.code)
        stream.write(ByteArray(1000), 10, 500)
        assertTrue(stream.truncated)
        assertEquals(String(stream.toByteArray()), "abcde$OUTPUT_TRUNCATED_MARKER")
    }

    @Test
    fun `truncate output`() {
        assertEquals("abc".truncateOutput(3), "abc")
        assertEquals("abcd".truncateOutput(3), "abc$OUTPUT_TRUNCATED_MARKER")
        // surrogate pair is not split
        assertEquals("ab😀".truncateOutput(3), "ab$OUTPUT_TRUNCATED_MARKER")
    }
}
