package com.podcastplayer.app.data.remote

import com.podcastplayer.app.domain.model.PodcastSearchResponse
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface iTunesApi {
    @GET("search")
    suspend fun searchPodcasts(
        @Query("term") term: String,
        @Query("media") media: String = "podcast",
        @Query("limit") limit: Int = 25
    ): Response<PodcastSearchResponse>

    companion object {
        private const val BASE_URL = "https://itunes.apple.com/"

        fun create(): iTunesApi {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(iTunesApi::class.java)
        }
    }
}
