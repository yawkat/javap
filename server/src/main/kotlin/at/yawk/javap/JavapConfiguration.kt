/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

import kotlinx.serialization.Serializable

/**
 * @author yawkat
 */
@Serializable
data class JavapConfiguration(
        val database: Database,
        val bindAddress: String = "127.0.0.1",
        val bindPort: Int = 8080
) {
    @Serializable
    data class Database(
            val user: String? = null,
            val password: String? = null,
            val url: String
    )
}