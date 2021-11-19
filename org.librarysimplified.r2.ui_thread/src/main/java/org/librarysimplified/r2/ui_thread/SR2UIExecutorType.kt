package org.librarysimplified.r2.ui_thread

import java.util.concurrent.Executor

/**
 * An executor that executes runnables on the UI thread and properly cancels
 * their execution when disposed.
 * It is primarily meant to safely listen to futures on the UI thread in a lifecycle-aware manner.
 *
 * Disposing the executor cancels the execution of any runnable previously submitted
 * and not yet executed, as well as that of runnables that might be submitted in the future.
 * Runnables being executed are not an issue because they are executed on the UI thread,
 * which means that no UI-related lifecycle action can be performed at the same time.
 */

interface SR2UIExecutorType : Executor {

  /**
   * Execute the runnable on the UI thread after the given delay.
   */

  fun executeAfter(command: Runnable, ms: Long)

  /**
   * Cancel the execution of any runnable previously submitted
   * and not yet executed, as well as that of runnables that might be submitted in the future.
   */

  fun dispose()
}
