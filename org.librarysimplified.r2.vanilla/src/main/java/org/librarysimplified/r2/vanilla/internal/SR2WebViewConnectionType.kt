package org.librarysimplified.r2.vanilla.internal

import com.google.common.util.concurrent.ListenableFuture
import org.librarysimplified.r2.api.SR2ScrollingMode
import java.io.Closeable

/**
 * A connection to a web view.
 */

internal interface SR2WebViewConnectionType : Closeable {

  fun openURL(
    location: String
  ): ListenableFuture<*>

  fun setScrolling(
    mode: SR2ScrollingMode
  ): ListenableFuture<*>

  fun executeJS(
    f: (SR2JavascriptAPIType) -> ListenableFuture<*>
  ): ListenableFuture<Any>
}
