package app.arju.mpvgo.core.di

import app.arju.mpvgo.core.database.MpvGoDatabase
import app.arju.mpvgo.core.preferences.AdvancedPreferences
import app.arju.mpvgo.core.preferences.AppearancePreferences
import app.arju.mpvgo.core.preferences.AudioPreferences
import app.arju.mpvgo.core.preferences.BrowserPreferences
import app.arju.mpvgo.core.preferences.DecoderPreferences
import app.arju.mpvgo.core.preferences.FoldersPreferences
import app.arju.mpvgo.core.preferences.GesturePreferences
import app.arju.mpvgo.core.preferences.PlayerPreferences
import app.arju.mpvgo.core.preferences.SettingsManager
import app.arju.mpvgo.core.preferences.SubtitlesPreferences
import app.arju.mpvgo.core.preferences.TapZonePreferences
import app.arju.mpvgo.core.preferences.preference.AndroidPreferenceStore
import app.arju.mpvgo.core.preferences.preference.PreferenceStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val PreferencesModule =
  module {
    single { AndroidPreferenceStore(androidContext()) }.bind(PreferenceStore::class)

    single { AppearancePreferences(get()) }
    singleOf(::PlayerPreferences)
    singleOf(::GesturePreferences)
    singleOf(::TapZonePreferences)
    singleOf(::DecoderPreferences)
    singleOf(::SubtitlesPreferences)
    singleOf(::AudioPreferences)
    singleOf(::AdvancedPreferences)
    single { BrowserPreferences(get(), androidContext()) }
    singleOf(::FoldersPreferences)
    singleOf(::SettingsManager)
  }
