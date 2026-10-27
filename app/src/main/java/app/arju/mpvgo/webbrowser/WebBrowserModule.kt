package app.arju.mpvgo.webbrowser

import app.arju.mpvgo.webbrowser.adblock.AdBlockPreferences
import app.arju.mpvgo.webbrowser.adblock.AdBlocker
import app.arju.mpvgo.webbrowser.core.BrowserPreferences
import app.arju.mpvgo.webbrowser.core.TabManager
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val WebBrowserModule =
  module {
    singleOf(::AdBlockPreferences)
    singleOf(::AdBlocker)
    singleOf(::BrowserPreferences)
    singleOf(::TabManager)
  }
