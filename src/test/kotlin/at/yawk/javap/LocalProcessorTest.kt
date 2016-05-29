package at.yawk.javap

import at.yawk.javap.model.ProcessingInput
import at.yawk.javap.model.ProcessingOutput
import org.testng.Assert.assertEquals
import org.testng.annotations.Test

/**
 * @author yawkat
 */

class LocalProcessorTest {
    val localProcessor = LocalProcessor(SystemSdkProvider)

    @Test
    fun `empty file`() {
        assertEquals(
                localProcessor.process(ProcessingInput("", SystemSdkProvider.JDK)),
                ProcessingOutput("", NO_CLASSES_GENERATED)
        )
    }

    @Test
    fun `compile error`() {
        assertEquals(
                localProcessor.process(ProcessingInput("abc", SystemSdkProvider.JDK)),
                ProcessingOutput("""Main.java:1: error: reached end of file while parsing
abc
^
1 error
""", null)
        )
    }

    @Test
    fun `simple program`() {
        assertEquals(
                localProcessor.process(ProcessingInput("class A { void a() { int i = 0; i++; } }", SystemSdkProvider.JDK)),
                ProcessingOutput("", """Compiled from "Main.java"
class A
  minor version: 0
  major version: 52
  flags: ACC_SUPER
{
  A();
    descriptor: ()V
    flags:
    Code:
      stack=1, locals=1, args_size=1
        start local 0 // A this
         0: aload_0
         1: invokespecial #1                  // Method java/lang/Object."<init>":()V
         4: return
        end local 0 // A this
      LineNumberTable:
        line 1: 0
      LocalVariableTable:
        Start  Length  Slot  Name   Signature
            0       5     0  this   LA;

  void a();
    descriptor: ()V
    flags:
    Code:
      stack=1, locals=2, args_size=1
        start local 0 // A this
         0: iconst_0
         1: istore_1
        start local 1 // int i
         2: iinc          1, 1
         5: return
        end local 1 // int i
        end local 0 // A this
      LineNumberTable:
        line 1: 0
      LocalVariableTable:
        Start  Length  Slot  Name   Signature
            0       6     0  this   LA;
            2       4     1     i   I
}
SourceFile: "Main.java"
""")
        )
    }
}