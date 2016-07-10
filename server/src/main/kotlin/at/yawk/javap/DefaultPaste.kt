package at.yawk.javap

import at.yawk.javap.model.Paste
import at.yawk.javap.model.ProcessingInput
import javax.inject.Inject

/**
 * @author yawkat
 */
class DefaultPaste @Inject constructor(sdkProvider: SdkProvider, processor: Processor) {
    val defaultPastes = sdkProvider.defaultSdkByLanguage.map {
        val code = when(it.key) {
            SdkLanguage.JAVA -> """import java.util.*;
import java.lang.*;
import java.lang.invoke.*;
import java.lang.reflect.*;
import java.io.*;

public class Main {
    public Main() {
        int i = 0;
        i++;
    }
}"""
                    SdkLanguage.KOTLIN -> """import java.util.*

class Main() {
    init {
        var i = 0
        i++
    }
}"""
        }
        val input = ProcessingInput(code, it.value.name)
        Paste("default:${it.key}", "", input, processor.process(input))
    }
}