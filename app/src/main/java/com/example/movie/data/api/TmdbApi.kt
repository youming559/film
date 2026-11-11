package com.example.movie.data.api

import com.example.movie.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

interface TmdbApi {

    /** 本周热门（电影+剧集混合） */
    @GET("trending/all/week")
    suspend fun trending(): PagedResponse<TmdbItem>

    /** 搜索电影+剧集 */
    @GET("search/multi")
    suspend fun searchMulti(@Query("query") query: String): PagedResponse<TmdbItem>
}

object TmdbClient {

    /**
     * 代理设置：
     * 国内实测 https://api.tmdb.org 可以直连（而 api.themoviedb.org 被墙），所以默认不用代理。
     * 如果哪天 api.tmdb.org 也不通了，就在这里填代理：
     * - 模拟器：填 "10.0.2.2"（指向宿主机），端口填代理软件端口（Clash 默认 7890）
     * - 真机：填电脑的局域网 IP，如 "192.168.1.x"
     */
    var proxyHost: String? = null
    var proxyPort: Int = 7890

    /** 自动附加 api_key 和中文语言参数 */
    private val authInterceptor = Interceptor { chain ->
        val url = chain.request().url.newBuilder()
            .addQueryParameter("api_key", BuildConfig.TMDB_API_KEY)
            .addQueryParameter("language", "zh-CN")
            .build()
        chain.proceed(chain.request().newBuilder().url(url).build())
    }

    val okHttp: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
        proxyHost?.takeIf { it.isNotBlank() }?.let { host ->
            builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(host, proxyPort)))
        }
        builder.build()
    }

    private val json = Json { ignoreUnknownKeys = true }

    val api: TmdbApi by lazy {
        Retrofit.Builder()
            // 注意：用 api.tmdb.org 这个域名，国内可直连；api.themoviedb.org 被墙
            .baseUrl("https://api.tmdb.org/3/")
            .client(okHttp)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TmdbApi::class.java)
    }
}
