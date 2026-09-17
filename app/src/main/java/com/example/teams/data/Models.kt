package com.example.teams.data

import com.google.firebase.firestore.PropertyName
import java.util.Date

enum class PlayCriteria {
    @PropertyName("none")
    NONE,
    @PropertyName("half")
    HALF_GAME,
    @PropertyName("three_quarters")
    THREE_QUARTERS
}

enum class GKFieldRequirement {
    @PropertyName("none")
    NONE,
    @PropertyName("one_quarter")
    ONE_QUARTER,
    @PropertyName("half_game")
    HALF_GAME
}

data class Team(
    val id: String? = null,
    val name: String? = null,
    val customName: String? = null,
    val refereeAdminId: String? = null,
    val coachEmail: String? = null,
    val coachUid: String? = null,
    val playCriteria: PlayCriteria? = PlayCriteria.NONE,
    val totalPoints: Long? = 0L,
    val organizationId: String? = null,
    val seasonId: String? = null,
    val divisionName: String? = null,
    val gender: String? = "Boys", // Match Referee app
    val subDivision: String? = null, // Link to sub-division rules
    val stats: Map<String, Any>? = emptyMap()
)

data class UserProfile(
    val uid: String? = null,
    val email: String? = null,
    val name: String? = null,
    val role: Map<String, String>? = emptyMap(),
    @get:PropertyName("tier") @set:PropertyName("tier")
    var tier: String? = "Base", // Renamed and annotated to match Referee app
    val organizationId: String? = null,
    val adminOrgIds: List<String>? = emptyList(),
    val coachOrgIds: List<String>? = emptyList(),
    val teamId: String? = null,
    val playerIds: List<String>? = emptyList(),
    val phoneNumber: String? = null,
    val bio: String? = null
) {
    fun isSystemAdmin(): Boolean {
        return role?.containsValue("SystemAdmin") == true
    }

    fun isCoachAdmin(): Boolean {
        return role?.containsValue("CoachAdmin") == true || 
               role?.containsValue("Admin") == true
    }

    fun isCoach(): Boolean {
        return role?.containsValue("Coach") == true
    }

    fun isParent(): Boolean {
        return role?.containsValue("Parent") == true
    }
}

enum class UserRole {
    @PropertyName("SystemAdmin")
    SYSTEM_ADMIN,
    @PropertyName("CoachAdmin")
    COACH_ADMIN,
    @PropertyName("Coach")
    COACH,
    @PropertyName("Parent")
    PARENT
}

data class Player(
    val id: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val teamId: String? = null,
    val organizationId: String? = null,
    val age: Int? = null,
    val shirtSize: String? = null,
    val jerseyNumber: String? = null,
    val position: String? = null, // Primary Position
    val preferredPositions: List<String>? = emptyList(),
    val chemistryPlayerIds: List<String>? = emptyList(), // UIDs of players they work well with
    val satPeriodsTotal: Int? = 0, // Track total sat periods for equal play time
    val playedPeriodsTotal: Int? = 0,
    val parentUids: List<String>? = emptyList()
)

data class ChatMessage(
    val id: String? = null,
    val senderUid: String? = null,
    val receiverUid: String? = null,
    val message: String? = null,
    val timestamp: Date? = Date(),
    val organizationId: String? = null
)

data class TeamPost(
    val id: String? = null,
    val teamId: String? = null,
    val coachUid: String? = null,
    val content: String? = null,
    val timestamp: Date? = Date()
)

data class Organization(
    val id: String? = null,
    val name: String? = null,
    val contactEmail: String? = null,
    @get:PropertyName("tier") @set:PropertyName("tier")
    var tier: String? = "Free",
    val isTeamAppSyncEnabled: Boolean? = false,
    val playCriteria: PlayCriteria? = PlayCriteria.NONE, // Organization-wide rule
    val gkFieldRequirement: GKFieldRequirement? = GKFieldRequirement.NONE
)

data class Season(
    val id: String? = null,
    val name: String? = null,
    val organizationId: String? = null,
    @get:PropertyName("active")
    val isActive: Boolean? = true,
    @get:PropertyName("live") @set:PropertyName("live")
    var live: Boolean? = false,
    val archivedAt: Date? = null,
    val startDate: Date? = null,
    val endDate: Date? = null,
    val collectPoints: Boolean? = false,
    val maxPointsPerWeekend: Int? = 0,
    val centerRefereePoints: Int? = 0,
    val assistantRefereePoints: Int? = 0,
    val divisions: List<Division>? = emptyList() 
)

data class Division(
    val name: String? = null,
    val subDivision: String? = null, // e.g. "Pool A"
    val playersPerTeam: Int? = 11,
    val halfDurationMinutes: Int? = 45,
    val ballSize: Int? = 5,
    val teamsAccumulatePoints: Boolean? = true,
    val playCriteria: PlayCriteria? = null,
    val gkFieldRequirement: GKFieldRequirement? = null
)

data class Game(
    val id: String? = null,
    val gameNumber: Int? = 0,
    val time: String? = "", // Added to match Referee app
    val homeTeamName: String? = null,
    val awayTeamName: String? = null,
    val date: Date? = Date(),
    val status: String? = null,
    val homeScore: Int? = null,
    val awayScore: Int? = null,
    val organizationId: String? = null,
    val seasonId: String? = null
)

data class PointAward(
    val id: String? = null,
    val gameId: String? = null,
    val refereeId: String? = null,
    val teamId: String? = null,
    val points: Int? = 0,
    val timestamp: Date? = Date(),
    val organizationId: String? = null,
    val seasonId: String? = null
)

enum class ScheduleItemType {
    @PropertyName("game")
    GAME,
    @PropertyName("practice")
    PRACTICE,
    @PropertyName("event")
    EVENT
}

data class ScheduleItem(
    val id: String? = null,
    val teamId: String? = null,
    val type: ScheduleItemType? = ScheduleItemType.EVENT,
    val title: String? = null,
    val date: Date? = Date(),
    val startTime: String? = null,
    val endTime: String? = null,
    val location: String? = null,
    val description: String? = null,
    val isRecurring: Boolean? = false,
    val recurringDays: List<Int>? = emptyList(),
    val linkedLineupId: String? = null
)

data class Lineup(
    val id: String? = null,
    val teamId: String? = null,
    val gameId: String? = null,
    val divisionType: Int? = 11,
    val periodAssignments: List<PeriodAssignment>? = emptyList(),
    val timestamp: Date? = Date()
)

data class PeriodAssignment(
    val periodNumber: Int? = 1,
    val positions: Map<String, String>? = emptyMap()
)

data class FieldPosition(
    val id: String? = null,
    val name: String? = null,
    val xPercent: Float? = 0f,
    val yPercent: Float? = 0f
)

enum class AttendanceStatus {
    @PropertyName("attending")
    ATTENDING,
    @PropertyName("not_attending")
    NOT_ATTENDING,
    @PropertyName("late")
    LATE,
    @PropertyName("unknown")
    UNKNOWN
}

data class Attendance(
    val playerId: String? = null,
    val scheduleItemId: String? = null,
    val status: AttendanceStatus? = AttendanceStatus.UNKNOWN,
    val timestamp: Date? = Date()
)
