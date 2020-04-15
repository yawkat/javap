/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

import at.yawk.javap.model.HttpException
import at.yawk.javap.model.PasteDao
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.undertow.Undertow
import io.undertow.server.HttpHandler
import io.undertow.server.handlers.PathHandler
import io.undertow.server.handlers.resource.ClassPathResourceManager
import io.undertow.server.handlers.resource.Resource
import io.undertow.server.handlers.resource.ResourceChangeListener
import io.undertow.server.handlers.resource.ResourceHandler
import io.undertow.server.handlers.resource.ResourceManager
import io.undertow.util.StatusCodes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import org.flywaydb.core.Flyway
import org.jdbi.v3.core.Jdbi
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

/**
 * @author yawkat
 */
val jsonConfiguration = JsonConfiguration.Stable.copy(encodeDefaults = false)

fun main(args: Array<String>) {
    val json = Json(jsonConfiguration)

    val config = json.parse(
            JavapConfiguration.serializer(),
            Files.readAllBytes(Paths.get(args[0])).toString(Charsets.UTF_8))

    val dataSource = HikariDataSource(HikariConfig().apply {
        username = config.database.user
        password = config.database.password
        jdbcUrl = config.database.url
    })

    val flyway = Flyway()
    flyway.dataSource = dataSource
    flyway.migrate()

    val jdbi = Jdbi.create(dataSource).installPlugins()
    val sdkProvider = SdkProviderImpl()
    sdkProvider.start()
    val processor = LocalProcessor(sdkProvider, Bubblewrap())
    val pasteResource = PasteResource(
            json,
            jdbi.onDemand(PasteDao::class.java),
            processor,
            DefaultPaste(processor)
    )

    var handler = handleExceptions(HttpHandler { throw HttpException(StatusCodes.NOT_FOUND, "Not found") })
    handler = PathHandler(handler).apply {
        val cl = object {}.javaClass.classLoader

        val kotlinFiles = setOf(
                "kotlin.js",
                "kotlin.meta.js",
                "kotlin.js.map",
                "kotlinx-serialization-kotlinx-serialization-runtime.js",
                "kotlinx-serialization-kotlinx-serialization-runtime.meta.js",
                "kotlinx-serialization-kotlinx-serialization-runtime.js.map"
        )
        val rootClasspath = ClassPathResourceManager(cl)
        // kotlin files are at classpath root
        for (kotlinFile in kotlinFiles) {
            addExactPath("/static/$kotlinFile",
                    ResourceHandler(ExactResourceManager(kotlinFile, rootClasspath), handler))
        }
        addPrefixPath("/webjars", ResourceHandler(ClassPathResourceManager(cl, "META-INF/resources/webjars"), handler)
                .apply {
                    cacheTime = TimeUnit.DAYS.toSeconds(1).toInt()
                })
        addPrefixPath("/static", ResourceHandler(ClassPathResourceManager(cl, "static"), handler))
        // serve index.html
        addExactPath("/", ResourceHandler(ExactResourceManager("/static/index.html", rootClasspath), handler))
    }
    handler = handleExceptions(pasteResource.buildHandler(handler))

    val undertow = Undertow.builder()
            .addHttpListener(config.bindPort, config.bindAddress)
            .setHandler(handler)
            .build()
    undertow.start()
}