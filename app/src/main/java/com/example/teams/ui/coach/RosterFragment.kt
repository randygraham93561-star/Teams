package com.example.teams.ui.coach

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.teams.R
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.teams.data.*
import com.example.teams.databinding.FragmentRosterBinding
import com.example.teams.databinding.ItemTransformBinding
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class RosterFragment : Fragment() {

    private var _binding: FragmentRosterBinding? = null
    private val binding get() = _binding!!
    private val auth = FirebaseAuth.getInstance()
    private val repository = FirestoreRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRosterBinding.inflate(inflater, container, false)
        
        val adapter = RosterAdapter()
        binding.recyclerviewRoster.adapter = adapter
        
        binding.fabAddPlayer.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val uid = auth.currentUser?.uid ?: return@launch
                val profile = repository.getUserProfile(uid)
                val tid = profile?.teamId
                if (tid != null) {
                    val bundle = Bundle().apply { putString("teamId", tid) }
                    findNavController().navigate(R.id.nav_player_details, bundle)
                }
            }
        }

        loadRoster(adapter)
        
        return binding.root
    }

    private fun loadRoster(adapter: RosterAdapter) {
        viewLifecycleOwner.lifecycleScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            val profile = repository.getUserProfile(uid) ?: return@launch
            
            val isCoach = profile.isCoach()
            
            if (isCoach) {
                binding.fabAddPlayer.visibility = View.VISIBLE
            }

            val teamId = if (isCoach) {
                profile.teamId
            } else {
                repository.getPlayersForParent(uid).firstOrNull()?.teamId
            }

            teamId?.let { tid ->
                val players = repository.getRoster(tid)
                adapter.submitList(players)
                
                adapter.onItemClick = { player ->
                    val bundle = Bundle().apply { 
                        putString("playerId", player.id) 
                        putString("teamId", tid)
                    }
                    findNavController().navigate(R.id.nav_player_details, bundle)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    class RosterAdapter : ListAdapter<Player, RosterViewHolder>(RosterDiff()) {
        var onItemClick: ((Player) -> Unit)? = null

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RosterViewHolder {
            val binding = ItemTransformBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return RosterViewHolder(binding)
        }
        override fun onBindViewHolder(holder: RosterViewHolder, position: Int) {
            val player = getItem(position)
            holder.binding.textViewItemTransform.text = "${player.firstName ?: ""} ${player.lastName ?: ""}\n#${player.jerseyNumber ?: "00"} | ${player.position ?: "None"}"
            holder.itemView.setOnClickListener { onItemClick?.invoke(player) }
        }
    }
    class RosterViewHolder(val binding: ItemTransformBinding) : RecyclerView.ViewHolder(binding.root)
    class RosterDiff : DiffUtil.ItemCallback<Player>() {
        override fun areItemsTheSame(old: Player, new: Player) = old.id == new.id
        override fun areContentsTheSame(old: Player, new: Player) = old == new
    }
}
