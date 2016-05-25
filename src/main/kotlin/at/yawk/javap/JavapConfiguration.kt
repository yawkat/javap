package at.yawk.javap

import io.dropwizard.Configuration
import io.dropwizard.db.DataSourceFactory

/**
 * @author yawkat
 */
class JavapConfiguration(val database: DataSourceFactory) : Configuration()