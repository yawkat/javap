/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap.model

import at.yawk.javap.SdkLanguage
import at.yawk.javap.jsonConfiguration
import kotlinx.serialization.json.Json
import org.testng.Assert
import org.testng.annotations.Test
import java.util.concurrent.ThreadLocalRandom

class CompilerConfigurationSerializerTest {
    private val properties = ConfigProperties.properties.getValue(SdkLanguage.JAVA)
    private fun withoutDefaults(configuration: CompilerConfiguration): Map<String, Any?> {
        return configuration.filter { (k, v) ->
            properties.single { it.id == k }.default != v
        }
    }

    @Test
    fun test() {
        val config = mutableMapOf<String, Any?>()
        for (property in properties) {
            if (property is ConfigProperty.Flag) {
                config[property.id] = ThreadLocalRandom.current().nextBoolean()
            }
        }
        config[ConfigProperties.lint.id] = setOf("a", "b")

        val serializer = ConfigProperties.serializers.getValue(SdkLanguage.JAVA)
        val json = Json(jsonConfiguration)
        Assert.assertEquals(
                withoutDefaults(json.parse(serializer, json.stringify(serializer, config))),
                withoutDefaults(config)
        )
    }
}