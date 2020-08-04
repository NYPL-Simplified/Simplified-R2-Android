package org.librarysimplified.r2.drm.core

import android.content.Context
import org.readium.r2.shared.publication.ContentProtection

interface ContentProtectionProvider {

    fun create(context: Context): ContentProtection

}
