/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap.model

/**
 * @author yawkat
 */
data class Paste(
        val id: String,
        val ownerToken: String,
        val editable: Boolean,

        val input: ProcessingInput,
        val output: ProcessingOutput
) {
    companion object {
        fun fromJson(it: dynamic) = Paste(
                id = it.id,
                ownerToken = it.ownerToken,
                editable = it.editable,
                input = ProcessingInput(code = it.input.code, compilerName = it.input.compilerName),
                output = ProcessingOutput(
                        compilerLog = it.output.compilerLog,
                        javap = it.output.javap,
                        procyon = it.output.procyon
                )
        )
    }
}