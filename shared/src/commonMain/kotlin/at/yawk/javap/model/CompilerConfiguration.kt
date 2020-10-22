package at.yawk.javap.model

import at.yawk.javap.Sdk
import at.yawk.javap.SdkLanguage
import at.yawk.javap.Sdks
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

typealias CompilerConfiguration = Map<String, Any?>

object ConfigProperties {

    fun validateAndBuildCommandLine(sdk: Sdk, config: CompilerConfiguration): List<String> {
        val out = mutableListOf<String>()
        val properties: Iterable<ConfigProperty<*>> = properties.getValue(sdk.language)
        val remaining = config.keys.toMutableSet()
        for (property in properties) {
            if (property.canApplyTo(sdk)) {
                if (remaining.remove(property.id)) {
                    property.validateFrom(sdk, config)
                    property.applyFrom(out, config)
                } // else not set
            }
        }
        require(remaining.isEmpty())
        if (sdk is Sdk.Java) {
            val debugLines = config.getOrElse("debugLines") { true } as Boolean
            val debugSource = config.getOrElse("debugSource") { true } as Boolean
            val debugVars = config.getOrElse("debugVars") { true } as Boolean
            // build -g
            if (debugLines && debugSource && debugVars) {
                out.add("-g")
            } else if (!debugLines && !debugSource && !debugVars) {
                out.add("-g:none")
            } else {
                out.add("-g:" + listOfNotNull(
                        if (debugLines) "lines" else null,
                        if (debugVars) "vars" else null,
                        if (debugSource) "source" else null
                ).joinToString(","))
            }
            val warningsAsErrors = config.getOrElse("warningsAsErrors") { false } as Boolean
            if (warningsAsErrors) {
                if (sdk is Sdk.Ecj) {
                    out.add("-failOnWarning")
                } else {
                    out.add("-Werror")
                }
            }
        } else if (sdk is Sdk.Scala) {
            val languageOpts = listOf("dynamics", "postfixOps", "reflectiveCalls", "implicitConversions",
                    "higherKinds", "existentials")
            if (languageOpts.any { config[it] == true }) {
                out.add("-language:" + languageOpts.filter { config[it] == true }.joinToString(","))
            }
        }
        if (sdk is Sdk.HasLint) {
            val lint = lint.get(config)
            if (lint != null) {
                when (sdk) {
                    is Sdk.Ecj -> {
                        when {
                            lint.isEmpty() -> out.add("-warn:none")
                            lint.containsAll(sdk.supportedWarnings) -> out.add("-warn:all")
                            else -> out.add("-warn:" + lint.intersect(sdk.supportedWarnings).joinToString(","))
                        }
                    }
                    is Sdk.OpenJdk -> {
                        when {
                            lint.isEmpty() -> out.add("-Xlint:none")
                            lint.containsAll(sdk.supportedWarnings) -> out.add("-Xlint:all")
                            else -> out.add("-Xlint:" + lint.intersect(sdk.supportedWarnings).joinToString(","))
                        }
                    }
                    is Sdk.Scala -> {
                        when {
                            lint.isEmpty() -> out.add("-Xlint:")
                            lint.containsAll(sdk.supportedWarnings) -> out.add("-Xlint:_")
                            else -> out.add("-Xlint:" + lint.intersect(sdk.supportedWarnings).joinToString(","))
                        }
                    }
                    else -> {
                        throw AssertionError()
                    }
                }
            }
        }
        return out
    }


    private inline fun releaseChoice(id: String,
                                     param: String,
                                     crossinline range: (Int) -> IntRange): ConfigProperty.RangeChoice =
            object : ConfigProperty.RangeChoice(id, param, default = null) {
                override fun apply(out: MutableList<String>, value: Int?) {
                    if (value != null) {
                        out.add(param)
                        if (value <= 5) {
                            out.add("1.$value")
                        } else {
                            out.add(value.toString())
                        }
                    }
                }

                override fun getRange(sdk: Sdk): IntRange {
                    val release = (sdk as Sdk.Java).release
                    return range(release)
                }
            }

