package io.rgb.loader.load

import android.view.View
import android.widget.ImageView
import io.rgb.Rgb
import io.rgb.image.ImageRequest
import kotlinx.coroutines.Job
import timber.log.Timber

internal class ViewTargetRequestManager(
    private val root: ImageView
) : View.OnAttachStateChangeListener {
    @Volatile
    var currentRequest: ImageRequest? = null
        private set

    @Volatile
    var currentRequestJob: Job? = null
        private set
    private var skipAttach = true

    fun setCurrentRequestJob(request: ImageRequest, job: Job) {
        currentRequestJob?.cancel()
        currentRequest = request
        currentRequestJob = job
        skipAttach = true
    }

    /** Detach the current request from this view. */
    fun clearCurrentRequest() {
        currentRequest = null
        currentRequestJob = null
    }

    override fun onViewAttachedToWindow(v: View) {
        if (skipAttach) {
            skipAttach = false
            return
        }
        currentRequest?.let {
            Rgb.load(root, it)
        }
    }

    override fun onViewDetachedFromWindow(v: View) {
        currentRequestJob?.cancel()
        skipAttach = false
        Timber.d("${v.hashCode()} - onViewDetachedFromWindow")
    }
}
