package com.neojou.montecarlopi

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.browser.window

/**
 * Browser / Wasm entry — mounts shared [App] into the page body.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    document.title = AppVersion.APP_NAME
    ComposeViewport(document.body!!) {
        App(demo = window.location.search.contains("demo=1"))
    }
}