    private val propertyRelease = releaseChoice("release", "--release") { release ->
        when {
            release <= 8 -> throw AssertionError()
            release <= 11 -> 6..release
            else -> 7..release
        }
    }.apply { minJavaVersion = 9 }
    val lombok: ConfigProperty<Boolean> = ConfigProperty.SpecialFlag("lombok", "Lombok", default = true)
    val lint: ConfigProperty<Set<String>?> = object : ConfigProperty.Special<Set<String>?>(
            "lint", default = null,
            serializer = SetSerializer(String.serializer()).nullable) {

        override fun canApplyTo(sdk: Sdk) = sdk is Sdk.HasLint

        override fun validate(sdk: Sdk, value: Set<String>?) {
            require(value == null || Sdks.allSupportedWarnings.containsAll(value))
        }
    }
    private val propertiesJava = listOf<ConfigProperty<*>>(
            propertyRelease,
            releaseChoice("source", "-source") { release ->
                when {
                    release <= 8 -> 3..release
                    release <= 11 -> 6..release
                    else -> 7..release
                }
            }.apply {
                enableDependsOn = ConfigProperty.Interdependency(propertyRelease) { _, it -> it == null }
            },
            releaseChoice("target", "-target") { release ->
                when {
                    release <= 8 -> 1..release
                    release <= 11 -> 6..release
                    else -> 7..release
                }
            }.apply {
                enableDependsOn = ConfigProperty.Interdependency(propertyRelease) { _, it -> it == null }
            },
            ConfigProperty.SpecialFlag("debugLines", "-g:lines", default = true),
            ConfigProperty.SpecialFlag("debugVars", "-g:vars", default = true),
            ConfigProperty.SpecialFlag("debugSource", "-g:source", default = true),
            ConfigProperty.SimpleFlag("verbose", "-verbose"),
            ConfigProperty.SimpleFlag("deprecation", "-deprecation"),
            lombok,
            ConfigProperty.SimpleFlag("reflectionParameters", "-parameters").apply { minJavaVersion = 8 },
            object : ConfigProperty.SpecialFlag("warningsAsErrors", "-Werror") {
                override fun canApplyTo(sdk: Sdk) =
                        super.canApplyTo(sdk) && (sdk !is Sdk.Ecj || sdk.release >= 9)
            },
            lint,
            ConfigProperty.SimpleFlag("preview", "--enable-preview").apply {
                minJavaVersion = 11
            },
            ConfigProperty.SimpleFlag("proceedOnError", "-proceedOnError", default = true).apply {
                requireEcj = true
            },
            ConfigProperty.SimpleFlag("preserveAllLocals", "-preserveAllLocals").apply {
                requireEcj = true
            },
            ConfigProperty.SimpleFlag("genericSignature", "-genericsignature").apply {
                requireEcj = true
            }
    )

    private val v1_0_2 = KotlinVersion(1, 0, 2)
    private val v1_0_3 = KotlinVersion(1, 0, 3)
    private val v1_0_5 = KotlinVersion(1, 0, 5)
    private val v1_1_1 = KotlinVersion(1, 1, 1)
    private val v1_2_30 = KotlinVersion(1, 2, 30)
    private val v1_3_10 = KotlinVersion(1, 3, 10)
    private val v1_3_50 = KotlinVersion(1, 3, 50)

