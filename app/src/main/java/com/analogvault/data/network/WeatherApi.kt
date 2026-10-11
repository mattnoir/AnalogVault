package com.analogvault.data.network

import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApi {
    @GET("data/2.5/weather")
    suspend fun getCurrentWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric"
    ): WeatherResponse

    /**
     * Place search by name.
     *
     * The Geocoding API is on the same free tier and the same key as current
     * weather — no separate subscription, no extra token. It returns several
     * matches because place names are not unique, and picking between two
     * Springfields is the user's job, not ours.
     */
    @GET("geo/1.0/direct")
    suspend fun searchPlaces(
        @Query("q") query: String,
        @Query("appid") apiKey: String,
        @Query("limit") limit: Int = 5,
    ): List<GeoPlace>
}

/**
 * A place the weather can be asked about.
 *
 * [state] is only populated for US results, which is exactly where it is needed
 * to tell two identically named towns apart.
 */
data class GeoPlace(
    val name: String,
    val lat: Double,
    val lon: Double,
    val country: String? = null,
    val state: String? = null,
) {
    /** "Bratislava, SK" or "Springfield, Illinois, US". */
    val label: String
        get() = listOfNotNull(
            name.takeIf { it.isNotBlank() },
            state?.takeIf { it.isNotBlank() },
            country?.takeIf { it.isNotBlank() },
        ).joinToString(", ")
}

data class WeatherResponse(
    val name: String,
    val main: MainData,
    val weather: List<WeatherDesc>,
    val wind: WindData,
    val clouds: CloudData,
    val visibility: Int?,
    val sys: SysData?,
    val dt: Long = 0,          // current time as Unix epoch (UTC)
    val timezone: Int = 0      // timezone offset in seconds from UTC
)

data class MainData(
    val temp: Double,
    val feels_like: Double,
    val humidity: Int,
    val pressure: Int
)

data class WeatherDesc(
    val id: Int,
    val main: String,
    val description: String,
    val icon: String
)

data class WindData(val speed: Double, val deg: Int?)
data class CloudData(val all: Int)
data class SysData(
    val country: String?,
    val sunrise: Long = 0,     // Unix epoch UTC
    val sunset: Long = 0       // Unix epoch UTC
)
