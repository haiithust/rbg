package io.rgb.loader.mapper

import android.net.Uri

interface RequestDataMapper<T : Any> {
    val acceptType: Class<*>

    fun handles(data: T): Boolean = true

    fun map(data: T): Uri
}
