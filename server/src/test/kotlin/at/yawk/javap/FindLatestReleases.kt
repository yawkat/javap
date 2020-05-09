/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

import net.adoptopenjdk.v3.api.AOV3Architecture
import net.adoptopenjdk.v3.api.AOV3HeapSize
import net.adoptopenjdk.v3.api.AOV3ImageKind
import net.adoptopenjdk.v3.api.AOV3JVMImplementation
import net.adoptopenjdk.v3.api.AOV3OperatingSystem
import net.adoptopenjdk.v3.vanilla.AOV3Clients

fun main() {
    AOV3Clients().createClient().use { adopt ->
        val releases = adopt.availableReleases { }.execute().availableReleases()
        for (i in releases) {
            val assets = adopt.assetsForLatest({}, i, AOV3JVMImplementation.HOTSPOT).execute()
            val asset = assets.single {
                it.binary().architecture() == AOV3Architecture.X64 &&
                        it.binary().heapSize() == AOV3HeapSize.NORMAL &&
                        it.binary().imageType() == AOV3ImageKind.JDK &&
                        it.binary().operatingSystem() == AOV3OperatingSystem.LINUX
            }
            val pkg = asset.binary().package_()
            println("$i: ${pkg.checksum().get()} ${pkg.link()}")
        }
    }
}