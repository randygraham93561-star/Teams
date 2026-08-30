package com.example.teams.ui.admin

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class AdminViewModel : ViewModel() {
    private val _text = MutableLiveData<String>().apply {
        value = "This is admin dashboard"
    }
    val text: LiveData<String> = _text
}