    private val propertyLanguageVersion: ConfigProperty.Choice<String?> = object : ConfigProperty.Choice<String?>(
            "languageVersion",
            "-language-version",
            default = null, serializer = String.serializer().nullable) {
        override fun apply(out: MutableList<String>, value: String?) {
            if (value != null) {
                out.add("-language-version")
                // validated by strict getChoices
                out.add(value)
            }
        }

        override fun getChoices(sdk: Sdk) = mapOf("" to null) +
                languageVersionMap
                        .filter { it.value <= (sdk as Sdk.Kotlin).release }
                        .keys.associateBy { it }
    }
    private val propertiesKotlin = listOf<ConfigProperty<*>>(
            ConfigProperty.SimpleFlag("verbose", "-verbose")
                    .apply { minKotlinVersion = v1_0_2 },
            ConfigProperty.SimpleFlag("noCallAssertions", "-Xno-call-assertions")
                    .apply { minKotlinVersion = v1_0_2 },
            ConfigProperty.SimpleFlag("noParamAssertions", "-Xno-param-assertions")
                    .apply { minKotlinVersion = v1_0_2 },
            ConfigProperty.SimpleFlag("noOptimize", "-Xno-optimize")
                    .apply { minKotlinVersion = v1_0_2 },
            ConfigProperty.SimpleFlag("noInline", "-Xno-inline")
                    .apply { minKotlinVersion = v1_0_2 },
            object : ConfigProperty.Choice<Int?>("jvmTarget", "-jvm-target",
                    default = null, serializer = Int.serializer().nullable) {
                /**
                 * Supported jvm targets. 7 should be excluded
                 */
                /**
                 * Supported jvm targets. 7 should be excluded
                 */
                private fun jvmTargets(sdk: Sdk.Kotlin): IntRange {
                    if (sdk.release >= v1_3_50) return 6..12
                    if (sdk.release >= v1_1_1) return 6..8
                    if (sdk.release >= v1_0_3) return 6..6
                    throw AssertionError()
                }

                override fun apply(out: MutableList<String>, value: Int?) {
                    if (value != null) {
                        out.add("-jvm-target")
                        if (value <= 8) {
                            out.add("1.$value")
                        } else {
                            out.add("$value")
                        }
                    }
                }

                override fun getChoices(sdk: Sdk) =
                        mapOf("" to null) +
                                jvmTargets(sdk as Sdk.Kotlin).filter { it != 7 }.associateBy { "$it" }
            }.apply { minKotlinVersion = v1_0_3 },
            propertyLanguageVersion.apply { minKotlinVersion = v1_0_3 },
            object : ConfigProperty.Choice<String?>("apiVersion", "-api-version", default = null,
                    serializer = String.serializer().nullable) {
                init {
                    choicesDependOn = Interdependency(propertyLanguageVersion) { sdk, langVersion ->
                        mapOf("" to null) + languageVersionMap
                                .filter { it.value <= (sdk as Sdk.Kotlin).release }
                                .filter {
                                    langVersion == null ||
                                            it.value <= languageVersionMap.getValue(langVersion)
                                }
                                .keys.associateBy { it }

                    }
                }

                override fun apply(out: MutableList<String>, value: String?) {
                    if (value != null) {
                        out.add("-api-version")
                        // validated by strict getChoices
                        out.add(value)
                    }
                }

                override fun getChoices(sdk: Sdk) = throw AssertionError()
            }.apply { minKotlinVersion = v1_0_5 },
            ConfigProperty.SimpleFlag("javaParameters", "-java-parameters")
                    .apply { minKotlinVersion = v1_1_1 },
            ConfigProperty.SimpleFlag("coroutines", "-Xcoroutines=enable")
                    .apply { minKotlinVersion = v1_1_1 },
            ConfigProperty.SimpleFlag("warningsAsErrors", "-Werror")
                    .apply { minKotlinVersion = v1_2_30 },
            object : ConfigProperty.Choice<Boolean?>("normalizeConstructorCalls", "-Xnormalize-constructor-calls",
                    default = null,
                    serializer = Boolean.serializer().nullable) {
                override fun apply(out: MutableList<String>, value: Boolean?) {
                    if (value != null) {
                        out.add("-Xnormalize-constructor-calls=${if (value) "enable" else "disable"}")
                    }
                }

                override fun getChoices(sdk: Sdk) = mapOf("" to null, "enable" to true, "disable" to false)
            }.apply { minKotlinVersion = v1_2_30 },
            ConfigProperty.SimpleFlag("noExceptionOnExplicitEqualsForBoxedNull",
                    "-Xno-exception-on-explicit-equals-for-boxed-null")
                    .apply { minKotlinVersion = v1_2_30 },
            ConfigProperty.SimpleFlag("noReceiverAssertions", "-Xno-receiver-assertions")
                    .apply { minKotlinVersion = v1_2_30 },
            ConfigProperty.SimpleFlag("effectSystem", "-Xeffect-system")
                    .apply { minKotlinVersion = v1_2_30 },
            object : ConfigProperty.Choice<AssertionMode?>("assertions",
                    "-Xassertions",
                    default = null,
                    serializer = AssertionMode.serializer().nullable) {
                private val choices = mapOf(
                        AssertionMode.ALWAYS_ENABLE to "always-enable",
                        AssertionMode.ALWAYS_DISABLE to "always-disable",
                        AssertionMode.JVM to "jvm",
                        AssertionMode.LEGACY to "legacy"
                )

                override fun apply(out: MutableList<String>, value: AssertionMode?) {
                    if (value != null) {
                        out.add("-Xassertions=" + choices.getValue(value))
                    }
                }

                override fun getChoices(sdk: Sdk) = mapOf("" to null) +
                        choices.entries.associate { it.value to it.key }
            }.apply { minKotlinVersion = v1_3_10 },
            object : ConfigProperty.Choice<JvmDefaultMode?>("jvmDefault", "-Xjvm-default", default = null,
                    serializer = JvmDefaultMode.serializer().nullable) {
                private val choices = mapOf(
                        JvmDefaultMode.ENABLE to "enable",
                        JvmDefaultMode.DISABLE to "disable",
                        JvmDefaultMode.COMPATIBILITY to "compatibility"
                )

                override fun apply(out: MutableList<String>, value: JvmDefaultMode?) {
                    if (value != null) {
                        out.add("-Xjvm-default=" + choices.getValue(value))
                    }
                }

                override fun getChoices(sdk: Sdk) = mapOf("" to null) +
                        choices.entries.associate { it.value to it.key }
            }.apply { minKotlinVersion = v1_3_10 },
            ConfigProperty.SimpleFlag("allowResultReturnType", "-Xallow-result-return-type")
                    .apply { minKotlinVersion = v1_2_30 },
            ConfigProperty.SimpleFlag("properIeee754Comparisons", "-Xproper-ieee754-comparisons")
                    .apply { minKotlinVersion = v1_2_30 },
            ConfigProperty.SimpleFlag("sanitizeParentheses", "-Xsanitize-parentheses")
                    .apply { minKotlinVersion = v1_3_50 },
            ConfigProperty.SimpleFlag("listPhases", "-Xlist-phases")
                    .apply { minKotlinVersion = v1_3_50 },
            ConfigProperty.SimpleFlag("polymorphicSignature", "-Xpolymorphic-signature")
                    .apply { minKotlinVersion = v1_3_50 }
    )

