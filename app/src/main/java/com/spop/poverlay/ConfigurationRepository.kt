package com.spop.poverlay

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow

class ConfigurationRepository(context: Context, lifecycleOwner: LifecycleOwner) : AutoCloseable {

    enum class Preferences(val key: String) {
        ShowTimerWhenMinimized("showTimerWhenMinimized"),
        BleTxEnabled("bleTxEnabled"),
        BleFtmsDeviceName("bleFtmsDeviceName")
    }

    companion object {
        const val SharedPrefsName = "configuration"
        // This workaround is required since SharedPreferences
        // only stores weak references to objects
        val SharedPreferenceListeners =
            mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()
    }

    private val mutableShowTimerWhenMinimized = MutableStateFlow(true)
    private val mutableBleTxEnabled = MutableStateFlow(true)
    private val mutableBleFtmsDeviceName = MutableStateFlow("Grupetto FTMS")

    val showTimerWhenMinimized = mutableShowTimerWhenMinimized
    val bleTxEnabled = mutableBleTxEnabled
    val bleFtmsDeviceName = mutableBleFtmsDeviceName

    private val sharedPreferences: SharedPreferences

    // Must be kept as reference, unowned lambda would be garbage collected
    private fun createSharedPreferencesListener() =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            updateFromSharedPrefs()
        }

    private val listener : SharedPreferences.OnSharedPreferenceChangeListener

    init {
        sharedPreferences = context.getSharedPreferences(SharedPrefsName, Context.MODE_PRIVATE)
        updateFromSharedPrefs()

        listener = createSharedPreferencesListener()
        SharedPreferenceListeners.add(listener)
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver{
            override fun onStop(owner: LifecycleOwner) {
                close()
            }
        })
    }

    fun setShowTimerWhenMinimized(isShown: Boolean) {
        mutableShowTimerWhenMinimized.value = isShown
        sharedPreferences.edit {
            putBoolean(Preferences.ShowTimerWhenMinimized.key, isShown)
        }
    }

    fun setBleTxEnabled(enabled: Boolean) {
        mutableBleTxEnabled.value = enabled
        sharedPreferences.edit {
            putBoolean(Preferences.BleTxEnabled.key, enabled)
        }
    }

    fun setBleFtmsDeviceName(name: String) {
        mutableBleFtmsDeviceName.value = name
        sharedPreferences.edit {
            putString(Preferences.BleFtmsDeviceName.key, name)
        }
    }

    private fun updateFromSharedPrefs() {
        mutableShowTimerWhenMinimized.value =
            sharedPreferences
                .getBoolean(Preferences.ShowTimerWhenMinimized.key, true)

        mutableBleTxEnabled.value =
            sharedPreferences
                .getBoolean(Preferences.BleTxEnabled.key, true)

        mutableBleFtmsDeviceName.value =
            sharedPreferences
                .getString(Preferences.BleFtmsDeviceName.key, "Grupetto FTMS") ?: "Grupetto FTMS"
    }

    override fun close() {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
        SharedPreferenceListeners.remove(listener)
    }
}