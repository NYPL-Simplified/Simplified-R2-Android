package org.librarysimplified.r2.views.internal

import android.view.View

/**
 * Register a callback to be invoked when this view is clicked,
 * preventing new clicks for the given time in milliseconds.
 */

internal fun View.setOnClickListener(interval: Long, onClick: (View) -> Unit) {
  setOnClickListener(
    SR2DebouncingOnClickListener(interval, onClick)
  )
}

private class SR2DebouncingOnClickListener(
  private val interval: Long,
  private val onClick: (View) -> Unit
) : View.OnClickListener {

  private var isEnabled = true

  override fun onClick(view: View) {
    if (isEnabled) {
      isEnabled = false
      onClick.invoke(view)
      view.postDelayed(
        { isEnabled = true },
        interval
      )
    }
  }
}
