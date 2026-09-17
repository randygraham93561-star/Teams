package com.example.teams.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.teams.R
import com.example.teams.data.FirestoreRepository
import com.example.teams.data.UserProfile
import com.example.teams.databinding.FragmentCoachAuthBinding
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class CoachAuthFragment : Fragment() {

    private var _binding: FragmentCoachAuthBinding? = null
    private val binding get() = _binding!!
    private val auth = FirebaseAuth.getInstance()
    private val repository = FirestoreRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCoachAuthBinding.inflate(inflater, container, false)
        
        binding.btnSignUp.setOnClickListener {
            signUp()
        }
        
        return binding.root
    }

    private fun signUp() {
        val email = binding.editEmail.text.toString()
        val password = binding.editPassword.text.toString()
        
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(context, "Please enter email and password", Toast.LENGTH_SHORT).show()
            return
        }
        
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    user?.let {
                        saveProfile(it.uid, email)
                    }
                } else {
                    val errorMsg = task.exception?.message ?: "Unknown error"
                    val finalMsg = "Sign up failed: $errorMsg"
                    Toast.makeText(context, finalMsg, Toast.LENGTH_LONG).show()
                    android.util.Log.e("Signup", "Error: $errorMsg", task.exception)
                }
            }
    }

    private fun saveProfile(uid: String, email: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val assignedTeam = repository.getTeamForCoach(email)
            
            val roleString = when {
                email.equals("randalltruck93@gmail.com", ignoreCase = true) -> "CoachAdmin"
                assignedTeam != null -> "Coach"
                else -> "Parent"
            }
            
            val roleTeamId = if (roleString == "Coach") assignedTeam?.id else null
            val profile = UserProfile(
                uid = uid,
                email = email,
                name = "User", // Placeholder name
                role = mapOf("Teams" to roleString),
                teamId = roleTeamId,
                organizationId = assignedTeam?.organizationId ?: ""
            )
            
            repository.saveUserProfile(profile)
            val successMsg = "Account created as $roleString!"
            Toast.makeText(context, successMsg, Toast.LENGTH_SHORT).show()
            
            if (roleString == "CoachAdmin") {
                findNavController().navigate(R.id.nav_admin_dashboard)
            } else if (roleString == "Coach") {
                findNavController().navigate(R.id.nav_coach_team)
            } else {
                // Parents go to Welcome Setup
                findNavController().navigate(R.id.nav_welcome_setup)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
