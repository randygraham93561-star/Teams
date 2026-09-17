package com.example.teams.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.teams.data.FirestoreRepository
import com.example.teams.data.UserProfile
import com.example.teams.databinding.FragmentProfileBinding
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private val auth = FirebaseAuth.getInstance()
    private val repository = FirestoreRepository()
    private var currentUserProfile: UserProfile? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        
        loadProfile()
        
        binding.btnSaveProfile.setOnClickListener {
            saveProfile()
        }
        
        return binding.root
    }

    private fun loadProfile() {
        val uid = auth.currentUser?.uid ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            binding.progressProfile.visibility = View.VISIBLE
            binding.btnSaveProfile.isEnabled = false
            
            val profile = repository.getUserProfile(uid)
            currentUserProfile = profile
            
            profile?.let {
                binding.textEmailDisplay.text = it.email
                binding.editName.setText(it.name)
                binding.editPhone.setText(it.phoneNumber ?: "")
                binding.editBio.setText(it.bio ?: "")
                
                val displayRole = when {
                    it.isSystemAdmin() -> "System Admin"
                    it.isCoachAdmin() -> "Coach Admin"
                    it.isCoach() -> "Coach"
                    it.isParent() -> "Parent"
                    else -> it.role?.get("Teams") ?: it.role?.values?.firstOrNull() ?: "User"
                }
                val roleText = "Role: $displayRole"
                binding.textRole.text = roleText
                
                // Load Organization Name
                if (!it.organizationId.isNullOrEmpty()) {
                    val org = repository.getOrganizationById(it.organizationId)
                    val orgNameText = "Organization: ${org?.name ?: "Unknown"}"
                    binding.textOrganization.text = orgNameText
                } else {
                    binding.textOrganization.text = "Organization: None"
                }
                
                // Load Team Name
                if (!it.teamId.isNullOrEmpty()) {
                    val team = repository.getTeamById(it.teamId)
                    val teamNameText = "Team: ${team?.customName ?: team?.name ?: "Unknown"}"
                    binding.textTeam.text = teamNameText
                } else {
                    binding.textTeam.text = "Team: Not Assigned"
                }
            }
            
            binding.progressProfile.visibility = View.GONE
            binding.btnSaveProfile.isEnabled = true
        }
    }

    private fun saveProfile() {
        val profile = currentUserProfile ?: return
        val newName = binding.editName.text.toString().trim()
        val newPhone = binding.editPhone.text.toString().trim()
        val newBio = binding.editBio.text.toString().trim()
        
        if (newName.isEmpty()) {
            binding.layoutName.error = "Name cannot be empty"
            return
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            binding.progressProfile.visibility = View.VISIBLE
            binding.btnSaveProfile.isEnabled = false
            
            val updatedProfile = profile.copy(
                name = newName,
                phoneNumber = newPhone,
                bio = newBio
            )
            try {
                repository.saveUserProfile(updatedProfile)
                currentUserProfile = updatedProfile
                Toast.makeText(context, "Profile updated successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressProfile.visibility = View.GONE
                binding.btnSaveProfile.isEnabled = true
                binding.layoutName.error = null
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
