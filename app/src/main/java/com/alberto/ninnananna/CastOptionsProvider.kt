package com.alberto.ninnananna

import android.content.Context
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider

/**
 * Options of the Cast framework, declared in the manifest with the
 * OPTIONS_PROVIDER_CLASS_NAME meta-data. Uses Google's **Default Media
 * Receiver** ("CC1AD845"): no custom app registration required.
 */
class CastOptionsProvider : OptionsProvider {

    override fun getCastOptions(context: Context): CastOptions {
        return CastOptions.Builder()
            .setReceiverApplicationId("CC1AD845")
            .build()
    }

    override fun getAdditionalSessionProviders(context: Context): MutableList<SessionProvider>? {
        return null
    }
}
