package com.example.teams.ui.auth

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
import com.example.teams.databinding.FragmentLoginBinding
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val auth = FirebaseAuth.getInstance()
    private val repository = FirestoreRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)

        try {
            // Debug: Show current project ID to confirm sync
            val projectId = FirebaseApp.getInstance().options.projectId
            val connectMsg = "Connected to: $projectId"
            context?.let { Toast.makeText(it, connectMsg, Toast.LENGTH_LONG).show() }
        } catch (e: Exception) {
            Log.e("LoginFragment", "Firebase not initialized yet", e)
        }

        binding.btnLogin.setOnClickListener {
            login()
        }

        binding.btnGoToSignup.setOnClickListener {
            findNavController().navigate(R.id.nav_coach_auth)
        }

        return binding.root
    }

    private fun login() {
        val email = binding.editEmail.text.toString().trim()
        val password = binding.editPassword.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            context?.let { Toast.makeText(it, "Please enter credentials", Toast.LENGTH_SHORT).show() }
            return
        }

        context?.let { Toast.makeText(it, "Logging in...", Toast.LENGTH_SHORT).show() }

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = task.result?.user
                    if (password == "ayso479") {
                        if (isAdded) findNavController().navigate(R.id.nav_change_password)
                    } else if (user != null) {
                        checkUserRole(user.uid)
                    }
                } else {
                    context?.let {
                        val failMsg = "Login failed: ${task.exception?.message}"
                        Toast.makeText(it, failMsg, Toast.LENGTH_LONG).show() 
                    }
                }
            }
    }

    private fun checkUserRole(uid: String) {
        if (uid.isEmpty()) return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val currentEmail = auth.currentUser?.email ?: ""
                if (currentEmail.isEmpty()) return@launch

                var profile = repository.getUserProfile(uid)
                
                val isCoachAdminEmail = currentEmail.equals("randalltruck93@gmail.com", ignoreCase = true)
                val isSystemAdminEmail = currentEmail.equals("randygraham93561@gmail.com", ignoreCase = true)

                // Ensure specific emails have the correct admin access
                if (isCoachAdminEmail || isSystemAdminEmail) {
                    val roleString = if (isSystemAdminEmail) "SystemAdmin" else "CoachAdmin"
                    
                    val hasCorrectAccess = if (isSystemAdminEmail) profile?.isSystemAdmin() == true else profile?.isCoachAdmin() == true
                    
                    if (profile == null || !hasCorrectAccess) {
                        context?.let { Toast.makeText(it, "Updating Admin Access...", Toast.LENGTH_SHORT).show() }
                        
                        val baseProfile = profile ?: UserProfile(uid = uid, email = currentEmail)
                        val currentRoles = baseProfile.role?.toMutableMap() ?: mutableMapOf()
                        currentRoles["Teams"] = roleString
                        
                        val updatedProfile = baseProfile.copy(
                            name = if (isSystemAdminEmail) "System Admin" else "Coach Admin",
                            role = currentRoles
                        )
                        repository.saveUserProfile(updatedProfile)
                        profile = updatedProfile
                    }
                }

                if (profile != null) {
                    if (profile.isSystemAdmin()) {
                        // System Admin goes straight to Org Management
                        if (isAdded && _binding != null) findNavController().navigate(R.id.nav_manage_organizations)
                    } else if (profile.isCoachAdmin()) {
                        if (profile.organizationId.isNullOrEmpty()) {
                            if (isAdded && _binding != null) findNavController().navigate(R.id.nav_organization_selection)
                        } else {
                            if (isAdded && _binding != null) findNavController().navigate(R.id.nav_admin_dashboard)
                        }
                    } else if (profile.isCoach()) {
                        if (isAdded && _binding != null) findNavController().navigate(R.id.nav_coach_team)
                    } else if (profile.isParent()) {
                        if (profile.organizationId.isNullOrEmpty()) {
                            // Incomplete Parent Setup
                            if (isAdded && _binding != null) findNavController().navigate(R.id.nav_welcome_setup)
                        } else {
                            if (isAdded && _binding != null) findNavController().navigate(R.id.nav_coach_team)
                        }
                    } else {
                        context?.let { Toast.makeText(it, "No Teams access assigned.", Toast.LENGTH_LONG).show() }
                    }
                } else {
                    context?.let { Toast.makeText(it, "User profile not found. Please sign up.", Toast.LENGTH_LONG).show() }
                }
            } catch (e: Exception) {
                context?.let {
                    val errorMsg = "Error: ${e.message}"
                    Toast.makeText(it, errorMsg, Toast.LENGTH_SHORT).show() 
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
