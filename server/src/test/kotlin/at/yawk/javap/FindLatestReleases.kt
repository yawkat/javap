/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

private const val API = "https://api.adoptium.net/v3"

private fun HttpClient.getJson(path: String): JsonElement {
    val response = send(HttpRequest.newBuilder(URI.create(API + path)).build(), HttpResponse.BodyHandlers.ofString())
    check(response.statusCode() == 200) { "$path: HTTP ${response.statusCode()}" }
    return Json.parseToJsonElement(response.body())
}

private val JsonElement.string get() = jsonPrimitive.content

fun main() {
    HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build().use { http ->
        val releases = http.getJson("/info/available_releases").jsonObject["available_releases"]!!.jsonArray
        for (release in releases.map { it.jsonPrimitive.int }) {
            val assets = http.getJson("/assets/latest/$release/hotspot?architecture=x64&image_type=jdk&os=linux")
            val pkg = assets.jsonArray
                .map { it.jsonObject["binary"]!!.jsonObject }
                .single { it["heap_size"]!!.string == "normal" }["package"]!!.jsonObject
            // print as nix/sdks.nix openjdk arguments
            val hash = java.util.Base64.getEncoder()
                .encodeToString(java.util.HexFormat.of().parseHex(pkg["checksum"]!!.string))
            println("$release: url = \"${pkg["link"]!!.string}\"; hash = \"sha256-$hash\";")
        }
    }
}
