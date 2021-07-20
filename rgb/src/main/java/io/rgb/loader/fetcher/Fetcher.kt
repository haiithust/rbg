package io.rgb.loader.fetcher

import io.rgb.image.ImageSize

interface Fetcher<T : Any> {

    fun handles(data: T): Boolean = true

    fun key(data: T): String?

    suspend fun fetch(
        data: T,
        size: ImageSize,
    ): FetchResult
}
