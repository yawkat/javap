/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

import io.dropwizard.Configuration
import io.dropwizard.db.DataSourceFactory

/**
 * @author yawkat
 */
class JavapConfiguration(val database: DataSourceFactory) : Configuration()