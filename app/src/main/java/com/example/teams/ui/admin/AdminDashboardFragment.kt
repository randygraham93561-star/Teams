package com.example.teams.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.teams.data.Team
import com.example.teams.databinding.FragmentAdminDashboardBinding
import com.example.teams.databinding.ItemTransformBinding

class AdminDashboardFragment : Fragment() {

    private var _binding: FragmentAdminDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val adminViewModel = ViewModelProvider(this).get(AdminViewModel::class.java)
        _binding = FragmentAdminDashboardBinding.inflate(inflater, container, false)
        
        val adapter = TeamAdapter()
        binding.recyclerviewAdminDashboard.adapter = adapter
        
        // Placeholder for team data
        val placeholderTeams = listOf(
            Team(id = "T101", name = "Lions", coachEmail = "coach1@example.com"),
            Team(id = "T102", name = "Tigers", coachEmail = "coach2@example.com")
        )
        adapter.submitList(placeholderTeams)
        
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    class TeamAdapter : ListAdapter<Team, TeamViewHolder>(TeamDiffCallback()) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TeamViewHolder {
            val binding = ItemTransformBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return TeamViewHolder(binding)
        }

        override fun onBindViewHolder(holder: TeamViewHolder, position: Int) {
            val team = getItem(position)
            holder.binding.textViewItemTransform.text = "${team.name} (${team.id})"
        }
    }

    class TeamViewHolder(val binding: ItemTransformBinding) : RecyclerView.ViewHolder(binding.root)

    class TeamDiffCallback : DiffUtil.ItemCallback<Team>() {
        override fun areItemsTheSame(oldItem: Team, newItem: Team): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Team, newItem: Team): Boolean = oldItem == newItem
    }
}
