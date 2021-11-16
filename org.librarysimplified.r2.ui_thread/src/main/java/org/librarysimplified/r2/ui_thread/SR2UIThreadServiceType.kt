package org.librarysimplified.r2.ui_thread

import android.os.Handler
import android.os.Looper

/**
 * A service to run functions on the Android UI thread.
 */

interface SR2UIThreadServiceType {

  /**
   * Check that the current thread is the UI thread and raise [ ] if it isn't.
   */

  fun checkIsUIThread() {
    check(isUIThread()) {
      String.format(
        "Current thread '%s' is not the Android UI thread",
        Thread.currentThread()
      )
    }
  }

  /**
   * @return `true` iff the current thread is the UI thread.
   */

  fun isUIThread(): Boolean {
    return Looper.getMainLooper().thread === Thread.currentThread()
  }

  /**
   * Run the given Runnable on the UI thread.
   *
   * @param r The runnable
   */

  fun runOnUIThreadUnsafe(r: Runnable) {
    val looper = Looper.getMainLooper()
    val h = Handler(looper)
    h.post(r)
  }

  /**
   * Run the given Runnable on the UI thread after the specified delay.
   *
   * @param r The runnable
   * @param ms The delay in milliseconds
   */

  fun runOnUIThreadUnsafeDelayed(
    r: Runnable,
    ms: Long
  ) {
    val looper = Looper.getMainLooper()
    val h = Handler(looper)
    h.postDelayed(r, ms)
  }

  /**
   * Create a new UI executor.
   */

  fun createExecutor(): SR2UIExecutorType {
    return SR2UIExecutor()
  }
}
