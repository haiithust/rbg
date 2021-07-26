package io.rgb.sample

import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.recyclerview.widget.StaggeredGridLayoutManager.VERTICAL
import io.rgb.load
import io.rgb.sample.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    private lateinit var binding: ActivityMainBinding
    private lateinit var listAdapter: ImageListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        if (SDK_INT >= 29) {
            window.setDecorFitsSystemWindowsCompat(false)
            binding.toolbar.setOnApplyWindowInsetsListener { view, insets ->
                view.updatePadding(top = insets.systemWindowInsetTopCompat)
                insets
            }
        }

        listAdapter = ImageListAdapter(this) { viewModel.screen.value = it }
        binding.list.apply {
            setHasFixedSize(true)
            (itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
            layoutManager = StaggeredGridLayoutManager(listAdapter.numColumns, VERTICAL)
            adapter = listAdapter
        }

        lifecycleScope.apply {
            launch { viewModel.images.collect(::setImages) }
            launch { viewModel.screen.collect(::setScreen) }
        }
    }

    private fun setScreen(screen: Screen) {
        when (screen) {
            is Screen.List -> {
                binding.list.isVisible = true
                binding.detail.isVisible = false
            }
            is Screen.Detail -> {
                binding.list.isVisible = false
                binding.detail.isVisible = true
                binding.detail.load(screen.image.uri)
            }
        }
    }

    private fun setImages(images: List<Image>) {
        listAdapter.submitList(images) {
            // Ensure we're at the top of the list when the list items are updated.
            binding.list.scrollToPosition(0)
        }
    }

    override fun onBackPressed() {
        if (!viewModel.onBackPressed()) {
            super.onBackPressed()
        }
    }
}
