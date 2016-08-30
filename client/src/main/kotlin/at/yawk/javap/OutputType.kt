/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

import at.yawk.javap.model.ProcessingOutput

/**
 * @author yawkat
 */
enum class OutputType(val getter: (ProcessingOutput) -> String?) {
    compilerLog({ it.compilerLog }),
    javap({ it.javap }),
    procyon({ it.procyon }),
}