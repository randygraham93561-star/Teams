package com.example.teams.data

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.tasks.await

class FirestoreRepository {
    val db = FirebaseFirestore.getInstance()
    private val teamsCollection = db.collection("teams")
    private val usersCollection = db.collection("users")

    suspend fun saveTeam(team: Team): String {
        val id = team.id ?: teamsCollection.document().id
        teamsCollection.document(id).set(team.copy(id = id), SetOptions.merge()).await()
        return id
    }

    private fun docToTeam(doc: DocumentSnapshot): Team? {
        if (!doc.exists()) return null
        return try {
            val data = doc.data
            Team(
                id = doc.id,
                name = data?.get("name") as? String,
                customName = data?.get("customName") as? String,
                refereeAdminId = data?.get("refereeAdminId") as? String,
                coachEmail = data?.get("coachEmail") as? String,
                coachUid = data?.get("coachUid") as? String,
                playCriteria = (data?.get("playCriteria") as? String)?.let { 
                    try { PlayCriteria.valueOf(it.uppercase()) } catch(e: Exception) { PlayCriteria.NONE }
                } ?: PlayCriteria.NONE,
                totalPoints = (data?.get("totalPoints") as? Number)?.toLong() ?: 0L,
                organizationId = data?.get("organizationId") as? String,
                seasonId = data?.get("seasonId") as? String,
                divisionName = data?.get("divisionName") as? String,
                gender = data?.get("gender") as? String ?: "Boys",
                subDivision = data?.get("subDivision") as? String,
                stats = data?.get("stats") as? Map<String, Any> ?: emptyMap()
            )
        } catch (e: Exception) {
            Log.e("Repository", "Error parsing Team: ${doc.id}", e)
            null
        }
    }

