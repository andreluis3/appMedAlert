package com.andre.medalert.data.repository

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

// Extensão para criar o DataStore
private val Context.dataStore by preferencesDataStore(name = "user_prefs")

