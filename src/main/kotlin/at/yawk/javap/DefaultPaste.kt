package at.yawk.javap

import at.yawk.javap.model.Paste
import at.yawk.javap.model.ProcessingInput
import javax.inject.Inject

/**
 * @author yawkat
 */
class DefaultPaste @Inject constructor(sdkProvider: SdkProvider, processor: Processor) {
    private val javaInput = ProcessingInput("""import java.util.*;
import java.lang.*;
import java.lang.invoke.*;
import java.lang.reflect.*;
import java.io.*;

public class Main {
    public Main() {
        int i = 0;
        i++;
    }
}""", sdkProvider.defaultSdkByLanguage[SdkLanguage.JAVA]!!.name)
    private val kotlinInput = ProcessingInput("""import java.util.*

class Main() {
    init {
        var i = 0
        i++
    }
}""", sdkProvider.defaultSdkByLanguage[SdkLanguage.KOTLIN]!!.name)
    val defaultPastes = listOf(
            Paste("default:JAVA", "", javaInput, processor.process(javaInput)),
            Paste("default:KOTLIN", "", kotlinInput, processor.process(kotlinInput))
    )
}