// RUN_PIPELINE_TILL: FRONTEND
// RENDER_DIAGNOSTICS_FULL_TEXT
// WITH_KOTLINX_COROUTINES

package sample

import io.lokki.coEvery
import io.lokki.coVerify
import io.lokki.every
import io.lokki.verify
import kotlinx.coroutines.runBlocking

interface Api {
    suspend fun load(): String
}

fun test(api: Api) {
    every { <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>runBlocking { api.load() }<!> } returns "a"
    verify { <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>runBlocking { api.load() }<!> }
    coEvery { <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>runBlocking { api.load() }<!> } returns "a"
    coVerify { <!LOKKI_STUB_BLOCK_NOT_SINGLE_CALL!>runBlocking { api.load() }<!> }
}