    @Serializable
    private enum class AssertionMode {
        ALWAYS_ENABLE,
        ALWAYS_DISABLE,
        JVM,
        LEGACY,
    }

    @Serializable
    private enum class JvmDefaultMode {
        ENABLE,
        DISABLE,
        COMPATIBILITY
    }

    private val languageVersionMap = listOf(
            KotlinVersion(1, 0),
            KotlinVersion(1, 1),
            KotlinVersion(1, 2),
            KotlinVersion(1, 3)
    ).associateBy { "${it.major}.${it.minor}" }

    private val v2_11_8 = KotlinVersion(2, 11, 8)
    private val v2_12_5 = KotlinVersion(2, 12, 5)

    private val scala = listOf(
            ConfigProperty.SimpleFlag("deprecation", "-deprecation"),
            ConfigProperty.SimpleFlag("explainTypes", "-explaintypes"),
            object : ConfigProperty.Choice<String>("debug", "-g", serializer = String.serializer(), default = "vars") {
                override fun apply(out: MutableList<String>, value: String) {
                    if (value != default) {
                        out.add("-g:$value")
                    }
                }

                override fun getChoices(sdk: Sdk) =
                        listOf("none", "source", "line", "vars", "notailcalls").associateBy { it }
            },
            ConfigProperty.SpecialFlag("debugSource", "-g:source"),
            ConfigProperty.SpecialFlag("debugLine", "-g:line"),
            ConfigProperty.SpecialFlag("debugVars", "-g:vars"),
            ConfigProperty.SpecialFlag("debugNoTailCalls", "-g:notailcalls"),
            ConfigProperty.SimpleFlag("debugNoSpecialization", "-no-specialization"),
            ConfigProperty.SimpleFlag("optimise", "-optimise").apply { maxScalaVersion = v2_11_8 },
            object : ConfigProperty.RangeChoice("target", "-target", default = null) {
                override fun apply(out: MutableList<String>, value: Int?) {
                    if (value != null) {
                        out.add("-target")
                        out.add("jvm-1.$value")
                    }
                }

                override fun getRange(sdk: Sdk): IntRange {
                    return 5..8
                }
            },
            object : ConfigProperty.RangeChoice("release", "-release", default = null) {
                override fun apply(out: MutableList<String>, value: Int?) {
                    if (value != null) {
                        out.add("-release")
                        out.add(value.toString())
                    }
                }

                override fun getRange(sdk: Sdk): IntRange {
                    return 6..9
                }
            },
            ConfigProperty.SimpleFlag("unchecked", "-unchecked"),
            ConfigProperty.SimpleFlag("uniqid", "-uniqid"),
            ConfigProperty.SimpleFlag("verbose", "-verbose"),
            ConfigProperty.SimpleFlag("checkInit", "-Xcheckinit"),
            ConfigProperty.SimpleFlag("dev", "-Xdev"),
            ConfigProperty.SimpleFlag("disableAssertions", "-Xdisable-assertions"),
            ConfigProperty.SimpleFlag("experimental", "-Xexperimental"),
            ConfigProperty.SimpleFlag("warningsAsErrors", "-Xfatal-warnings"),
            ConfigProperty.SimpleFlag("fullLubs", "-Xfull-lubs"),
            ConfigProperty.SimpleFlag("future", "-Xfuture"),
            ConfigProperty.SimpleFlag("noForwarders", "-Xno-forwarders"),
            ConfigProperty.SimpleFlag("noPatmatAnalysis", "-Xno-patmat-analysis"),
            ConfigProperty.SimpleFlag("noUescape", "-Xno-uescape"),
            ConfigProperty.SpecialFlag("dynamics", "-language:dynamics"),
            ConfigProperty.SpecialFlag("postfixOps", "-language:postfixOps"),
            ConfigProperty.SpecialFlag("reflectiveCalls", "-language:reflectiveCalls"),
            ConfigProperty.SpecialFlag("implicitConversions", "-language:implicitConversions"),
            ConfigProperty.SpecialFlag("higherKinds", "-language:higherKinds"),
            ConfigProperty.SpecialFlag("existentials", "-language:existentials"),
            ConfigProperty.SimpleFlag("virtPatMat", "-Yvirtpatmat").apply {
                minScalaVersion = v2_12_5
                maxScalaVersion = v2_12_5
            },
            lint
    )

