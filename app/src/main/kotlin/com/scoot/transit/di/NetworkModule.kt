package com.scoot.transit.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.scoot.transit.BuildConfig
import com.scoot.transit.data.remote.BartEtdApi
import com.scoot.transit.data.remote.OpenRouteServiceApi
import com.scoot.transit.data.remote.SiriStopMonitoringApi
import com.scoot.transit.data.remote.TransitFeedApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory

@Qualifier annotation class FiveOneOne
@Qualifier annotation class Bart
@Qualifier annotation class Ors

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Provides @Singleton
    fun okHttp(): OkHttpClient {
        val log = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor(log)
            .build()
    }

    @Provides @Singleton @FiveOneOne
    fun retrofit511(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://api.511.org/")
            .client(client)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides @Singleton @Bart
    fun retrofitBart(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://api.bart.gov/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides @Singleton @Ors
    fun retrofitOrs(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://api.openrouteservice.org/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    fun transitFeedApi(@FiveOneOne r: Retrofit): TransitFeedApi = r.create(TransitFeedApi::class.java)

    @Provides
    fun siriApi(@FiveOneOne r: Retrofit): SiriStopMonitoringApi = r.create(SiriStopMonitoringApi::class.java)

    @Provides
    fun bartEtdApi(@Bart r: Retrofit): BartEtdApi = r.create(BartEtdApi::class.java)

    @Provides
    fun orsApi(@Ors r: Retrofit): OpenRouteServiceApi = r.create(OpenRouteServiceApi::class.java)
}
