package com.neonrush.game.net

import com.neonrush.game.storage.LocalStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/** Default production backend (Vercel). Can be overridden per device in Settings. */
object ApiConfig {
    const val DEFAULT_BASE_URL = "https://aass-api.vercel.app/"
}

/** Ensures the URL is a well-formed Retrofit base url (scheme + trailing slash). */
fun normalizeBaseUrl(raw: String): String? {
    var url = raw.trim()
    if (url.isEmpty()) return ApiConfig.DEFAULT_BASE_URL
    if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
    return try {
        url.trim().toHttpUrlOrNull()?.toString()
    } catch (t: Throwable) {
        null
    }
}

interface NeonRushApi {
    @GET("api/health")
    suspend fun health(): HealthResponse

    @POST("api/players")
    suspend fun register(@Body body: RegisterRequest): RegisterResponse

    @PATCH("api/players/{id}")
    suspend fun rename(@Path("id") id: String, @Body body: RenameRequest): PlayerDto

    @POST("api/scores")
    suspend fun submitScore(@Body body: ScoreSubmitRequest): ScoreSubmitResponse

    @GET("api/leaderboard")
    suspend fun leaderboard(
        @Query("mode") mode: String,
        @Query("key") key: String? = null,
        @Query("limit") limit: Int = 100
    ): LeaderboardResponse

    @GET("api/players/{id}/stats")
    suspend fun stats(@Path("id") id: String): PlayerStatsResponse
}

object ApiClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val cache = java.util.concurrent.ConcurrentHashMap<String, NeonRushApi>()

    /** Returns (and caches) an API client bound to the given base url. */
    fun forUrl(rawUrl: String?): NeonRushApi {
        val url = normalizeBaseUrl(rawUrl ?: "") ?: ApiConfig.DEFAULT_BASE_URL
        return cache.getOrPut(url) {
            Retrofit.Builder()
                .baseUrl(url)
                .client(http)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(NeonRushApi::class.java)
        }
    }
}

/**
 * Offline-tolerant repository: every call returns null on any failure so the
 * game always stays playable without internet.
 */
object Repo {

    private fun api(store: LocalStore) = ApiClient.forUrl(store.serverUrl)

    /** Returns the player id, registering the device once if needed. */
    suspend fun ensurePlayer(store: LocalStore): String? = withContext(Dispatchers.IO) {
        val existing = store.playerId
        if (existing != null) return@withContext existing
        try {
            val res = api(store).register(RegisterRequest(deviceId = store.deviceId))
            store.playerId = res.player.id
            store.nickname = res.player.nickname
            res.player.id
        } catch (t: Throwable) {
            null
        }
    }

    suspend fun submitScore(store: LocalStore, req: ScoreSubmitRequest): ScoreSubmitResponse? =
        withContext(Dispatchers.IO) {
            try {
                api(store).submitScore(req)
            } catch (t: Throwable) {
                null
            }
        }

    suspend fun leaderboard(store: LocalStore, mode: String, key: String?): LeaderboardResponse? =
        withContext(Dispatchers.IO) {
            try {
                api(store).leaderboard(mode, key)
            } catch (t: Throwable) {
                null
            }
        }

    suspend fun stats(store: LocalStore, playerId: String): PlayerStatsResponse? =
        withContext(Dispatchers.IO) {
            try {
                api(store).stats(playerId)
            } catch (t: Throwable) {
                null
            }
        }

    suspend fun rename(store: LocalStore, playerId: String, nickname: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                api(store).rename(playerId, RenameRequest(nickname))
                true
            } catch (t: Throwable) {
                false
            }
        }

    /** Ping the configured game server. Returns null when unreachable. */
    suspend fun health(store: LocalStore): HealthResponse? = withContext(Dispatchers.IO) {
        try {
            api(store).health()
        } catch (t: Throwable) {
            null
        }
    }
}