    suspend fun getTeamsForAdmin(adminUid: String): List<Team> {
        return try {
            val snapshot = teamsCollection.whereEqualTo("adminUid", adminUid).get().await()
            snapshot.documents.mapNotNull { docToTeam(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getTeamForCoach(email: String): Team? {
        return try {
            val snapshot = teamsCollection.whereEqualTo("coachEmail", email).get().await()
            val doc = snapshot.documents.firstOrNull() ?: return null
            docToTeam(doc)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getAvailableRefereeTeams(organizationId: String, seasonId: String? = null): List<Team> {
        return try {
            var query = teamsCollection.whereEqualTo("organizationId", organizationId)
            if (!seasonId.isNullOrEmpty()) {
                query = query.whereEqualTo("seasonId", seasonId)
            }
            val snapshot = query.get().await()
            snapshot.documents.mapNotNull { docToTeam(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getLiveSeason(organizationId: String): Season? {
        if (organizationId.isBlank()) return null
        return try {
            val snapshot = db.collection("seasons")
                .whereEqualTo("organizationId", organizationId)
                .whereEqualTo("live", true)
                .get()
                .await()
            val doc = snapshot.documents.firstOrNull()
            doc?.toObject<Season>()?.copy(id = doc.id)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getTeamById(teamId: String): Team? {
        return try {
            val snapshot = teamsCollection.document(teamId).get().await()
            docToTeam(snapshot)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveUserProfile(profile: UserProfile) {
        // Use set with SetOptions.merge() to avoid overwriting fields from other apps (like the Referee app's 'role')
        val uid = profile.uid ?: return
        usersCollection.document(uid).set(profile, SetOptions.merge()).await()
    }

    suspend fun getUserProfile(uid: String): UserProfile? {
        return try {
            val snapshot = usersCollection.document(uid).get().await()
            if (!snapshot.exists()) {
                Log.d("Repository", "Profile does not exist for UID: $uid")
                return null
            }
            
            val data = snapshot.data
            val email = data?.get("email") as? String
            val name = data?.get("name") as? String
            val organizationId = data?.get("organizationId") as? String
            val teamId = data?.get("teamId") as? String
            val tier = data?.get("tier") as? String
            
            // Handle complex 'role' field
            val rawRole = data?.get("role")
            val roleMap = when (rawRole) {
                is Map<*, *> -> rawRole.entries.associate { it.key.toString() to it.value.toString() }.toMap()
                is String -> mapOf("Teams" to rawRole)
                else -> emptyMap()
            }
            
            UserProfile(
                uid = uid,
                email = email,
                name = name,
                role = roleMap,
                organizationId = organizationId,
                teamId = teamId,
                tier = tier ?: "Base"
            )
        } catch (e: Exception) {
            Log.e("Repository", "Fatal profile load error for $uid", e)
            null
        }
    }

    suspend fun findExistingPlayerOnTeam(teamId: String, firstName: String, lastName: String): Player? {
        return try {
            val snapshot = db.collection("players")
                .whereEqualTo("teamId", teamId)
                .whereEqualTo("firstName", firstName.trim())
                .whereEqualTo("lastName", lastName.trim())
                .get()
                .await()
            snapshot.documents.firstOrNull()?.toObject<Player>()?.copy(id = snapshot.documents.first().id)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun savePlayer(player: Player): String {
        return if (player.id.isNullOrEmpty()) {
            val doc = db.collection("players").document()
            val newPlayer = player.copy(id = doc.id)
            doc.set(newPlayer).await()
            doc.id
        } else {
            db.collection("players").document(player.id!!).set(player).await()
            player.id!!
        }
    }

    suspend fun linkPlayerToParent(uid: String, playerId: String) {
        val user = getUserProfile(uid) ?: return
        val currentIds = user.playerIds ?: emptyList()
        if (!currentIds.contains(playerId)) {
            val newList = currentIds + playerId
            usersCollection.document(uid).update("playerIds", newList).await()
        }
        
        // Also update player to include parent UID
        val player = db.collection("players").document(playerId).get().await().toObject<Player>()
        val currentParents = player?.parentUids ?: emptyList()
        if (player != null && !currentParents.contains(uid)) {
            val newParents = currentParents + uid
            db.collection("players").document(playerId).update("parentUids", newParents).await()
        }
    }

    suspend fun getPlayersForParent(uid: String): List<Player> {
        return try {
            val user = getUserProfile(uid) ?: return emptyList()
            val playerIds = user.playerIds ?: emptyList()
            if (playerIds.isEmpty()) return emptyList()
            
            val snapshot = db.collection("players")
                .whereIn("id", playerIds)
                .get()
                .await()
            snapshot.documents.mapNotNull { doc ->
                try {
                    doc.toObject<Player>()?.copy(id = doc.id)
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getAllOrganizations(): List<Organization> {
        return try {
            val snapshot = db.collection("organizations").get().await()
            snapshot.documents.mapNotNull { it.toObject<Organization>()?.copy(id = it.id) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun updateUserOrganization(uid: String, orgId: String) {
        usersCollection.document(uid).update("organizationId", orgId).await()
    }

    // Messaging Logic
    suspend fun sendMessage(message: ChatMessage, sender: UserProfile, receiver: UserProfile) {
        val canMessage = when {
            sender.isCoachAdmin() -> sender.organizationId == receiver.organizationId
            sender.isCoach() -> {
                receiver.isParent() && sender.organizationId == receiver.organizationId
            }
            sender.isParent() -> {
                (receiver.isCoach() || receiver.isCoachAdmin()) &&
                        sender.organizationId == receiver.organizationId
            }
            else -> false
        }

        if (canMessage) {
            db.collection("messages").add(message).await()
        } else {
            throw Exception("Messaging rules violation: You cannot message this user.")
        }
    }

    fun getMessages(user1: String, user2: String) = db.collection("messages")
        .whereIn("senderUid", listOf(user1, user2))
        .whereIn("receiverUid", listOf(user1, user2))
        .orderBy("timestamp")

    suspend fun createTeamPost(post: TeamPost) {
        db.collection("team_posts").add(post).await()
    }

    fun getTeamPosts(teamId: String) = db.collection("team_posts")
        .whereEqualTo("teamId", teamId)
        .orderBy("timestamp")

    suspend fun updateTeamCustomName(teamId: String, customName: String) {
        teamsCollection.document(teamId).update("customName", customName).await()
    }

    suspend fun createScheduleItem(item: ScheduleItem) {
        val doc = db.collection("schedule").document()
        db.collection("schedule").document(doc.id).set(item.copy(id = doc.id)).await()
    }

    fun getScheduleFlow(teamId: String, teamName: String) = db.collection("schedule")
        .whereEqualTo("teamId", teamId)
        .snapshots()

    fun getTeamsFlow(organizationId: String) = teamsCollection
        .whereEqualTo("organizationId", organizationId)
        .snapshots()

    // Games are in 'games' collection, filtered by organization and then filtered by team name locally
    fun getGamesFlow(organizationId: String) = db.collection("games")
        .whereEqualTo("organizationId", organizationId)
        .snapshots()

    suspend fun saveAttendance(attendance: Attendance) {
        val id = "${attendance.playerId}_${attendance.scheduleItemId}"
        db.collection("attendance").document(id).set(attendance).await()
    }

    suspend fun saveOrganization(org: Organization): String {
        val id = if (org.id.isNullOrEmpty()) db.collection("organizations").document().id else org.id!!
        db.collection("organizations").document(id).set(org.copy(id = id)).await()
        return id
    }

    suspend fun updateOrganizationSubscription(orgId: String, tier: String, syncEnabled: Boolean) {
        db.collection("organizations").document(orgId).update(
            "tier", tier,
            "isTeamAppSyncEnabled", syncEnabled
        ).await()
    }

    fun getAttendanceFlow(scheduleItemId: String) = db.collection("attendance")
        .whereEqualTo("scheduleItemId", scheduleItemId)
        .snapshots()

    suspend fun saveLineup(lineup: Lineup): String {
        val id = if (lineup.id.isNullOrEmpty()) db.collection("lineups").document().id else lineup.id!!
        db.collection("lineups").document(id).set(lineup.copy(id = id)).await()
        return id
    }

    fun getLineupForGame(gameId: String) = db.collection("lineups")
        .whereEqualTo("gameId", gameId)
        .snapshots()

    suspend fun getRoster(teamId: String): List<Player> {
        return try {
            val snapshot = db.collection("players").whereEqualTo("teamId", teamId).get().await()
            snapshot.documents.mapNotNull { doc ->
                try {
                    doc.toObject<Player>()?.copy(id = doc.id)
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun saveSeason(season: Season): String {
        val id = if (season.id.isNullOrEmpty()) db.collection("seasons").document().id else season.id!!
        db.collection("seasons").document(id).set(season.copy(id = id), SetOptions.merge()).await()
        return id
    }

    suspend fun addDivisionToSeason(seasonId: String, division: Division) {
        val seasonDoc = db.collection("seasons").document(seasonId).get().await()
        val season = seasonDoc.toObject<Season>()
        val currentDivisions = season?.divisions ?: emptyList()
        val updatedDivisions = currentDivisions + division
        db.collection("seasons").document(seasonId).update("divisions", updatedDivisions).await()
    }

    suspend fun getDivisions(organizationId: String, seasonId: String? = null): List<Division> {
        return try {
            val season = if (!seasonId.isNullOrBlank()) {
                db.collection("seasons").document(seasonId).get().await().toObject<Season>()
            } else if (!organizationId.isNullOrBlank()) {
                getLiveSeason(organizationId)
            } else {
                null
            }
            season?.divisions ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun updateOrganizationRules(orgId: String, criteria: PlayCriteria, gkRequirement: GKFieldRequirement) {
        db.collection("organizations").document(orgId).update(
            "playCriteria", criteria,
            "gkFieldRequirement", gkRequirement
        ).await()
    }

    suspend fun getOrganizationById(orgId: String): Organization? {
        return try {
            val snapshot = db.collection("organizations").document(orgId).get().await()
            snapshot.toObject<Organization>()
        } catch (e: Exception) {
            null
        }
    }
}
