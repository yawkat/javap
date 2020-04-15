/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

/**
 * @author yawkat
 */
enum class SdkLanguage(val fileName: String) {
    JAVA("Main.java"),
    KOTLIN("Main.kt"),
    SCALA("Main.scala"),
}