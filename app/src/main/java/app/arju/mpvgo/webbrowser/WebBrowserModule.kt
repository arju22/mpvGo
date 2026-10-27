package app.arju.mpvgo.webbrowser

import app.arju.mpvgo.webbrowser.adblock.AdBlockPreferences
import app.arju.mpvgo.webbrowser.adblock.AdBlocker
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val WebBrowserModule =
  module {
    singleOf(::AdBlockPreferences)
    singleOf(::AdBlocker)
  }
