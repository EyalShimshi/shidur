package io.github.eyalshimshi.shidur.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

/** Single preferences file shared by the app, the playback service and the widget (same process). */
val Context.shidurDataStore: DataStore<Preferences> by preferencesDataStore(name = "shidur")

internal fun DataStore<Preferences>.safeData(): Flow<Preferences> =
    data.catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