    val properties = mapOf(
            SdkLanguage.JAVA to propertiesJava,
            SdkLanguage.KOTLIN to propertiesKotlin,
            SdkLanguage.SCALA to scala
    )

    init {
        for ((language, list) in properties) {
            for (property in list) {
                property.init(language)
            }
        }
    }

    val serializers: Map<SdkLanguage, KSerializer<CompilerConfiguration>> =
            properties.mapValues { (language, properties) ->
                object : KSerializer<CompilerConfiguration> {
                    override val descriptor = buildClassSerialDescriptor("CompilerConfiguration.$language") {
                        for (property in properties) {
                            element(property.id, property.serializer.descriptor)
                        }
                    }

                    private fun <T> serializeEntry(
                        structure: CompositeEncoder,
                        config: CompilerConfiguration,
                        index: Int,
                        property: ConfigProperty<T>
                    ) {
                        if (config.containsKey(property.id)) {
                            val value = config[property.id]
                            if (value != property.default) {
                                @Suppress("UNCHECKED_CAST")
                                structure.encodeSerializableElement(
                                        descriptor,
                                        index,
                                        property.serializer,
                                        value as T
                                )
                            }
                        }
                    }

                    override fun serialize(encoder: Encoder, value: CompilerConfiguration) {
                        val structure = encoder.beginStructure(descriptor)
                        for ((index, property) in properties.withIndex()) {
                            serializeEntry(structure, value, index, property)
                        }
                        structure.endStructure(descriptor)
                    }

                    override fun deserialize(decoder: Decoder): CompilerConfiguration {
                        val result = mutableMapOf<String, Any?>()
                        val structure = decoder.beginStructure(descriptor)
                        while (true) {
                            val i = structure.decodeElementIndex(descriptor)
                            if (i == CompositeDecoder.DECODE_DONE) break
                            val property = properties.getOrNull(i) ?: throw SerializationException("unknown index $i")
                            result[property.id] = structure.decodeSerializableElement(
                                    property.serializer.descriptor, i, property.serializer)
                        }
                        structure.endStructure(descriptor)
                        return result
                    }

                }
            }
}

