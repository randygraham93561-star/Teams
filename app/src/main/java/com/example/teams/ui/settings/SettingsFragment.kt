package com.example.teams.ui.settings

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
import com.example.teams.R
import com.example.teams.data.FirestoreRepository
import com.example.teams.databinding.FragmentSettingsBinding
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val auth = FirebaseAuth.getInstance()
    private val repository = FirestoreRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        
        loadUserProfile()
        loadOrganizations()
        
        binding.btnLogout.setOnClickListener {
            logout()
        }

        binding.btnEditProfile.setOnClickListener {
            findNavController().navigate(R.id.nav_profile)
        }

        binding.btnLinkAdditionalPlayer.setOnClickListener {
            findNavController().navigate(R.id.nav_welcome_setup)
        }

        binding.btnAddPlayer.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val uid = auth.currentUser?.uid ?: return@launch
                val profile = repository.getUserProfile(uid)
                val teamId = profile?.teamId
                if (!teamId.isNullOrEmpty()) {
                    val bundle = Bundle().apply { putString("teamId", teamId) }
                    findNavController().navigate(R.id.nav_player_details, bundle)
                } else {
                    Toast.makeText(context, "You are not assigned to a team.", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        return binding.root
    }

    private fun loadOrganizations() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val orgs = repository.getAllOrganizations()
                val orgNames = orgs.map { it.name ?: "Unknown" }
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, orgNames)
                binding.autoCompleteOrg.setAdapter(adapter)
                
                binding.autoCompleteOrg.setOnItemClickListener { _, _, position, _ ->
                    val selectedOrg = orgs[position]
                    updateUserOrganization(selectedOrg.id ?: "")
                }
            } catch (e: Exception) {
                Log.e("Settings", "Error loading orgs", e)
            }
        }
    }

    private fun updateUserOrganization(orgId: String) {
        val uid = auth.currentUser?.uid ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                repository.updateUserOrganization(uid, orgId)
                Toast.makeText(context, "Organization updated!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadUserProfile() {
        val uid = auth.currentUser?.uid ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val profile = repository.getUserProfile(uid)
            profile?.let { prof ->
                binding.textProfileName.text = prof.name ?: "Anonymous"
                binding.textProfileEmail.text = prof.email ?: "No Email"
                
                // Set Current Organization in dropdown
                if (!prof.organizationId.isNullOrBlank()) {
                    val org = repository.getOrganizationById(prof.organizationId)
                    binding.autoCompleteOrg.setText(org?.name ?: "", false)
                }

                // Show Add Player button only for coaches/admins
                val isCoach = prof.isCoach() || prof.isCoachAdmin()
                binding.btnAddPlayer.visibility = if (isCoach) View.VISIBLE else View.GONE
                
                // Show Link button for parents (and coaches who are also parents)
                binding.btnLinkAdditionalPlayer.visibility = if (prof.isParent()) View.VISIBLE else View.GONE
                
                if (prof.isCoach()) {
                    binding.textCoachLabel.visibility = View.VISIBLE
                    binding.layoutCustomTeamName.visibility = View.VISIBLE
                    
                    val teamId = prof.teamId
                    if (!teamId.isNullOrBlank()) {
                        val team = repository.getTeamById(teamId)
                        binding.editCustomTeamName.setText(team?.customName ?: "")
                        
                        binding.layoutCustomTeamName.setEndIconOnClickListener {
                            val newName = binding.editCustomTeamName.text.toString()
                            updateTeamName(teamId, newName)
                        }
                    }
                }
            }
        }
    }

    private fun updateTeamName(teamId: String, newName: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                repository.updateTeamCustomName(teamId, newName)
                Toast.makeText(context, "Team name updated!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun logout() {
        auth.signOut()
        findNavController().navigate(R.id.nav_login)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
