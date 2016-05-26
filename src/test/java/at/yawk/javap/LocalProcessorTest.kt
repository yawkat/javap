package at.yawk.javap

import at.yawk.javap.model.ProcessingInput
import at.yawk.javap.model.ProcessingOutput
import org.testng.Assert.assertEquals
import org.testng.annotations.Test

/**
 * @author yawkat
 */

class LocalProcessorTest {
    val localProcessor = LocalProcessor(SystemJdkProvider)

    @Test
    fun `empty file`() {
        assertEquals(
                localProcessor.process(ProcessingInput("", SystemJdkProvider.JDK)),
                ProcessingOutput("", NO_CLASSES_GENERATED)
        )
    }

    @Test
    fun `compile error`() {
        assertEquals(
                localProcessor.process(ProcessingInput("abc", SystemJdkProvider.JDK)),
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
                localProcessor.process(ProcessingInput("class A { void a() { int i = 0; i++; } }", SystemJdkProvider.JDK)),
                ProcessingOutput("", """Compiled from "Main.java"
class A {
  A();
    descriptor: ()V
    Code:
       0: aload_0
       1: invokespecial #1                  // Method java/lang/Object."<init>":()V
       4: return
    LineNumberTable:
      line 1: 0
    LocalVariableTable:
      Start  Length  Slot  Name   Signature
          0       5     0  this   LA;

  void a();
    descriptor: ()V
    Code:
       0: iconst_0
       1: istore_1
       2: iinc          1, 1
       5: return
    LineNumberTable:
      line 1: 0
    LocalVariableTable:
      Start  Length  Slot  Name   Signature
          0       6     0  this   LA;
          2       4     1     i   I
}
""")
        )
    }
}