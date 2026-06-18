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
