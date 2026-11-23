// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT

// FILE: http.kt
package http

open class HttpClient {
    open fun request(url: String): String = url
}

fun HttpClient.get(url: String, block: () -> Unit = {}): String = request(url)

suspend fun HttpClient.post(url: String): String = request(url)

// FILE: star.kt
package star

import http.*
import io.lokki.every
import io.lokki.mock

fun test() {
    val client = mock<HttpClient>()
    every { client.<!LOKKI_NOT_INTERCEPTABLE!>get<!>("url") } returns "a"
    every { <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>client.get("url") { }<!> } returns "a"
}

// FILE: explicit.kt
package explicit

import http.HttpClient
import http.get
import io.lokki.every
import io.lokki.mock

fun test() {
    val client = mock<HttpClient>()
    every { client.<!LOKKI_NOT_INTERCEPTABLE!>get<!>("url") } returns "a"
}

// FILE: samePackage.kt
package http

import io.lokki.every
import io.lokki.mock

fun test() {
    val client = mock<HttpClient>()
    every { client.<!LOKKI_NOT_INTERCEPTABLE!>get<!>("url") } returns "a"
}
