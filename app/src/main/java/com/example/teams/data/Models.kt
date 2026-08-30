package com.example.teams.data

import com.google.firebase.firestore.PropertyName

enum class PlayCriteria {
    @PropertyName("none")
    NONE,
    @PropertyName("half")
    HALF_GAME,
    @PropertyName("three_quarters")
    THREE_QUARTERS
}

data class Team(
    val id: String = "",
    val name: String = "",
    val refereeAdminId: String = "",
    val coachEmail: String = "",
    val coachUid: String? = null,
    val playCriteria: PlayCriteria = PlayCriteria.NONE,
    val stats: Map<String, Any> = emptyMap()
)

data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val role: String = "COACH" // ADMIN or COACH
)
