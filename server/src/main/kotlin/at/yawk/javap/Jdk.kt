/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

import java.nio.file.Path

/**
 * @author yawkat
 */
class Jdk(val path: Path) {
    val java = path.resolve("bin/java")!!
    val javap = path.resolve("bin/javap")!!
}