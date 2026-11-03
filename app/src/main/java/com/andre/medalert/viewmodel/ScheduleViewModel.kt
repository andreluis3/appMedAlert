package com.andre.medalert.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.andre.medalert.data.local.entity.MedicationEntity
import com.andre.medalert.data.schedule.SessionMedications
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ScheduleViewModel(application: Application) : AndroidViewModel(application) {

    val medications: StateFlow<List<MedicationEntity>> = SessionMedications.observe(application)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}