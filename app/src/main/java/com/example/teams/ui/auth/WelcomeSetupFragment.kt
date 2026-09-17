package com.example.teams.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.teams.R
import com.example.teams.data.*
import com.example.teams.databinding.FragmentWelcomeSetupBinding
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class WelcomeSetupFragment : Fragment() {

    private var _binding: FragmentWelcomeSetupBinding? = null
    private val binding get() = _binding!!
    private val auth = FirebaseAuth.getInstance()
    private val repository = FirestoreRepository()

    private var organizations: List<Organization> = emptyList()
    private var selectedOrg: Organization? = null
    private var teams: List<Team> = emptyList()
    private var selectedTeam: Team? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWelcomeSetupBinding.inflate(inflater, container, false)
        
        loadOrganizations()
        
        binding.btnCompleteSetup.setOnClickListener {
            completeSetup()
        }
        
        return binding.root
    }

    private fun loadOrganizations() {
        viewLifecycleOwner.lifecycleScope.launch {
            organizations = repository.getAllOrganizations()
            val orgNames = organizations.map { it.name ?: "Unknown" }
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, orgNames)
            binding.autoCompleteOrg.setAdapter(adapter)
            
            binding.autoCompleteOrg.setOnItemClickListener { _, _, position, _ ->
                selectedOrg = organizations[position]
                loadTeams(selectedOrg?.id ?: "")
            }
        }
    }

    private fun loadTeams(orgId: String) {
        if (orgId.isEmpty()) return
        
        viewLifecycleOwner.lifecycleScope.launch {
            binding.progressSetup.visibility = View.VISIBLE
            val liveSeason = repository.getLiveSeason(orgId)
            teams = repository.getAvailableRefereeTeams(orgId, liveSeason?.id)
            
            val teamNames = teams.map { it.name ?: "Unknown" }
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, teamNames)
            binding.autoCompleteTeam.setAdapter(adapter)
            
            binding.autoCompleteTeam.setOnItemClickListener { _, _, position, _ ->
                selectedTeam = teams[position]
                loadPlayers(selectedTeam?.id ?: "")
            }
            binding.progressSetup.visibility = View.GONE
        }
    }

    private var roster: List<Player> = emptyList()
    private var selectedPlayer: Player? = null

    private fun loadPlayers(teamId: String) {
        if (teamId.isEmpty()) return
        
        viewLifecycleOwner.lifecycleScope.launch {
            binding.progressSetup.visibility = View.VISIBLE
            roster = repository.getRoster(teamId)
            
            if (roster.isNotEmpty()) {
                binding.textPlayerLabel.visibility = View.VISIBLE
                binding.layoutPlayerSelection.visibility = View.VISIBLE
                
                val playerNames = roster.map { "${it.firstName} ${it.lastName} (#${it.jerseyNumber ?: "00"})" }
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, playerNames)
                binding.autoCompletePlayer.setAdapter(adapter)
                
                binding.autoCompletePlayer.setOnItemClickListener { _, _, position, _ ->
                    selectedPlayer = roster[position]
                }
            } else {
                Toast.makeText(context, "No players found on this team.", Toast.LENGTH_SHORT).show()
                binding.textPlayerLabel.visibility = View.GONE
                binding.layoutPlayerSelection.visibility = View.GONE
            }
            binding.progressSetup.visibility = View.GONE
        }
    }

    private fun completeSetup() {
        val name = binding.editName.text.toString().trim()
        val phone = binding.editPhone.text.toString().trim()
        val org = selectedOrg
        val team = selectedTeam
        val player = selectedPlayer
        val uid = auth.currentUser?.uid ?: return

        if (name.isEmpty()) {
            binding.layoutName.error = "Name is required"
            return
        }
        if (org == null || team == null) {
            Toast.makeText(context, "Please select an organization and team", Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            binding.progressSetup.visibility = View.VISIBLE
            binding.btnCompleteSetup.isEnabled = false
            
            try {
                val currentProfile = repository.getUserProfile(uid)
                val currentIds = currentProfile?.playerIds ?: emptyList()
                val newIds = if (player?.id != null && !currentIds.contains(player.id)) currentIds + player.id else currentIds
                
                val updatedProfile = (currentProfile ?: UserProfile(uid = uid, email = auth.currentUser?.email)).copy(
                    name = name,
                    phoneNumber = phone,
                    organizationId = org.id,
                    teamId = team.id, // Primary team
                    playerIds = newIds
                )
                repository.saveUserProfile(updatedProfile)
                
                // Also link parent to player record in the player's collection
                player?.id?.let { pid ->
                    repository.linkPlayerToParent(uid, pid)
                }

                Toast.makeText(context, "Setup complete! Welcome, $name", Toast.LENGTH_LONG).show()
                
                // Navigate to Dashboard
                findNavController().navigate(R.id.nav_coach_team)
                
            } catch (e: Exception) {
                Toast.makeText(context, "Error saving setup: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressSetup.visibility = View.GONE
                binding.btnCompleteSetup.isEnabled = true
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
