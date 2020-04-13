/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

import at.yawk.javap.model.PasteDao
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.google.inject.Guice
import com.google.inject.Module
import io.dropwizard.Application
import io.dropwizard.assets.AssetsBundle
import io.dropwizard.jdbi3.JdbiFactory
import io.dropwizard.servlets.assets.AssetServlet
import io.dropwizard.setup.Bootstrap
import io.dropwizard.setup.Environment
import org.flywaydb.core.Flyway
import org.jdbi.v3.core.Jdbi
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * @author yawkat
 */
fun main(args: Array<String>) {
    JavapApplication().run(*args)
}

class JavapApplication : Application<JavapConfiguration>() {
    override fun getName(): String {
        return "javap"
    }

    override fun initialize(bootstrap: Bootstrap<JavapConfiguration>) {
        bootstrap.objectMapper.registerModule(KotlinModule())
        bootstrap.addBundle(AssetsBundle("/META-INF/resources/webjars", "/webjars", "", "webjars"))
        bootstrap.addBundle(AssetsBundle("/static", "/", "index.html", "index"))
        bootstrap.addBundle(object : AssetsBundle("/static", "/static", "", "js") {
            override fun createServlet(): AssetServlet {
                return object : AssetServlet(resourcePath, uriPath, indexFile, StandardCharsets.UTF_8) {
                    override fun getResourceUrl(absoluteRequestedResourcePath: String): URL {
                        if (absoluteRequestedResourcePath == "static/kotlin.js") {
                            return super.getResourceUrl("kotlin.js")
                        }
                        return super.getResourceUrl(absoluteRequestedResourcePath)
                    }
                }
            }
        })
    }

    override fun run(configuration: JavapConfiguration, environment: Environment) {
        val dataSource = configuration.database.build(environment.metrics(), "postgresql")
        val flyway = Flyway()
        flyway.dataSource = dataSource
        flyway.migrate()

        val dbi = JdbiFactory().build(environment, configuration.database, dataSource, "postgresql")
        val injector = Guice.createInjector(Module {
            it.bind(Jdbi::class.java).toInstance(dbi)
            it.bind(PasteDao::class.java).toInstance(dbi.onDemand(PasteDao::class.java))
            it.bind(SdkProvider::class.java).to(SdkProviderImpl::class.java)
        })
        injector.getInstance(SdkProviderImpl::class.java).start()

        environment.jersey().register(injector.getInstance(PasteResource::class.java))
    }
}