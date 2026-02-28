package com.pcfutbol.core.data.di

import android.content.Context
import androidx.room.Room
import com.pcfutbol.core.data.db.PcfDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): PcfDatabase =
        Room.databaseBuilder(ctx, PcfDatabase::class.java, "pcfutbol.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideTeamDao(db: PcfDatabase) = db.teamDao()
    @Provides fun providePlayerDao(db: PcfDatabase) = db.playerDao()
    @Provides fun provideCompetitionDao(db: PcfDatabase) = db.competitionDao()
    @Provides fun provideFixtureDao(db: PcfDatabase) = db.fixtureDao()
    @Provides fun provideStandingDao(db: PcfDatabase) = db.standingDao()
    @Provides fun provideSeasonStateDao(db: PcfDatabase) = db.seasonStateDao()
    @Provides fun provideManagerProfileDao(db: PcfDatabase) = db.managerProfileDao()
    @Provides fun provideTacticPresetDao(db: PcfDatabase) = db.tacticPresetDao()
    @Provides fun provideNewsDao(db: PcfDatabase) = db.newsDao()
    @Provides fun provideNationalSquadDao(db: PcfDatabase) = db.nationalSquadDao()
}
