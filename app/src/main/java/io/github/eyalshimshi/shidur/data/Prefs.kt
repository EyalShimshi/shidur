package io.github.eyalshimshi.shidur.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/** Single preferences file shared by the app, the playback service and the widget (same process). */
fun Context.shidurPreferences(): SharedPreferences =
    applicationContext.getSharedPreferences("shidur", Context.MODE_PRIVATE)

/** Emits [read] right away and again after every change to these preferences. */
internal fun <T> SharedPreferences.observe(read: (SharedPreferences) -> T): Flow<T> = callbackFlow {
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, _ -> trySend(read(prefs)) }
    registerOnSharedPreferenceChangeListener(listener)
    trySend(read(this@observe))
    awaitClose { unregisterOnSharedPreferenceChangeListener(listener) }
}.conflate()
