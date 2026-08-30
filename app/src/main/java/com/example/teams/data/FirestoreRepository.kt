package com.example.teams.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import kotlinx.coroutines.tasks.await

class FirestoreRepository {
    private val db = FirebaseFirestore.getInstance()
    private val teamsCollection = db.collection("teams")
    private val usersCollection = db.collection("users")

    suspend fun saveTeam(team: Team) {
        teamsCollection.document(team.id).set(team).await()
    }

    suspend fun getTeamsForAdmin(adminUid: String): List<Team> {
        return try {
            val snapshot = teamsCollection.whereEqualTo("adminUid", adminUid).get().await()
            snapshot.documents.mapNotNull { it.toObject<Team>() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getTeamForCoach(email: String): Team? {
        return try {
            val snapshot = teamsCollection.whereEqualTo("coachEmail", email).get().await()
            snapshot.documents.firstOrNull()?.toObject<Team>()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveUserProfile(profile: UserProfile) {
        usersCollection.document(profile.uid).set(profile).await()
    }
}
