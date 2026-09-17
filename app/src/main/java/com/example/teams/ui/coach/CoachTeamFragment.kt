package com.example.teams.ui.coach

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.teams.R
import com.example.teams.data.*
import com.example.teams.databinding.FragmentCoachTeamBinding
import com.example.teams.databinding.ItemScheduleBinding
import com.example.teams.databinding.ItemTransformBinding
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CoachTeamFragment : Fragment() {

    private var _binding: FragmentCoachTeamBinding? = null
    private val binding get() = _binding!!
    private val auth = FirebaseAuth.getInstance()
    private val repository = FirestoreRepository()
    
    private var currentTeamId: String? = null
    private var isCoach: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCoachTeamBinding.inflate(inflater, container, false)
        
        val adapter = ScheduleAdapter(
            onClick = { item ->
                if (!isCoach) showAttendanceDialog(item)
            },
            onLineupClick = { item ->
                val bundle = Bundle().apply {
                    putString("teamId", currentTeamId)
                    putString("gameId", item.id)
                }
                findNavController().navigate(R.id.nav_lineup, bundle)
            }
        )
        binding.recyclerviewSchedule.adapter = adapter

        loadInitialData()
        
        binding.btnShareLink.setOnClickListener {
            shareLink()
        }

        binding.btnLogoutBlockedCoach.setOnClickListener {
            auth.signOut()
            findNavController().navigate(R.id.nav_login)
        }

        binding.fabAddSchedule.setOnClickListener {
            val teamId = currentTeamId
            if (teamId != null) {
                val bundle = Bundle().apply { putString("teamId", teamId) }
                findNavController().navigate(R.id.nav_add_schedule_item, bundle)
            } else {
                Toast.makeText(context, "Team data not loaded yet.", Toast.LENGTH_SHORT).show()
            }
        }

        
        return binding.root
    }

    private fun loadInitialData() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                Log.d("CoachDash", "Loading initial data for UID...")
                val uid = auth.currentUser?.uid ?: return@launch
                val profile = repository.getUserProfile(uid)
                
                profile?.let { prof ->
                    Log.d("CoachDash", "User role identified from map")
                    
                    val isUserCoach = prof.isCoach()
                    val isUserParent = prof.isParent()

                    _binding?.let { b ->
                        if (isUserParent && !isUserCoach) {
                            isCoach = false
                            b.fabAddSchedule.hide()
                            setupParentDashboard(uid)
                        } else if (isUserCoach) {
                            isCoach = true
                            b.layoutParentTeamSelector.visibility = View.GONE
                            
                            // 1. Try to get team by the explicit ID in the profile
                            Log.d("CoachDash", "Looking for team by ID: ${prof.teamId}")
                            var team = if (!prof.teamId.isNullOrEmpty()) repository.getTeamById(prof.teamId!!) else null
                            
                            // 2. If not found, fallback to email lookup (matching Referee app)
                            if (team == null) {
                                val email = prof.email ?: ""
                                Log.d("CoachDash", "Team ID not found, trying email fallback: $email")
                                team = repository.getTeamForCoach(email) ?: repository.getTeamForCoach(email.lowercase())
                            }
                            
                            if (team != null) {
                                Log.d("CoachDash", "Team found: ${team.name}. Setting up view.")
                                currentTeamId = team.id
                                setupTeamView(team)
                            } else {
                                Log.e("CoachDash", "No team found for coach profile: ${prof.uid}")
                                if (isAdded) {
                                    Toast.makeText(context, "No assigned team found. Please check with your Admin.", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                } ?: run {
                    if (isAdded) {
                        Toast.makeText(context, "User profile not found. Please log in again.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("CoachDash", "CRITICAL: Error in loadInitialData", e)
            }
        }
    }

    private suspend fun setupParentDashboard(uid: String) {
        try {
            val players = repository.getPlayersForParent(uid)
            val teamIds = players.mapNotNull { it.teamId }.distinct()
            if (teamIds.isEmpty()) {
                if (isAdded) {
                    Toast.makeText(context, "No teams assigned. Ask your coach to add you.", Toast.LENGTH_LONG).show()
                }
                return
            }
            
            val teams = teamIds.mapNotNull { repository.getTeamById(it) }
            
            if (teams.isEmpty()) {
                if (isAdded) {
                    Toast.makeText(context, "Assigned teams could not be found.", Toast.LENGTH_LONG).show()
                }
                return
            }
    
            val b = _binding
            val currentContext = context
            if (teams.size > 1 && b != null && currentContext != null) {
                b.layoutParentTeamSelector.visibility = View.VISIBLE
                val adapter = ArrayAdapter(currentContext, android.R.layout.simple_dropdown_item_1line, teams.map { it.name ?: "Unknown" })
                b.autoCompleteParentTeam.setAdapter(adapter)
                b.autoCompleteParentTeam.setOnItemClickListener { _, _, pos, _ ->
                    currentTeamId = teams[pos].id
                    setupTeamView(teams[pos])
                }
            }
            
            if (teams.isNotEmpty()) {
                currentTeamId = teams[0].id
                setupTeamView(teams[0])
            }
        } catch (e: Exception) {
            Log.e("CoachDash", "Error in setupParentDashboard", e)
        }
    }

    private fun setupTeamView(team: Team) {
        val b = _binding ?: return
        Log.d("CoachDash", "Setting up view for team: ${team.id}")
        
        // Check organization subscription
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val orgId = team.organizationId
                val orgs = repository.getAllOrganizations()
                val org = if (orgId.isNullOrBlank()) null else orgs.find { it.id == orgId }
                val tier = org?.tier ?: "Free"
                
                Log.d("CoachDash", "Org identified: ${org?.name}, Tier: $tier")

                // Always use a local, null-safe binding reference inside coroutines
                _binding?.let { currentBinding ->
                    val isEnabled = tier == "Standard" || tier == "Pro" || org?.isTeamAppSyncEnabled == true
                    currentBinding.cardUpgradeRequiredCoach.visibility = if (isEnabled) View.GONE else View.VISIBLE
                    if (!isEnabled) {
                        currentBinding.cardUpgradeRequiredCoach.bringToFront()
                        currentBinding.recyclerviewSchedule.visibility = View.GONE
                        currentBinding.fabAddSchedule.hide()
                        currentBinding.btnShareLink.visibility = View.GONE
                    } else {
                        currentBinding.recyclerviewSchedule.visibility = View.VISIBLE
                        if (isCoach) {
                            currentBinding.fabAddSchedule.show()
                        }
                        currentBinding.btnShareLink.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                Log.e("CoachDash", "Error checking subscription", e)
            }
        }

        val displayName = team.customName ?: team.name ?: "Unknown Team"
        b.textCoachTeam.text = "$displayName Dashboard"
        
        val points = team.totalPoints ?: 0
        b.textPointsDisplay.text = "Referee Points: $points"
        
        val teamId = team.id ?: "N/A"
        b.textTeamLink.text = "teams://join/$teamId"

        binding.btnHomeSettings.setOnClickListener {
            findNavController().navigate(R.id.nav_settings)
        }

        observeSchedule(team)
    }

    private fun observeSchedule(team: Team) {
        val myTeamId = team.name ?: ""
        val myDisplayName = team.customName ?: team.name ?: "Team"
        Log.d("CoachDash", "Observing schedule for teamName: $myTeamId")

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Combine Games from Referee, Events from Coach, and all Teams to resolve names
                combine(
                    repository.getScheduleFlow(team.id ?: "", team.name ?: ""),
                    repository.getGamesFlow(team.organizationId ?: ""),
                    repository.getTeamsFlow(team.organizationId ?: "")
                ) { coachItems, refereeGames, allOrgTeams ->
                    Log.d("CoachDash", "Combine triggered: CoachItems=${coachItems.size()}, Games=${refereeGames.size()}, Teams=${allOrgTeams.size()}")
                    val schedule = mutableListOf<ScheduleDisplayItem>()
                    
                    try {
                        // Map of Team ID -> Best Display Name
                        val teamNameMap = allOrgTeams.documents.associate { doc ->
                            val t = try { doc.toObject(Team::class.java) } catch (e: Exception) { null }
                            (t?.name ?: doc.id) to (t?.customName ?: t?.name ?: "Unknown Team")
                        }
                        
                        // Add Games
                        val games = refereeGames.documents.mapNotNull { 
                            try { it.toObject(Game::class.java) } catch (e: Exception) { null }
                        }.filter { it.homeTeamName == myTeamId || it.awayTeamName == myTeamId }
                        
                        Log.d("CoachDash", "Processing ${games.size} filtered games")

                        games.forEach {
                            val opponentId = if(it.homeTeamName == myTeamId) it.awayTeamName else it.homeTeamName
                            val isApproved = it.status == "ReportApproved" || it.status == "Completed"
                            
                            val resultText = if (isApproved && it.homeScore != null && it.awayScore != null) {
                                "Final: ${it.homeScore} - ${it.awayScore}"
                            } else {
                                it.status ?: "Scheduled"
                            }
                            
                            // Display: [MyDisplayName] vs [OpponentDisplayName]
                            val opponentDisplayName = teamNameMap[opponentId ?: ""] ?: opponentId ?: "Unknown"
                            val gameTitle = "$myDisplayName vs $opponentDisplayName"
                            
                            val displayTime = formatTimeToAmPm(it.time ?: "")
                            schedule.add(ScheduleDisplayItem(it.id ?: "", gameTitle, it.date ?: Date(), "$displayTime | $resultText", isGame = true))
                        }
                        
                        // Add Practices/Events
                        coachItems.documents.forEach { doc ->
                            val item = try { doc.toObject(ScheduleItem::class.java) } catch (e: Exception) { null }
                            item?.let {
                                val typeLabel = if(it.type == ScheduleItemType.PRACTICE) "Practice" else it.title ?: "Event"
                                val timeRange = "${formatTimeToAmPm(it.startTime ?: "")} - ${formatTimeToAmPm(it.endTime ?: "")}"
                                schedule.add(ScheduleDisplayItem(it.id ?: "", title = "$myDisplayName $typeLabel", date = it.date ?: Date(), details = "$timeRange @ ${it.location ?: ""}"))
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("CoachDash", "Error processing schedule components", e)
                    }
                    
                    schedule.sortedBy { it.date }
                }.collect { items ->
                    Log.d("CoachDash", "Collected ${items.size} schedule items. Updating UI.")
                    _binding?.let { currentBinding ->
                        (currentBinding.recyclerviewSchedule.adapter as? ScheduleAdapter)?.submitList(items)
                    }
                }
            } catch (e: Exception) {
                Log.e("CoachDash", "Critical error in observeSchedule", e)
            }
        }
    }

    private fun formatTimeToAmPm(time24: String): String {
        if (time24.isEmpty()) return ""
        return try {
            val sdf24 = SimpleDateFormat("HH:mm", Locale.US)
            val sdfAmPm = SimpleDateFormat("h:mm a", Locale.US)
            val date = sdf24.parse(time24)
            if (date != null) sdfAmPm.format(date) else time24
        } catch (e: Exception) {
            time24 // Fallback if format is different
        }
    }

    private fun showAttendanceDialog(item: ScheduleDisplayItem) {
        val options = arrayOf("Will Attend", "Will Not Attend", "Will be Late")
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Attendance for ${item.title}")
            .setItems(options) { _, which ->
                val status = when(which) {
                    0 -> AttendanceStatus.ATTENDING
                    1 -> AttendanceStatus.NOT_ATTENDING
                    else -> AttendanceStatus.LATE
                }
                markAttendance(item.id, status)
            }.show()
    }

    private fun markAttendance(itemId: String, status: AttendanceStatus) {
        viewLifecycleOwner.lifecycleScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            val players = repository.getPlayersForParent(uid)
            players.forEach { player ->
                repository.saveAttendance(Attendance(player.id, itemId, status))
            }
            if (isAdded) {
                Toast.makeText(context, "Attendance marked!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareLink() {
        val b = _binding ?: return
        val link = b.textTeamLink.text.toString()
        if (link.isEmpty()) return
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, "Join our team! $link")
        }
        startActivity(android.content.Intent.createChooser(intent, "Share Link"))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    data class ScheduleDisplayItem(
        val id: String, 
        val title: String, 
        val date: Date, 
        val details: String, 
        val isGame: Boolean = false
    )

    class ScheduleAdapter(
        val onClick: (ScheduleDisplayItem) -> Unit,
        val onLineupClick: (ScheduleDisplayItem) -> Unit
    ) : ListAdapter<ScheduleDisplayItem, ScheduleViewHolder>(ScheduleDiff()) {
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScheduleViewHolder {
            val binding = ItemScheduleBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ScheduleViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ScheduleViewHolder, position: Int) {
            try {
                val item = getItem(position)
                val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.US)
                
                holder.binding.textDate.text = sdf.format(item.date).uppercase()
                holder.binding.textTitle.text = item.title
                holder.binding.textDetails.text = item.details
                
                holder.binding.btnLineup.visibility = if (item.isGame) View.VISIBLE else View.GONE
                holder.binding.btnLineup.setOnClickListener { onLineupClick(item) }
                
                holder.itemView.setOnClickListener { onClick(item) }
            } catch (e: Exception) {
                Log.e("CoachDash", "Error binding schedule item at pos $position", e)
            }
        }
    }

    class ScheduleViewHolder(val binding: ItemScheduleBinding) : 
        RecyclerView.ViewHolder(binding.root)

    class ScheduleDiff : DiffUtil.ItemCallback<ScheduleDisplayItem>() {
        override fun areItemsTheSame(old: ScheduleDisplayItem, new: ScheduleDisplayItem) = old.id == new.id
        override fun areContentsTheSame(old: ScheduleDisplayItem, new: ScheduleDisplayItem) = old == new
    }
}
