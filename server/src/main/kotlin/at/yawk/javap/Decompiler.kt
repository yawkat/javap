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
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

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
            val classFiles = Files.newDirectoryStream(classDir).use { it.sorted() }
                    .filter { it.toString().endsWith(".class") }
                    // the class dir is writable by the sandbox, so don't follow links or read special files
                    .filter {
                        Files.readAttributes(it, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
                                .isRegularFile
                    }
            if (classFiles.size > MAX_DECOMPILE_CLASS_FILES) {
                return "Too many class files to decompile (${classFiles.size}, limit $MAX_DECOMPILE_CLASS_FILES)"
            }
            val totalSize = classFiles.sumOf {
                Files.readAttributes(it, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS).size()
            }
            if (totalSize > MAX_DECOMPILE_CLASS_BYTES) {
                return "Class files too large to decompile ($totalSize bytes, limit $MAX_DECOMPILE_CLASS_BYTES)"
            }

            val ctx = DecompilerContext(settings)
            val astBuilder = AstBuilder(ctx)
            var remaining = MAX_DECOMPILE_CLASS_BYTES
            for (classFile in classFiles) {
                // bounded read, in case a file changed after the size check
                val bytes = Files.newInputStream(classFile, LinkOption.NOFOLLOW_LINKS).use {
                    it.readNBytes((remaining + 1).toInt())
                }
                remaining -= bytes.size
                if (remaining < 0) {
                    return "Class files too large to decompile (limit $MAX_DECOMPILE_CLASS_BYTES bytes)"
                }
                val def = ClassFileReader.readClass(
                        ClassFileReader.OPTION_PROCESS_ANNOTATIONS or ClassFileReader.OPTION_PROCESS_CODE,
                        IMetadataResolver.EMPTY,
                        Buffer(bytes)
                )
                astBuilder.addType(def)
                // only need this to avoid errors. would be better to decompile separately, but... TODO
                ctx.currentType = def
            }
            val output = PlainTextOutput()
            astBuilder.generateCode(output)
            return output.toString()
        }
    };

    @Throws(Exception::class)
    abstract fun decompile(classDir: Path): String
}