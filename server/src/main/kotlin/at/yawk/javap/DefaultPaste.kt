/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

import at.yawk.javap.model.CompilerConfiguration
import at.yawk.javap.model.Paste
import at.yawk.javap.model.ProcessingInput
import java.lang.Exception
import java.util.stream.Collectors

/**
 * @author yawkat
 */
class DefaultPaste constructor(processor: Processor) {
    val defaultPastes: Map<String, Paste>

    init {
        fun createDefaultPaste(language: SdkLanguage, sdk: Sdk): Paste {
            val code = when (language) {
                SdkLanguage.JAVA -> """import java.util.*;
import lombok.*;

public class Main {
    public Main() {
        int i = 0;
        i++;
    }
}"""
                SdkLanguage.KOTLIN -> """import java.util.*
import kotlinx.coroutines.*

class Main() {
    init {
        var i = 0
        i++
    }
}"""
                SdkLanguage.SCALA -> """object Main {
    def test(i: Int) = i + 1
}"""
            }
            val input = ProcessingInput(code, sdk.name, emptyMap())
            val output = processor.process(input)
            if (output.javap == null) {
                throw Exception("Compilation error in default paste: ${output.compilerLog}")
            }
            return Paste("default:$language", "", input, output)
        }

        @Suppress("USELESS_CAST")
        defaultPastes = (Sdks.defaultSdks.entries as Set<Map.Entry<SdkLanguage, Sdk>>).stream()
                .parallel()
                .map { (language, sdk) ->
                    createDefaultPaste(language, sdk)
                }
                .collect(Collectors.toMap({ it.id }, { it }))
    }
}