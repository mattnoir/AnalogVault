package com.analogvault

import android.content.Context
import androidx.room.Room
import com.analogvault.data.db.*
import com.analogvault.data.network.WeatherApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "analog_vault.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
            // Only fall back destructively on downgrade, never on upgrade
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Provides fun provideFilmDao(db: AppDatabase) = db.filmDao()
    @Provides fun provideCameraDao(db: AppDatabase) = db.cameraDao()
    @Provides fun provideLensDao(db: AppDatabase) = db.lensDao()
    @Provides fun provideAccessoryDao(db: AppDatabase) = db.accessoryDao()
    @Provides fun provideRollDao(db: AppDatabase) = db.rollDao()
    @Provides fun provideChemicalDao(db: AppDatabase) = db.chemicalDao()
    @Provides fun provideZoomLevelDao(db: AppDatabase) = db.zoomLevelDao()
    @Provides fun provideSettingDao(db: AppDatabase) = db.settingDao()
    @Provides fun provideBulkRollDao(db: AppDatabase) = db.bulkRollDao()

    @Provides @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .apply {
            if (BuildConfig.DEBUG)
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        }
        .build()

    @Provides @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl("https://api.openweathermap.org/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides @Singleton
    fun provideWeatherApi(retrofit: Retrofit): WeatherApi = retrofit.create(WeatherApi::class.java)
}
