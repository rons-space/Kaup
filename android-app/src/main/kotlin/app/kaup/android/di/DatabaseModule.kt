package app.kaup.android.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import app.kaup.core.data.KaupDatabase
import app.kaup.core.data.dao.ItemDao
import app.kaup.core.data.dao.LocationDao
import app.kaup.core.data.dao.OverrideLogDao
import app.kaup.core.data.dao.StockMovementDao
import app.kaup.core.data.dao.UserDao
import app.kaup.core.data.entities.LocationEntity
import app.kaup.shared.models.SyncStatus
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): KaupDatabase {
        val builder = Room.databaseBuilder(
            context,
            KaupDatabase::class.java,
            "kaup_database"
        ).addCallback(SeedDefaultLocation)

        if (KaupDatabase.ALPHA_DESTRUCTIVE_MIGRATION) {
            @Suppress("DEPRECATION")
            builder.fallbackToDestructiveMigration()
        }

        return builder.build()
    }

    /**
     * Seeds the single default location ADR-016 requires.
     *
     * This has to be a database callback rather than a step in onboarding.
     * Every location-aware table carries a non-null `locationId` foreign key,
     * so the row must exist before anything else can be written, and during the
     * alpha phase the database is recreated on every schema change: seeding
     * from onboarding would leave a wiped database with no location and no way
     * to insert one, because onboarding only runs once.
     */
    private object SeedDefaultLocation : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "INSERT INTO locations (id, name, address, isDefault, syncStatus) " +
                    "VALUES (?, ?, NULL, 1, ?)",
                arrayOf(
                    LocationEntity.DEFAULT_ID,
                    LocationEntity.DEFAULT_NAME,
                    SyncStatus.PENDING.name
                )
            )
        }
    }

    @Provides
    fun provideLocationDao(database: KaupDatabase): LocationDao = database.locationDao()

    @Provides
    fun provideItemDao(database: KaupDatabase): ItemDao = database.itemDao()

    @Provides
    fun provideStockMovementDao(database: KaupDatabase): StockMovementDao = database.stockMovementDao()

    @Provides
    fun provideUserDao(database: KaupDatabase): UserDao = database.userDao()

    @Provides
    fun provideOverrideLogDao(database: KaupDatabase): OverrideLogDao = database.overrideLogDao()
}
