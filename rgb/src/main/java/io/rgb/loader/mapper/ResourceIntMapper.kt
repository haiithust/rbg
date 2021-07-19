package io.rgb.loader.mapper

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.core.net.toUri

internal class ResourceIntMapper(
    private val context: Context,
) : RequestDataMapper<Int> {
    override val acceptType: Class<Int> = Int::class.java

    override fun handles(@DrawableRes data: Int) = runCatching {
        context.resources.getResourceEntryName(data) != null
    }.isSuccess

    override fun map(@DrawableRes data: Int): Uri {
        return "${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/$data".toUri()
    }
}
