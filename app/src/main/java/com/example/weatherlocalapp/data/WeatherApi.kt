package com.example.weatherlocalapp.data

import retrofit2.http.GET
import retrofit2.http.Path

// --- Retrofit API Interface ---
interface WeatherApi {
    @GET("https://www.jma.go.jp/bosai/forecast/data/forecast/{areaCode}.json")
    suspend fun getForecast(@Path("areaCode") areaCode: String): List<ForecastResponse>

    @GET("https://www.jma.go.jp/bosai/warning/data/warning/{areaCode}.json")
    suspend fun getWarning(@Path("areaCode") areaCode: String): WarningResponse

    @GET("https://www.jma.go.jp/bosai/jmaradar/data/nowcast/forecast/basetime.json")
    suspend fun getRadarBasetime(): BasetimeResponse
}

// --- Data Models for Forecast ---
data class ForecastResponse(
    val publishingOffice: String?,
    val reportDatetime: String?,
    val timeSeries: List<TimeSeries>?
)

data class TimeSeries(
    val timeDefines: List<String>?,
    val areas: List<AreaForecast>?
)

data class AreaForecast(
    val area: AreaInfo?,
    val weathers: List<String>?,
    val winds: List<String>?,
    val waves: List<String>?,
    val temps: List<String>?
)

data class AreaInfo(
    val name: String?,
    val code: String?
)

// --- Data Models for Warnings ---
data class WarningResponse(
    val publishingOffice: String?,
    val reportDatetime: String?,
    val headline: String?,
    val warningAreaResult: WarningAreaResult?
)

data class WarningAreaResult(
    val code: String?,
    val name: String?,
    val areas: List<WarningArea>?
)

data class WarningArea(
    val code: String?,
    val name: String?,
    val warningLevel: Int?,
    val warnings: List<WarningInfo>?
)

data class WarningInfo(
    val code: String?,
    val name: String?,
    val status: String? // e.g. "継続", "発表"
)

// --- Data Model for Radar Basetime ---
data class BasetimeResponse(
    val basetime: String,
    val validtime: String
)
