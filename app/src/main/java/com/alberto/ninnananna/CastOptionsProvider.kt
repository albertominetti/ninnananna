package com.alberto.ninnananna

import android.content.Context
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider

/**
 * Opzioni del framework Cast, dichiarate nel manifest con la meta-data
 * OPTIONS_PROVIDER_CLASS_NAME. Usa il **Default Media Receiver** di Google
 * ("CC1AD845"): non richiede registrazione di un'app personalizzata.
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
