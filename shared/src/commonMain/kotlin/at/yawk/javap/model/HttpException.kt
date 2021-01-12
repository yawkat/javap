/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap.model

import kotlinx.serialization.Serializable

@Serializable
data class HttpException(val code: Int, override val message: String) : Exception(message)