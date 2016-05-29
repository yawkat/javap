package at.yawk.javap

import at.yawk.javap.model.Paste
import at.yawk.javap.model.ProcessingInput
import javax.inject.Inject

/**
 * @author yawkat
 */
class DefaultPaste @Inject constructor(sdkProvider: SdkProvider, processor: Processor) {
    private val input = ProcessingInput("""import java.util.*;
import java.lang.*;
import java.lang.invoke.*;
import java.lang.reflect.*;
import java.io.*;

public class Main {
    public Main() {
        int i = 0;
        i++;
    }
}""",
            sdkProvider.defaultSdk.name)
    val defaultPaste = Paste(DEFAULT_PASTE_NAME, "", input, processor.process(input))
}