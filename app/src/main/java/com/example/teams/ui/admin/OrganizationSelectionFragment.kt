package com.example.teams.ui.admin

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
import com.example.teams.data.FirestoreRepository
import com.example.teams.data.Organization
import com.example.teams.databinding.FragmentOrganizationSelectionBinding
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class OrganizationSelectionFragment : Fragment() {

    private var _binding: FragmentOrganizationSelectionBinding? = null
    private val binding get() = _binding!!
    private val repository = FirestoreRepository()
    private val auth = FirebaseAuth.getInstance()
    private var selectedOrg: Organization? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOrganizationSelectionBinding.inflate(inflater, container, false)
        
        loadOrganizations()
        
        binding.btnConfirmOrg.setOnClickListener {
            confirmSelection()
        }
        
        return binding.root
    }

    private fun loadOrganizations() {
        viewLifecycleOwner.lifecycleScope.launch {
            val orgs = repository.getAllOrganizations()
            if (orgs.isEmpty()) {
                Toast.makeText(context, "No organizations found in database.", Toast.LENGTH_LONG).show()
                return@launch
            }
            
            val orgNames = orgs.map { it.name }
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, orgNames)
            binding.autoCompleteOrg.setAdapter(adapter)
            
            binding.autoCompleteOrg.setOnItemClickListener { _, _, position, _ ->
                selectedOrg = orgs[position]
            }
        }
    }

    private fun confirmSelection() {
        val org = selectedOrg
        val uid = auth.currentUser?.uid
        
        if (org == null || uid == null) {
            Toast.makeText(context, "Please select an organization", Toast.LENGTH_SHORT).show()
            return
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                repository.updateUserOrganization(uid, org.id ?: "")
                
                // If it's Base level, inform them immediately
                if (org.isTeamAppSyncEnabled != true) {
                    android.app.AlertDialog.Builder(requireContext())
                        .setTitle("Upgrade Required")
                        .setMessage("A subscription upgrade is required on the Referee Scheduler to use the Teams App for this organization. \n\nPlease ask your Commissioner or Referee Admin to upgrade.")
                        .setPositiveButton("OK") { _, _ ->
                            findNavController().navigate(R.id.nav_admin_dashboard)
                        }
                        .show()
                } else {
                    findNavController().navigate(R.id.nav_admin_dashboard)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error saving: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
