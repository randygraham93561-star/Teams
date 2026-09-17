package com.example.teams.ui.admin

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.teams.data.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.toObject

class AdminViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    
    private val _teams = MutableLiveData<List<Team>>()
    val teams: LiveData<List<Team>> = _teams

    private val _organizations = MutableLiveData<List<Organization>>()
    val organizations: LiveData<List<Organization>> = _organizations

    private val _seasons = MutableLiveData<List<Season>>()
    val seasons: LiveData<List<Season>> = _seasons

    private val _games = MutableLiveData<List<Game>>()
    val games: LiveData<List<Game>> = _games

    private val _pointAwards = MutableLiveData<List<PointAward>>()
    val pointAwards: LiveData<List<PointAward>> = _pointAwards

    private val _isSyncEnabled = MutableLiveData<Boolean>(true)
    val isSyncEnabled: LiveData<Boolean> = _isSyncEnabled

    private val registrations = mutableListOf<ListenerRegistration>()
    private var currentOrgId: String? = null
    private var isSystemAdmin: Boolean = false

    fun setAccessLevel(orgId: String?, systemAdmin: Boolean) {
        if (currentOrgId == orgId && isSystemAdmin == systemAdmin) return
        currentOrgId = orgId
        isSystemAdmin = systemAdmin
        stopRealTimeSync()
        startRealTimeSync(orgId, systemAdmin)
    }

    private fun startRealTimeSync(orgId: String?, systemAdmin: Boolean) {
        // 1. Teams
        val teamsQuery = if (systemAdmin) db.collection("teams") 
                         else db.collection("teams").whereEqualTo("organizationId", orgId)
        
        val teamsListener = teamsQuery.addSnapshotListener { snapshots, error ->
            if (error != null) return@addSnapshotListener
            snapshots?.let {
                _teams.value = it.documents.mapNotNull { doc ->
                    try {
                        doc.toObject<Team>()?.copy(id = doc.id)
                    } catch (e: Exception) {
                        Log.e("AdminViewModel", "Error parsing Team: ${doc.id}", e)
                        null
                    }
                }
            }
        }
        registrations.add(teamsListener)

        // 2. Organizations
        val orgsListener = if (systemAdmin) {
            db.collection("organizations").addSnapshotListener { snapshots, error ->
                if (error != null) return@addSnapshotListener
                snapshots?.let {
                    val list = it.documents.mapNotNull { doc -> 
                        try {
                            doc.toObject<Organization>()?.copy(id = doc.id)
                        } catch (e: Exception) {
                            Log.e("AdminViewModel", "Error parsing Org: ${doc.id}", e)
                            null
                        }
                    }
                    _organizations.value = list
                    _isSyncEnabled.value = true // System Admin always bypasses blocks
                }
            }
        } else {
            db.collection("organizations").document(if (orgId.isNullOrBlank()) "NONE" else orgId).addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                snapshot?.let {
                    val org = try { it.toObject<Organization>()?.copy(id = it.id) } catch (e: Exception) { null }
                    org?.let { o ->
                        _organizations.value = listOf(o)
                        // Trust the 'tier' field primarily for unlocking sync
                        val tier = o.tier ?: "Free"
                        _isSyncEnabled.value = tier == "Standard" || tier == "Pro" || o.isTeamAppSyncEnabled == true
                    }
                }
            }
        }
        orgsListener?.let { registrations.add(it) }

        // 3. Seasons
        val seasonsQuery = if (systemAdmin) db.collection("seasons")
                          else db.collection("seasons").whereEqualTo("organizationId", orgId)

        val seasonsListener = seasonsQuery.addSnapshotListener { snapshots, error ->
            if (error != null) return@addSnapshotListener
            snapshots?.let {
                _seasons.value = it.documents.mapNotNull { doc ->
                    try {
                        doc.toObject<Season>()?.copy(id = doc.id)
                    } catch (e: Exception) {
                        Log.e("AdminViewModel", "Error parsing Season: ${doc.id}", e)
                        null
                    }
                }
            }
        }
        registrations.add(seasonsListener)

        // 4. Games
        val gamesQuery = if (systemAdmin) db.collection("games")
                         else db.collection("games").whereEqualTo("organizationId", orgId)

        val gamesListener = gamesQuery.addSnapshotListener { snapshots, error ->
            if (error != null) return@addSnapshotListener
            snapshots?.let {
                _games.value = it.documents.mapNotNull { doc ->
                    try {
                        doc.toObject<Game>()?.copy(id = doc.id)
                    } catch (e: Exception) {
                        Log.e("AdminViewModel", "Error parsing Game: ${doc.id}", e)
                        null
                    }
                }
            }
        }
        registrations.add(gamesListener)

        // 5. Point Awards
        val pointsQuery = if (systemAdmin) db.collection("referee_points")
                          else db.collection("referee_points").whereEqualTo("organizationId", orgId)

        val pointsListener = pointsQuery.addSnapshotListener { snapshots, error ->
            if (error != null) return@addSnapshotListener
            snapshots?.let {
                _pointAwards.value = it.documents.mapNotNull { doc ->
                    try {
                        doc.toObject<PointAward>()?.copy(id = doc.id)
                    } catch (e: Exception) {
                        Log.e("AdminViewModel", "Error parsing PointAward: ${doc.id}", e)
                        null
                    }
                }
            }
        }
        registrations.add(pointsListener)
    }

    private fun stopRealTimeSync() {
        registrations.forEach { it.remove() }
        registrations.clear()
    }

    override fun onCleared() {
        super.onCleared()
        stopRealTimeSync()
    }
}
