package com.example.teams.ui.coach

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class CoachViewModel : ViewModel() {
    private val _text = MutableLiveData<String>().apply {
        value = "My Team Performance"
    }
    val text: LiveData<String> = _text
}
