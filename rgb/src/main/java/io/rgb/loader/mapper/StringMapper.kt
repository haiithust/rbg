package io.rgb.loader.mapper

import androidx.core.net.toUri

internal class StringMapper : RequestDataMapper<String> {
    override val acceptType: Class<String> = String::class.java

    override fun map(data: String) = data.toUri()
}