sealed class ConfigProperty<T>(
        val id: String,
        val default: T,
        internal val serializer: KSerializer<T>
) {
    private companion object {
        private val ktv1 = KotlinVersion(1, 0)
        private val ktvInf = KotlinVersion(255, 0)
    }

    private lateinit var language: SdkLanguage

    internal var minJavaVersion = 0
    internal var requireEcj = false
    internal var minKotlinVersion: KotlinVersion = ktv1
    internal var minScalaVersion: KotlinVersion = ktv1
    internal var maxScalaVersion: KotlinVersion = ktvInf

    /**
     * Only allow setting this property if a given interdependency is met
     */
    var enableDependsOn: Interdependency<*, Boolean>? = null
        internal set

    internal fun init(language: SdkLanguage) {
        this.language = language
    }

    fun get(config: CompilerConfiguration): T {
        if (config.containsKey(id)) {
            @Suppress("UNCHECKED_CAST")
            return config[id] as T
        } else {
            return default
        }
    }

    open fun canApplyTo(sdk: Sdk) = when (sdk) {
        is Sdk.OpenJdk, is Sdk.Ecj ->
            language == SdkLanguage.JAVA && minJavaVersion <= (sdk as Sdk.Java).release &&
                    (!requireEcj || sdk is Sdk.Ecj)
        is Sdk.KotlinJar, is Sdk.KotlinDistribution ->
            language == SdkLanguage.KOTLIN && minKotlinVersion <= (sdk as Sdk.Kotlin).release
        is Sdk.Scala ->
            language == SdkLanguage.SCALA &&
                    minScalaVersion <= sdk.release &&
                    maxScalaVersion >= sdk.release
    }

    internal fun applyFrom(out: MutableList<String>, config: CompilerConfiguration) =
            apply(out, get(config))

    protected abstract fun apply(out: MutableList<String>, value: T)

    internal open fun validateFrom(sdk: Sdk, config: CompilerConfiguration) {
        val value = get(config)
        if (value != default) {
            val enableDependsOn = enableDependsOn
            if (enableDependsOn != null) {
                require(enableDependsOn(sdk, config))
            }
        }
        validate(sdk, value)
    }

    protected abstract fun validate(sdk: Sdk, value: T)

    open class Special<T>(id: String, default: T, serializer: KSerializer<T>) : ConfigProperty<T>(id,
            default,
            serializer) {
        override fun apply(out: MutableList<String>, value: T) {
            // special handling
        }

        override fun validate(sdk: Sdk, value: T) {
            // special handling
        }
    }

    abstract class Choice<T>(id: String,
                             val name: String, default: T, serializer: KSerializer<T>) :
            ConfigProperty<T>(id, default, serializer) {
        /**
         * Change the choices based on an interdependency
         */
        var choicesDependOn: Interdependency<*, Map<String, T>>? = null
            internal set

        abstract fun getChoices(sdk: Sdk): Map<String, T>

        override fun validateFrom(sdk: Sdk, config: CompilerConfiguration) {
            super.validateFrom(sdk, config)

            val choicesDependOn = choicesDependOn
            if (choicesDependOn != null) {
                require(get(config) in choicesDependOn(sdk, config).values)
            }
        }

        override fun validate(sdk: Sdk, value: T) {
            if (choicesDependOn == null) {
                require(value in getChoices(sdk).values)
            }
            // else validated above
        }
    }

    abstract class RangeChoice(id: String, name: String, default: Int? = null) :
            Choice<Int?>(id, name, default, Int.serializer().nullable) {
        override fun getChoices(sdk: Sdk) =
                mapOf("" to null) + getRange(sdk).associateBy { "$it" }

        abstract fun getRange(sdk: Sdk): IntRange

        override fun validate(sdk: Sdk, value: Int?) {
            require(value == null || value in getRange(sdk))
        }
    }

    /**
     * A boolean flag
     */
    abstract class Flag(
            id: String,
            val displayName: String,
            default: Boolean
    ) : ConfigProperty<Boolean>(id, default = default, serializer = Boolean.serializer()) {
        override fun validate(sdk: Sdk, value: Boolean) {
        }
    }

    internal open class SpecialFlag(
            id: String,
            displayName: String,
            default: Boolean = false
    ) : Flag(id, displayName, default) {
        override fun apply(out: MutableList<String>, value: Boolean) {
        }
    }

    /**
     * A flag that leads to a simple added command line param
     */
    internal class SimpleFlag(
            id: String,
            private val value: String,
            default: Boolean = false
    ) : Flag(id, value, default = default) {
        override fun apply(out: MutableList<String>, value: Boolean) {
            if (value) {
                out.add(this.value)
            }
        }
    }

    data class Interdependency<T, R>(
            val dependsOn: ConfigProperty<T>,
            val function: (Sdk, T) -> R
    ) {
        operator fun invoke(sdk: Sdk, config: CompilerConfiguration) = function(sdk, dependsOn.get(config))
    }
}