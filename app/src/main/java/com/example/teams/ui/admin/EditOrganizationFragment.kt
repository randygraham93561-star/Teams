package com.example.teams.ui.admin

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.teams.R
import com.example.teams.data.*
import com.example.teams.databinding.FragmentEditOrganizationBinding
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class EditOrganizationFragment : Fragment() {

    private var _binding: FragmentEditOrganizationBinding? = null
    private val binding get() = _binding!!
    private val repository = FirestoreRepository()
    private var orgId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditOrganizationBinding.inflate(inflater, container, false)
        
        orgId = arguments?.getString("orgId")
        if (orgId != null) {
            loadOrganizationData(orgId!!)
        }

        binding.btnSaveOrg.setOnClickListener {
            saveOrganization()
        }
        
        return binding.root
    }

    private fun loadOrganizationData(id: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val doc = repository.db.collection("organizations").document(id).get().await()
                val org = doc.toObject(Organization::class.java)?.copy(id = doc.id)
                org?.let {
                    binding.editOrgName.setText(it.name ?: "")
                    binding.editCoachAdminEmail.setText(it.contactEmail ?: "")
                    when (it.tier) {
                        "Free" -> binding.radioFree.isChecked = true
                        "Base" -> binding.radioBase.isChecked = true
                        "Standard" -> binding.radioStandard.isChecked = true
                        "Pro" -> binding.radioPro.isChecked = true
                        else -> binding.radioFree.isChecked = true
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error loading: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveOrganization() {
        val name = binding.editOrgName.text.toString()
        val email = binding.editCoachAdminEmail.text.toString()
        
        if (name.isEmpty() || email.isEmpty()) {
            Toast.makeText(context, "Name and Email are required", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedTier = when (binding.radioGroupTier.checkedRadioButtonId) {
            R.id.radio_standard -> "Standard"
            R.id.radio_pro -> "Pro"
            R.id.radio_base -> "Base"
            else -> "Free"
        }
        
        val syncEnabled = selectedTier == "Standard" || selectedTier == "Pro"

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val org = Organization(
                    id = orgId ?: "",
                    name = name,
                    contactEmail = email,
                    tier = selectedTier,
                    isTeamAppSyncEnabled = syncEnabled
                )
                val savedId = repository.saveOrganization(org)
                
                // Assign/Pre-create the Coach Admin (Safely)
                preCreateCoachAdminAccount(email, savedId)

                Toast.makeText(context, "Organization saved successfully", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            } catch (e: Exception) {
                Log.e("EditOrg", "Save Error", e)
                Toast.makeText(context, "Error saving: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun preCreateCoachAdminAccount(email: String, orgId: String) {
        val trimmedEmail = email.trim()
        val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email
        
        // Prevent accidental self-demotion
        if (trimmedEmail.equals(currentUserEmail, ignoreCase = true)) {
            Log.d("EditOrg", "Skipping CoachAdmin assignment for self to prevent role overwrite")
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            val currentProfile = repository.getUserProfile(uid)
            if (currentProfile?.organizationId != orgId) {
                repository.saveUserProfile(currentProfile?.copy(organizationId = orgId) ?: UserProfile(uid = uid, email = trimmedEmail, organizationId = orgId))
            }
            return
        }

        val options = FirebaseOptions.Builder()
            .setApiKey("AIzaSyCuE8-me0wGJ7JuqElyYcto2W-nkWnyxtE")
            .setApplicationId("1:623687909625:android:f3f722e44c809099cea228")
            .setProjectId("referee-schedule-87697")
            .build()
        
        try {
            val existingApp = FirebaseApp.getApps(requireContext())
                .find { it.name == "secondary_mgmt" }
            
            val secondaryApp = existingApp ?: FirebaseApp.initializeApp(requireContext(), options, "secondary_mgmt")
            val secondaryAuth = FirebaseAuth.getInstance(secondaryApp)
            
            try {
                val result = secondaryAuth.createUserWithEmailAndPassword(trimmedEmail, "ayso479").await()
                val uid = result.user?.uid ?: ""
                repository.saveUserProfile(
                    UserProfile(
                        uid = uid,
                        email = trimmedEmail,
                        name = "Coach Admin",
                        role = mapOf("Teams" to "CoachAdmin"),
                        organizationId = orgId,
                        adminOrgIds = listOf(orgId)
                    )
                )
            } catch (authError: Exception) {
                Log.d("EditOrg", "User already exists or Auth error, updating profile")
                val users = repository.db.collection("users")
                    .whereEqualTo("email", trimmedEmail)
                    .get()
                    .await()
                
                users.documents.firstOrNull()?.let { doc ->
                    val data = doc.data
                    val rawRole = data?.get("role")
                    val currentRoles = when (rawRole) {
                        is Map<*, *> -> rawRole.entries.associate { it.key.toString() to it.value.toString() }.toMutableMap()
                        is String -> mutableMapOf("Teams" to rawRole)
                        else -> mutableMapOf()
                    }
                    currentRoles["Teams"] = "CoachAdmin"
                    
                    val updated = (doc.toObject(UserProfile::class.java) ?: UserProfile(uid = doc.id, email = trimmedEmail)).copy(
                        role = currentRoles,
                        organizationId = orgId,
                        adminOrgIds = (data?.get("adminOrgIds") as? List<String> ?: emptyList()) + orgId
                    )
                    repository.saveUserProfile(updated)
                }
            }
            secondaryApp.delete()
        } catch (e: Exception) {
            Log.e("EditOrg", "Secondary Auth Initialization Error: ${e.message}")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
