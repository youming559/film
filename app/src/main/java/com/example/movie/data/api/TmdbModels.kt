package com.example.movie.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PagedResponse<T>(
    val results: List<T> = emptyList()
)

/**
 * TMDb 条目（电影/剧集通用，来自 trending/all 或 search/multi）
 */
@Serializable
data class TmdbItem(
    val id: Int,
    val title: String? = null,               // 电影标题
    val name: String? = null,                // 剧集标题
    @SerialName("media_type") val mediaType: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    val overview: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,      // 电影上映日期
    @SerialName("first_air_date") val firstAirDate: String? = null,   // 剧集首播日期
    @SerialName("vote_average") val voteAverage: Double = 0.0
) {
    val displayTitle: String get() = title ?: name ?: "未知标题"
    val displayDate: String? get() = releaseDate?.ifBlank { null } ?: firstAirDate?.ifBlank { null }
    val type: String get() = mediaType ?: "movie"
    val posterUrl: String? get() = posterPath?.let { "https://images.tmdb.org/t/p/w500$it" }
    val typeLabel: String get() = if (type == "tv") "剧集" else "电影"
}
