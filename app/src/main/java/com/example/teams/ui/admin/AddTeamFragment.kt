package com.example.teams.ui.admin

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.teams.R
import com.example.teams.data.FirestoreRepository
import com.example.teams.data.PlayCriteria
import com.example.teams.data.Team
import com.example.teams.data.UserProfile
import com.example.teams.databinding.FragmentAddTeamBinding
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AddTeamFragment : Fragment() {

    private var _binding: FragmentAddTeamBinding? = null
    private val binding get() = _binding!!
    private val repository = FirestoreRepository()
    private val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
    private var selectedTeam: Team? = null
    private var organizationId: String = ""

    // Secondary auth to create users without signing out the admin
    private var secondaryAuth: com.google.firebase.auth.FirebaseAuth? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddTeamBinding.inflate(inflater, container, false)
        
        fetchAdminOrgAndLoadTeams()
        
        binding.btnSave.setOnClickListener {
            saveTeamAssignment()
        }
        
        return binding.root
    }

    private fun fetchAdminOrgAndLoadTeams() {
        viewLifecycleOwner.lifecycleScope.launch {
            auth.currentUser?.uid?.let { uid ->
                repository.getUserProfile(uid)?.let { profile ->
                    val orgId = profile.organizationId
                    if (orgId?.isNotEmpty() == true) {
                        organizationId = orgId
                        loadLiveSeasonAndTeams(organizationId)
                    }
                }
            }
        }
    }

    private fun loadLiveSeasonAndTeams(orgId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val liveSeason = repository.getLiveSeason(orgId)
                val teams = repository.getAvailableRefereeTeams(orgId, liveSeason?.id)
                
                if (teams.isNotEmpty()) {
                    val teamNames = teams.map { team ->
                        val div = team.divisionName ?: ""
                        val genChar = when (team.gender?.lowercase()) {
                            "boys" -> "B"
                            "girls" -> "G"
                            else -> "C"
                        }
                        
                        // Extract only the numeric part if the name is a full ID (e.g., "12UB-03" -> "03")
                        val rawName = team.name ?: "00"
                        val cleanNum = rawName.split("-").last()
                        val formattedNum = if (cleanNum.length == 1) "0$cleanNum" else cleanNum
                        
                        "$div$genChar-$formattedNum [Cloud]"
                    }
                    val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, teamNames)
                    binding.autoCompleteCloudTeams.setAdapter(adapter)
                    
                    binding.autoCompleteCloudTeams.setOnItemClickListener { _, _, position, _ ->
                        selectedTeam = teams[position]
                        
                        val divMatch = selectedTeam?.divisionName ?: ""
                        val genMatch = selectedTeam?.gender ?: "Boys"
                        
                        // FIX: Extract only the number part (e.g., "03") from the full ID (e.g., "12UB-03")
                        val fullName = selectedTeam?.name ?: ""
                        val numMatch = fullName.split("-").last()
                        
                        binding.autoCompleteBuilderDiv.setText(divMatch, false)
                        binding.autoCompleteBuilderGen.setText(genMatch, false)
                        binding.editBuilderNum.setText(numMatch)
                        updateGeneratedID()
                    }

                    // Load Sub-Divisions from Organization settings
                    val subDivs = repository.getDivisions(orgId, liveSeason?.id).mapNotNull { it.subDivision }.distinct()
                    if (subDivs.isNotEmpty()) {
                        val subAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, subDivs)
                        binding.autoCompleteBuilderDiv.setAdapter(subAdapter) // Or use a dedicated sub-div field if you have one
                    }
                } else {
                    binding.layoutCloudSync.visibility = View.GONE
                }
                
                setupIDBuilder()
            } catch (e: Exception) {
                Toast.makeText(context, "Error syncing cloud teams: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupIDBuilder() {
        // Divisions (Age Groups)
        val divisions = listOf("8U", "10U", "12U", "14U", "16U", "19U")
        val divAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, divisions)
        binding.autoCompleteBuilderDiv.setAdapter(divAdapter)

        // Genders
        val genders = listOf("Boys", "Girls", "Coed")
        val genAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, genders)
        binding.autoCompleteBuilderGen.setAdapter(genAdapter)

        // Listeners to auto-generate the Team ID string
        val textWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { updateGeneratedID() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }

        binding.autoCompleteBuilderDiv.setOnItemClickListener { _, _, _, _ -> updateGeneratedID() }
        binding.autoCompleteBuilderGen.setOnItemClickListener { _, _, _, _ -> updateGeneratedID() }
        binding.editBuilderNum.addTextChangedListener(textWatcher)
    }

    private fun updateGeneratedID() {
        val div = binding.autoCompleteBuilderDiv.text.toString()
        val gen = binding.autoCompleteBuilderGen.text.toString()
        val num = binding.editBuilderNum.text.toString()

        if (div.isNotEmpty() && gen.isNotEmpty() && num.isNotEmpty()) {
            val genChar = gen.first().uppercase()
            val formattedNum = if (num.length == 1) "0$num" else num
            val generatedId = "$div$genChar-$formattedNum"
            binding.textGeneratedId.text = "Team ID: $generatedId"
        } else {
            binding.textGeneratedId.text = "Team ID: ----"
        }
    }

    private fun saveTeamAssignment() {
        val div = binding.autoCompleteBuilderDiv.text.toString()
        val gen = binding.autoCompleteBuilderGen.text.toString()
        val num = binding.editBuilderNum.text.toString()
        val coachEmail = binding.editCoachEmail.text.toString().trim()

        if (div.isEmpty() || gen.isEmpty() || num.isEmpty() || coachEmail.isEmpty()) {
            Toast.makeText(context, "Please fill in all required fields", Toast.LENGTH_SHORT).show()
            return
        }

        val genChar = gen.first().uppercase()
        val formattedNum = if (num.length == 1) "0$num" else num
        val generatedName = "$div$genChar-$formattedNum"

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val liveSeason = repository.getLiveSeason(organizationId)
                val isLiveSeason = selectedTeam?.seasonId == liveSeason?.id || (selectedTeam == null && liveSeason != null)

                if (!isLiveSeason && coachEmail.isNotEmpty()) {
                    Toast.makeText(context, "Coaches can only be assigned to LIVE seasons. Adding team as draft.", Toast.LENGTH_LONG).show()
                }

                // Form the team object
                val teamToSave = Team(
                    id = selectedTeam?.id,
                    name = generatedName,
                    divisionName = div,
                    gender = gen,
                    organizationId = organizationId,
                    seasonId = selectedTeam?.seasonId ?: liveSeason?.id,
                    coachEmail = if (isLiveSeason) coachEmail else null,
                    playCriteria = null // Remove per-team override to favor Org rule
                )
                
                val savedTeamId = repository.saveTeam(teamToSave)
                
                if (isLiveSeason && coachEmail.isNotEmpty()) {
                    // Make sure this is fully completed before moving back
                    preCreateCoachAccount(coachEmail, savedTeamId)
                    Toast.makeText(context, "Team $generatedName assigned to $coachEmail", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Team $generatedName added to season draft", Toast.LENGTH_LONG).show()
                }
                
                findNavController().popBackStack()
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun preCreateCoachAccount(email: String, teamId: String) {
        val trimmedEmail = email.trim()
        val options = com.google.firebase.FirebaseOptions.Builder()
            .setApiKey("AIzaSyCuE8-me0wGJ7JuqElyYcto2W-nkWnyxtE")
            .setApplicationId("1:623687909625:android:f3f722e44c809099cea228")
            .setProjectId("referee-schedule-87697")
            .build()
        
        try {
            val context = context ?: return
            val existingApp = FirebaseApp.getApps(context)
                .find { it.name == "secondary" }
            
            val secondaryApp = existingApp ?: FirebaseApp.initializeApp(context, options, "secondary")
            val secondaryAuth = FirebaseAuth.getInstance(secondaryApp)
            
            try {
                val task = secondaryAuth.createUserWithEmailAndPassword(trimmedEmail, "ayso479").await()
                val uid = task.user?.uid ?: ""
                val coachProfile = UserProfile(
                    uid = uid,
                    email = trimmedEmail,
                    name = "Coach",
                    role = mapOf("Teams" to "Coach"),
                    organizationId = organizationId,
                    teamId = teamId
                )
                repository.saveUserProfile(coachProfile)
            } catch (e: Exception) {
                // If user exists or creation fails, try to update existing profile
                val users = repository.db.collection("users")
                    .whereEqualTo("email", trimmedEmail)
                    .get()
                    .await()
                
                users.documents.firstOrNull()?.let { doc ->
                    // Robustly parse the existing profile to avoid crashes on type mismatches
                    val data = doc.data
                    val rawRole = data?.get("role")
                    val currentRoles = when (rawRole) {
                        is Map<*, *> -> rawRole.entries.associate { it.key.toString() to it.value.toString() }.toMutableMap()
                        is String -> mutableMapOf("Teams" to rawRole)
                        else -> mutableMapOf()
                    }
                    currentRoles["Teams"] = "Coach"
                    
                    repository.db.collection("users").document(doc.id)
                        .update(
                            "teamId", teamId, 
                            "role", currentRoles,
                            "organizationId", organizationId
                        )
                        .await()
                }
            }
            secondaryApp.delete() // Critical: Remove secondary app to prevent auth state collision
        } catch (e: Exception) {
            android.util.Log.e("AddTeam", "Secondary Auth Error: ${e.message}")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
