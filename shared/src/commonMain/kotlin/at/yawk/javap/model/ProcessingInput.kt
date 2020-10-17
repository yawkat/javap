/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap.model

import at.yawk.javap.Sdks
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * @author yawkat
 */
data class ProcessingInput(
        val code: String,
        val compilerName: String,
        val compilerConfiguration: CompilerConfiguration
) {
    @ExperimentalSerializationApi
    @InternalSerializationApi
    @Serializer(forClass = ProcessingInput::class)
    companion object : KSerializer<ProcessingInput> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ProcessingInput") {
            element("code", String.serializer().descriptor)
            element("compilerName", String.serializer().descriptor)
            // TODO: use of buildSerialDescriptor is recommended against, explore replacements
            element("compilerConfiguration", buildSerialDescriptor("CompilerConfiguration", kind = SerialKind.CONTEXTUAL))
        }

        override fun deserialize(decoder: Decoder): ProcessingInput {
            var code: String? = null
            var compilerName: String? = null
            var compilerConfiguration: CompilerConfiguration? = null
            val structure = decoder.beginStructure(descriptor)
            loop@ while (true) {
                when (val i = structure.decodeElementIndex(descriptor)) {
                    CompositeDecoder.DECODE_DONE -> break@loop
                    0 -> code = structure.decodeStringElement(descriptor, i)
                    1 -> compilerName = structure.decodeStringElement(descriptor, i)
                    2 -> {
                        if (compilerName == null)
                            throw SerializationException("compilerName must appear before compilerConfiguration")
                        val serializer = ConfigProperties.serializers.getValue(
                                Sdks.sdksByName.getValue(compilerName).language)
                        compilerConfiguration = structure.decodeSerializableElement(descriptor, i, serializer)
                    }
                    else -> throw SerializationException("Unknown index $i")
                }
            }
            structure.endStructure(descriptor)
            return ProcessingInput(
                    code ?: throw SerializationException("code"),
                    compilerName ?: throw SerializationException("compilerName"),
                    compilerConfiguration ?: throw SerializationException("compilerConfiguration")
            )
        }

        override fun serialize(encoder: Encoder, value: ProcessingInput) {
            val structure = encoder.beginStructure(descriptor)
            structure.encodeStringElement(descriptor, 0, value.code)
            structure.encodeStringElement(descriptor, 1, value.compilerName)
            val configSerializer = ConfigProperties.serializers.getValue(
                    Sdks.sdksByName.getValue(value.compilerName).language)
            structure.encodeSerializableElement(descriptor, 2, configSerializer, value.compilerConfiguration)
            structure.endStructure(descriptor)
        }
    }
}