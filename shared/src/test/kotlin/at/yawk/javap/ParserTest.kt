package at.yawk.javap

import org.testng.Assert
import org.testng.annotations.Test

class ParserTest {
    @Test
    fun simple() {
        val javap = """
            Classfile /tmp/2851078679449397319/classes/Main.class
              Last modified Apr 11, 2020; size 272 bytes
              SHA-256 checksum e2fc7a94cb8da7907dbae8440a7728ce3b22ef28a88e67f6146c51fdfc744591
              Compiled from "Main.java"
            public class Main
              minor version: 0
              major version: 57
              flags: (0x0021) ACC_PUBLIC, ACC_SUPER
              this_class: #7                          // Main
              super_class: #2                         // java/lang/Object
              interfaces: 0, fields: 0, methods: 1, attributes: 1
            Constant pool:
               #1 = Methodref          #2.#3          // java/lang/Object."<init>":()V
               #2 = Class              #4             // java/lang/Object
               #3 = NameAndType        #5:#6          // "<init>":()V
               #4 = Utf8               java/lang/Object
               #5 = Utf8               <init>
               #6 = Utf8               ()V
               #7 = Class              #8             // Main
               #8 = Utf8               Main
               #9 = Utf8               Code
              #10 = Utf8               LineNumberTable
              #11 = Utf8               LocalVariableTable
              #12 = Utf8               this
              #13 = Utf8               LMain;
              #14 = Utf8               i
              #15 = Utf8               I
              #16 = Utf8               SourceFile
              #17 = Utf8               Main.java
            {
              public Main();
                descriptor: ()V
                flags: (0x0001) ACC_PUBLIC
                Code:
                  stack=1, locals=2, args_size=1
                    start local 0 // Main this
                     0: aload_0
                     1: invokespecial #1                  // Method java/lang/Object."<init>":()V
                     4: iconst_0
                     5: istore_1
                    start local 1 // int i
                     6: iinc          1, 1
                     9: return
                    end local 1 // int i
                    end local 0 // Main this
                  LineNumberTable:
                    line 5: 0
                    line 6: 4
                    line 7: 6
                    line 8: 9
                  LocalVariableTable:
                    Start  Length  Slot  Name   Signature
                        0      10     0  this   LMain;
                        6       4     1     i   I
            }
            SourceFile: "Main.java"
        """.trimIndent()
        val metadata = extractJavapMetadata(javap.split('\n'))
        Assert.assertEquals(
                metadata,
                JavapMetadata(
                        lineMappings = listOf(
                                LineMapping(sourceLines = 4..4, javapLines = 36..37),
                                LineMapping(sourceLines = 5..5, javapLines = 38..40),
                                LineMapping(sourceLines = 6..6, javapLines = 41..41),
                                LineMapping(sourceLines = 7..7, javapLines = 42..44)
                        ),
                        fileStarts = mapOf("Main.class" to 0),
                        regions = listOf(
                                ExpanderRegion(range = 11..28, openByDefault = false),
                                ExpanderRegion(range = 45..49, openByDefault = false),
                                ExpanderRegion(range = 50..53, openByDefault = true),
                                ExpanderRegion(range = 33..53, openByDefault = true)
                        )
                )
        )
    }
}