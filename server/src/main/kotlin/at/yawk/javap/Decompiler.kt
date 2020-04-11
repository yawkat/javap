/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

import com.strobel.assembler.metadata.Buffer
import com.strobel.assembler.metadata.ClassFileReader
import com.strobel.assembler.metadata.IMetadataResolver
import com.strobel.assembler.metadata.MetadataSystem
import com.strobel.decompiler.DecompilerContext
import com.strobel.decompiler.DecompilerSettings
import com.strobel.decompiler.PlainTextOutput
import com.strobel.decompiler.languages.java.BraceStyle
import com.strobel.decompiler.languages.java.JavaFormattingOptions
import com.strobel.decompiler.languages.java.ast.AstBuilder
import java.nio.file.Files
import java.nio.file.Path

/**
 * @author yawkat
 */
enum class Decompiler {
    PROCYON {
        private val settings = DecompilerSettings()

        init {
            // this ensures correct initialization order - removing this line will lead to an Exception in procyon code
            // (try it)
            MetadataSystem.instance()

            settings.forceExplicitImports = true
            settings.showSyntheticMembers = true
            settings.javaFormattingOptions = JavaFormattingOptions.createDefault()
            settings.javaFormattingOptions.ClassBraceStyle = BraceStyle.EndOfLine
            settings.javaFormattingOptions.EnumBraceStyle = BraceStyle.EndOfLine
        }

        override fun decompile(classDir: Path): String {
            val ctx = DecompilerContext(settings)
            val astBuilder = AstBuilder(ctx)
            Files.newDirectoryStream(classDir).use {
                for (classFile in it.sorted()) {
                    if (!classFile.toString().endsWith(".class")) continue
                    val def = ClassFileReader.readClass(
                            ClassFileReader.OPTION_PROCESS_ANNOTATIONS or ClassFileReader.OPTION_PROCESS_CODE,
                            IMetadataResolver.EMPTY,
                            Buffer(Files.readAllBytes(classFile))
                    )
                    astBuilder.addType(def)
                    // only need this to avoid errors. would be better to decompile separately, but... TODO
                    ctx.currentType = def
                }
            }
            val output = PlainTextOutput()
            astBuilder.generateCode(output)
            return output.toString()
        }
    };

    @Throws(Exception::class)
    abstract fun decompile(classDir: Path): String
}