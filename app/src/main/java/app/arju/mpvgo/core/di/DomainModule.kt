package app.arju.mpvgo.core.di

import app.arju.mpvgo.core.domain.anime4k.Anime4KManager
import app.arju.mpvgo.subtitles.SubtitleRepository
import org.koin.dsl.module
import org.koin.android.ext.koin.androidContext

val domainModule = module {
    single { Anime4KManager(androidContext()) }
    single { SubtitleRepository(androidContext(), get()) }
}
