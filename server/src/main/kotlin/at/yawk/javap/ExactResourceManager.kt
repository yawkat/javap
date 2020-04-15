/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

import io.undertow.server.handlers.resource.Resource
import io.undertow.server.handlers.resource.ResourceChangeEvent
import io.undertow.server.handlers.resource.ResourceChangeListener
import io.undertow.server.handlers.resource.ResourceManager

class ExactResourceManager(path: String,
                           private val next: ResourceManager) : ResourceManager {
    private val path = if (path.startsWith('/')) path.substring(1) else path
    private val listeners = mutableMapOf<ResourceChangeListener, ResourceChangeListener>()

    override fun isResourceChangeListenerSupported() = next.isResourceChangeListenerSupported

    override fun getResource(path: String): Resource? =
            if (path == "" || path == "/") {
                next.getResource(this.path)
            } else {
                null
            }

    override fun registerResourceChangeListener(listener: ResourceChangeListener) {
        val wrapped = ResourceChangeListener { evts ->
            for (evt in evts) {
                if (evt.resource == path || evt.resource == "/$path") {
                    listener.handleChanges(listOf(ResourceChangeEvent("", evt.type)))
                }
            }
        }
        listeners[listener] = wrapped
        next.registerResourceChangeListener(wrapped)
    }

    override fun removeResourceChangeListener(listener: ResourceChangeListener) {
        next.removeResourceChangeListener(listeners.remove(listener)!!)
    }

    override fun close() {
        next.close()
    }
}