package com.example.teams.ui.coach

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
import com.example.teams.databinding.FragmentParentJoinTeamBinding
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class ParentJoinTeamFragment : Fragment() {

    private var _binding: FragmentParentJoinTeamBinding? = null
    private val binding get() = _binding!!
    private val auth = FirebaseAuth.getInstance()
    private val repository = FirestoreRepository()
    private var selectedPlayer: Player? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentParentJoinTeamBinding.inflate(inflater, container, false)
        
        val teamId = arguments?.getString("teamId") ?: ""
        loadTeamAndPlayers(teamId)
        
        if (auth.currentUser != null) {
            binding.layoutParentName.visibility = View.GONE
            binding.layoutParentEmail.visibility = View.GONE
            binding.layoutParentPassword.visibility = View.GONE
            binding.btnJoinTeam.text = "Link to this Team"
        }
        
        binding.btnJoinTeam.setOnClickListener {
            if (selectedPlayer == null) {
                Toast.makeText(context, "Please select your player", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (auth.currentUser != null) {
                linkExistingParent(teamId)
            } else {
                createNewAccountAndJoin(teamId)
            }
        }
        
        return binding.root
    }

    private fun loadTeamAndPlayers(teamId: String) {
        if (teamId.isEmpty()) return
        
        viewLifecycleOwner.lifecycleScope.launch {
            val team = repository.getTeamById(teamId)
            team?.let {
                val teamNameText = "Joining Team: ${it.name ?: "Unknown Team"}"
                binding.textTeamNameDisplay.text = teamNameText
            }

            val players = repository.getRoster(teamId)
            if (players.isEmpty()) {
                Toast.makeText(context, "No roster found for this team. Ask your coach to add players.", Toast.LENGTH_LONG).show()
            }
            val playerNames = players.map { 
                val first = it.firstName ?: ""
                val last = it.lastName ?: ""
                val num = it.jerseyNumber ?: "00"
                "$first $last (#$num)"
            }
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, playerNames)
            binding.autoCompletePlayerSelection.setAdapter(adapter)
            binding.autoCompletePlayerSelection.setOnItemClickListener { _, _, position, _ ->
                selectedPlayer = players[position]
            }
        }
    }

    private fun linkExistingParent(teamId: String) {
        val uid = auth.currentUser?.uid ?: return
        saveParentAndPlayerLink(uid, teamId)
    }

    private fun createNewAccountAndJoin(teamId: String) {
        val email = binding.editParentEmail.text.toString()
        val password = binding.editParentPassword.text.toString()
        
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(context, "Please fill account credentials", Toast.LENGTH_SHORT).show()
            return
        }

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val uid = task.result?.user?.uid ?: ""
                    saveParentAndPlayerLink(uid, teamId)
                } else {
                    Toast.makeText(context, "Sign up failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun saveParentAndPlayerLink(uid: String, teamId: String) {
        val player = selectedPlayer ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val team = repository.getTeamById(teamId)
                val orgId = team?.organizationId ?: ""
                
                // 1. Link Parent and Player
                player.id?.let { pid ->
                    repository.linkPlayerToParent(uid, pid)
                }
                
                // 2. Update parent profile base info if new
                val existingProfile = repository.getUserProfile(uid)
                if (existingProfile == null) {
                    val profile = UserProfile(
                        uid = uid,
                        email = auth.currentUser?.email ?: "",
                        name = binding.editParentName.text.toString(),
                        role = mapOf("Teams" to "Parent"),
                        organizationId = orgId
                    )
                    repository.saveUserProfile(profile)
                }

                Toast.makeText(context, "Successfully linked to player!", Toast.LENGTH_SHORT).show()
                findNavController().navigate(R.id.nav_coach_team)
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
