package com.example.weatherlocalapp.data

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class WeatherRepository {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://www.jma.go.jp/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val weatherApi = retrofit.create(WeatherApi::class.java)

    suspend fun getForecast(areaCode: String): Result<List<ForecastResponse>> {
        return runCatching {
            weatherApi.getForecast(areaCode)
        }
    }

    suspend fun getWarning(areaCode: String): Result<WarningResponse> {
        return runCatching {
            weatherApi.getWarning(areaCode)
        }
    }

    suspend fun getRadarBasetime(): Result<BasetimeResponse> {
        return runCatching {
            weatherApi.getRadarBasetime()
        }
    }
}
