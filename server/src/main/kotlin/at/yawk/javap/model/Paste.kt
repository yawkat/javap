/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * @author yawkat
 */
@JsonIgnoreProperties("ownerToken")
data class Paste(
        val id: String,
        val ownerToken: String,

        val input: ProcessingInput,
        val output: ProcessingOutput
)