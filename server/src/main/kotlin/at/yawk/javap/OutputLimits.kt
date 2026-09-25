/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * Maximum number of bytes of (combined stdout / stderr) output kept per subprocess (compiler, javap).
 */
const val MAX_PROCESS_OUTPUT_BYTES = 2 * 1024 * 1024

/**
 * Maximum number of class files passed to the decompiler.
 */
const val MAX_DECOMPILE_CLASS_FILES = 1000

/**
 * Maximum total size of the class files passed to the decompiler.
 */
const val MAX_DECOMPILE_CLASS_BYTES = 2L * 1024 * 1024

/**
 * Maximum length (in chars) of each of the compiler log, javap and procyon output fields that is stored.
 */
const val MAX_STORED_OUTPUT_LENGTH = 4 * 1024 * 1024

const val OUTPUT_TRUNCATED_MARKER = "\n[output truncated]"

/**
 * [OutputStream] that keeps the first [limit] bytes written to it and silently discards the rest, so that a process
 * can still be drained completely without buffering all of its output.
 */
class BoundedOutputStream(private val limit: Int) : OutputStream() {
    private val buffer = ByteArrayOutputStream()

    /**
     * Whether any bytes were discarded.
     */
    @get:Synchronized
    var truncated = false
        private set

    @Synchronized
    override fun write(b: Int) {
        if (buffer.size() < limit) {
            buffer.write(b)
        } else {
            truncated = true
        }
    }

    @Synchronized
    override fun write(b: ByteArray, off: Int, len: Int) {
        val keep = minOf(len, limit - buffer.size())
        if (keep > 0) {
            buffer.write(b, off, keep)
        }
        if (keep < len) {
            truncated = true
        }
    }

    /**
     * The retained bytes, followed by [OUTPUT_TRUNCATED_MARKER] if anything was discarded.
     */
    @Synchronized
    fun toByteArray(): ByteArray {
        val bytes = buffer.toByteArray()
        return if (truncated) bytes + OUTPUT_TRUNCATED_MARKER.toByteArray(Charsets.UTF_8) else bytes
    }
}

/**
 * Truncate this string to at most [maxLength] chars (plus [OUTPUT_TRUNCATED_MARKER] if it was truncated).
 */
fun String.truncateOutput(maxLength: Int = MAX_STORED_OUTPUT_LENGTH): String {
    if (length <= maxLength) return this
    var end = maxLength
    // don't split a surrogate pair
    if (end > 0 && Character.isHighSurrogate(this[end - 1])) end--
    return substring(0, end) + OUTPUT_TRUNCATED_MARKER
}
