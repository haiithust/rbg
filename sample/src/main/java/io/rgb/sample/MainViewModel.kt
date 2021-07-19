package io.rgb.sample

import android.app.Application
import androidx.core.graphics.toColorInt
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.buffer
import okio.source
import org.json.JSONArray

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _images: MutableStateFlow<List<Image>> = MutableStateFlow(emptyList())
    val screen: MutableStateFlow<Screen> = MutableStateFlow(Screen.List)
    val images: StateFlow<List<Image>> = _images

    init {
        viewModelScope.launch {
            _images.value = loadImages("jpgs.json")
        }
    }

    fun onBackPressed(): Boolean {
        if (screen.value is Screen.Detail) {
            screen.value = Screen.List
            return true
        }
        return false
    }

    private suspend fun loadImages(fileName: String): List<Image> = withContext(Dispatchers.IO) {
        val images = mutableListOf<Image>()
        val json = JSONArray(context.assets.open(fileName).source().buffer().readUtf8())
        for (index in 0 until json.length()) {
            val image = json.getJSONObject(index)

            val url: String = image.getJSONObject("urls").getString("regular")
            val color: Int = image.getString("color").toColorInt()

            images += Image(
                uri = url,
                color = color,
                width = image.getInt("width"),
                height = image.getInt("height")
            )
        }

        images
    }
}
