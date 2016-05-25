package at.yawk.javap

import at.yawk.javap.model.PasteDao
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.google.inject.Guice
import com.google.inject.Module
import io.dropwizard.Application
import io.dropwizard.assets.AssetsBundle
import io.dropwizard.jdbi.DBIFactory
import io.dropwizard.setup.Bootstrap
import io.dropwizard.setup.Environment
import org.flywaydb.core.Flyway
import org.skife.jdbi.v2.DBI

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
        bootstrap.addBundle(AssetsBundle("/static"))
    }

    override fun run(configuration: JavapConfiguration, environment: Environment) {
        val dataSource = configuration.database.build(environment.metrics(), "postgresql")
        val flyway = Flyway()
        flyway.dataSource = dataSource
        flyway.migrate()

        val dbi = DBIFactory().build(environment, configuration.database, dataSource, "postgresql")
        val injector = Guice.createInjector(Module {
            it.bind(DBI::class.java).toInstance(dbi)
            it.bind(PasteDao::class.java).toInstance(dbi.onDemand(PasteDao::class.java))
        })
        environment.jersey().register(injector.getInstance(PasteResource::class.java))
    }
}