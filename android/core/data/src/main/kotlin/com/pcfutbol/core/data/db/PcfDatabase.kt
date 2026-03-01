package com.pcfutbol.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        TeamEntity::class,
        PlayerEntity::class,
        CompetitionEntity::class,
        FixtureEntity::class,
        StandingEntity::class,
        SeasonStateEntity::class,
        ManagerProfileEntity::class,
        TacticPresetEntity::class,
        NewsEntity::class,
        NationalSquadEntity::class,
    ],
    version = 10,
    exportSchema = true,
)
abstract class PcfDatabase : RoomDatabase() {
    abstract fun teamDao(): TeamDao
    abstract fun playerDao(): PlayerDao
    abstract fun competitionDao(): CompetitionDao
    abstract fun fixtureDao(): FixtureDao
    abstract fun standingDao(): StandingDao
    abstract fun seasonStateDao(): SeasonStateDao
    abstract fun managerProfileDao(): ManagerProfileDao
    abstract fun tacticPresetDao(): TacticPresetDao
    abstract fun newsDao(): NewsDao
    abstract fun nationalSquadDao(): NationalSquadDao
}
