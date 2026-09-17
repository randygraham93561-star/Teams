package com.example.teams.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.teams.R
import com.example.teams.data.FirestoreRepository
import com.example.teams.data.PlayCriteria
import com.example.teams.data.Team
import com.example.teams.databinding.FragmentAdminDashboardBinding
import com.example.teams.databinding.ItemTransformBinding
import kotlinx.coroutines.launch

class AdminDashboardFragment : Fragment() {

    private var _binding: FragmentAdminDashboardBinding? = null
    private val binding get() = _binding!!
    private val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
    private val repository = FirestoreRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val viewModel = try {
            ViewModelProvider(requireActivity()).get(AdminViewModel::class.java)
        } catch (e: Exception) {
            ViewModelProvider(this).get(AdminViewModel::class.java)
        }
        _binding = FragmentAdminDashboardBinding.inflate(inflater, container, false)
        
        val adapter = TeamAdapter()
        binding.recyclerviewAdminDashboard.adapter = adapter
        
        binding.fabAddTeam.setOnClickListener {
            if (isAdded) findNavController().navigate(R.id.nav_add_team)
        }

        binding.btnOrgSettings.setOnClickListener {
            if (isAdded) findNavController().navigate(R.id.nav_organization_settings)
        }

        // Fetch user profile and start sync for their specific organization or global access
        viewLifecycleOwner.lifecycleScope.launch {
            auth.currentUser?.uid?.let { uid ->
                repository.getUserProfile(uid)?.let { profile ->
                    // Unified role check matching system admin standards
                    val isSystemAdmin = profile.isSystemAdmin()
                    
                    if (isSystemAdmin) {
                        // System Admin should NOT be on this dashboard. Redirect them.
                        if (isAdded && _binding != null) findNavController().navigate(R.id.nav_manage_organizations)
                    } else if (profile.organizationId?.isNotEmpty() == true) {
                        if (_binding != null) {
                            binding.textDashboardTitle.text = "Coach Admin Dashboard"
                            binding.btnOrgSettings.visibility = View.VISIBLE
                            binding.fabAddTeam.show()
                        }
                        viewModel.setAccessLevel(profile.organizationId, false)
                    } else {
                        if (_binding != null) {
                            binding.textDashboardTitle.text = "Unauthorized Access"
                        }
                        if (isAdded) {
                            val msg = "You do not have permission to view this dashboard."
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }

        viewModel.teams.observe(viewLifecycleOwner) { teams ->
            adapter.submitList(teams)
        }

        viewModel.organizations.observe(viewLifecycleOwner) { orgs ->
            orgs.firstOrNull()?.playCriteria?.let {
                adapter.setGlobalCriteria(it)
            }
        }

        viewModel.isSyncEnabled.observe(viewLifecycleOwner) { isEnabled ->
            // Check BOTH organization setting and user's subscription tier
            if (isEnabled) {
                binding.cardUpgradeRequired.visibility = View.GONE
                binding.recyclerviewAdminDashboard.visibility = View.VISIBLE
                binding.fabAddTeam.show()
            } else {
                binding.cardUpgradeRequired.visibility = View.VISIBLE
                binding.recyclerviewAdminDashboard.visibility = View.GONE
                binding.fabAddTeam.hide()
            }
        }

        binding.btnLogoutBlocked.setOnClickListener {
            com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
            if (isAdded) findNavController().navigate(R.id.nav_login)
        }
        
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    class TeamAdapter : ListAdapter<Team, TeamViewHolder>(TeamDiffCallback()) {
        private var globalCriteria: PlayCriteria = PlayCriteria.NONE

        fun setGlobalCriteria(criteria: PlayCriteria) {
            globalCriteria = criteria
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TeamViewHolder {
            val ctx = parent.context
            val binding = ItemTransformBinding.inflate(LayoutInflater.from(ctx), parent, false)
            return TeamViewHolder(binding)
        }

        override fun onBindViewHolder(holder: TeamViewHolder, position: Int) {
            val team = getItem(position)
            
            // Use global organization criteria instead of per-team criteria
            val criteriaText = when(globalCriteria) {
                com.example.teams.data.PlayCriteria.HALF_GAME -> "1/2 Game"
                com.example.teams.data.PlayCriteria.THREE_QUARTERS -> "3/4 Game"
                else -> "No Req"
            }
            val subText = if (!team.subDivision.isNullOrEmpty()) " [${team.subDivision}]" else ""
            val name = team.name ?: "Unknown Team"
            val points = team.totalPoints ?: 0
            val text = "$name$subText\nPoints: $points/2 (this week cap)\nRule: $criteriaText"
            holder.binding.textViewItemTransform.text = text
        }
    }

    class TeamViewHolder(val binding: ItemTransformBinding) : RecyclerView.ViewHolder(binding.root)

    class TeamDiffCallback : DiffUtil.ItemCallback<Team>() {
        override fun areItemsTheSame(oldItem: Team, newItem: Team): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Team, newItem: Team): Boolean = oldItem == newItem
    }
}
