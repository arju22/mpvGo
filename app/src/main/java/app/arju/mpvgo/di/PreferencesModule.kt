package app.arju.mpvgo.di

import app.arju.mpvgo.database.MpvGoDatabase
import app.arju.mpvgo.preferences.AdvancedPreferences
import app.arju.mpvgo.preferences.AppearancePreferences
import app.arju.mpvgo.preferences.AudioPreferences
import app.arju.mpvgo.preferences.BrowserPreferences
import app.arju.mpvgo.preferences.DecoderPreferences
import app.arju.mpvgo.preferences.FoldersPreferences
import app.arju.mpvgo.preferences.GesturePreferences
import app.arju.mpvgo.preferences.PlayerPreferences
import app.arju.mpvgo.preferences.SettingsManager
import app.arju.mpvgo.preferences.SubtitlesPreferences
import app.arju.mpvgo.preferences.TapZonePreferences
import app.arju.mpvgo.preferences.preference.AndroidPreferenceStore
import app.arju.mpvgo.preferences.preference.PreferenceStore
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
