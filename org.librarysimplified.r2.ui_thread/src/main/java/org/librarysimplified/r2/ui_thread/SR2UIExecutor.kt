package org.librarysimplified.r2.ui_thread

import android.os.Handler
import android.os.Looper

internal class SR2UIExecutor : SR2UIExecutorType {

  private val handler: Handler =
    Handler(Looper.getMainLooper())

  private val callbacks: MutableList<Runnable> =
    mutableListOf()

  private var isDisposed: Boolean =
    false

  private val lock: Any =
    Any()

  override fun execute(command: Runnable) {
    synchronized(this.lock) {
      if (!this.isDisposed) {
        this.callbacks.add(command)
        this.handler.post(command)
      }
    }
  }

  override fun executeAfter(command: Runnable, ms: Long) {
    synchronized(this.lock) {
      if (!this.isDisposed) {
        this.callbacks.add(command)
        this.handler.postDelayed(command, ms)
      }
    }
  }

  override fun dispose() {
    synchronized(this.lock) {
      this.isDisposed = true
      while (this.callbacks.isNotEmpty()) {
        val callback = this.callbacks.removeFirst()
        this.handler.removeCallbacks(callback)
      }
    }
  }
}
