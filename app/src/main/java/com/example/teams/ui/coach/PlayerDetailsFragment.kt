package com.example.teams.ui.coach

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.teams.R
import com.example.teams.data.*
import com.example.teams.databinding.FragmentPlayerDetailsBinding
import com.example.teams.databinding.ItemTransformBinding
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class PlayerDetailsFragment : Fragment() {

    private var _binding: FragmentPlayerDetailsBinding? = null
    private val binding get() = _binding!!
    private val repository = FirestoreRepository()
    private var playerId: String? = null
    private var teamId: String? = null
    private val selectedChemistryIds = mutableSetOf<String>()
    private val allPositions = listOf("GK", "LB", "CB", "RB", "LM", "CM", "RM", "LF", "RF")
    private val selectedPositions = mutableSetOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerDetailsBinding.inflate(inflater, container, false)
        
        playerId = arguments?.getString("playerId")
        teamId = arguments?.getString("teamId")

        binding.editPreferredPositions.setOnClickListener { showPositionDialog() }
        
        val adapter = ChemistryAdapter { otherPlayerId, isSelected ->
            if (isSelected) selectedChemistryIds.add(otherPlayerId)
            else selectedChemistryIds.remove(otherPlayerId)
        }
        binding.recyclerviewChemistry.adapter = adapter

        loadData(adapter)

        binding.btnSavePlayer.setOnClickListener { savePlayer() }
        
        return binding.root
    }

    private fun showPositionDialog() {
        val checkedItems = BooleanArray(allPositions.size) { i -> selectedPositions.contains(allPositions[i]) }
        
        AlertDialog.Builder(requireContext())
            .setTitle("Select Preferred Positions")
            .setMultiChoiceItems(allPositions.toTypedArray(), checkedItems) { _, which, isChecked ->
                if (isChecked) selectedPositions.add(allPositions[which])
                else selectedPositions.remove(allPositions[which])
            }
            .setPositiveButton("OK") { _, _ ->
                binding.editPreferredPositions.setText(selectedPositions.joinToString(", "))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadData(adapter: ChemistryAdapter) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // 1. Load Player Details
                playerId?.let { pid ->
                    val playerDoc = repository.db.collection("players").document(pid).get().await()
                    val player = playerDoc.toObject(Player::class.java)?.copy(id = pid)
                    player?.let {
                        binding.editPlayerFirstName.setText(it.firstName ?: "")
                        binding.editPlayerLastName.setText(it.lastName ?: "")
                        binding.editJerseyNumber.setText(it.jerseyNumber ?: "")
                        
                        it.preferredPositions?.let { positions ->
                            selectedPositions.clear()
                            selectedPositions.addAll(positions)
                            binding.editPreferredPositions.setText(positions.joinToString(", "))
                        } ?: run {
                            // Backward compatibility: check the single 'position' field
                            it.position?.let { p ->
                                selectedPositions.clear()
                                selectedPositions.add(p)
                                binding.editPreferredPositions.setText(p)
                            }
                        }

                        it.chemistryPlayerIds?.let { ids -> selectedChemistryIds.addAll(ids) }
                        teamId = it.teamId
                    }
                }

                // 2. Load Roster for Chemistry (excluding self)
                teamId?.let { tid ->
                    val fullRoster = repository.getRoster(tid)
                    val chemistryList = fullRoster.filter { it.id != playerId }
                    adapter.submitList(chemistryList)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error loading: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun savePlayer() {
        val first = binding.editPlayerFirstName.text.toString()
        val last = binding.editPlayerLastName.text.toString()
        val jersey = binding.editJerseyNumber.text.toString()

        if (first.isEmpty() || last.isEmpty()) {
            Toast.makeText(context, "Name required", Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val pid = playerId ?: ""
                val playerUpdate = Player(
                    id = if (pid.isEmpty()) null else pid,
                    firstName = first,
                    lastName = last,
                    jerseyNumber = jersey,
                    position = selectedPositions.firstOrNull(), // Primary is first selected
                    preferredPositions = selectedPositions.toList(),
                    chemistryPlayerIds = selectedChemistryIds.toList(),
                    teamId = teamId
                )
                repository.savePlayer(playerUpdate)
                Toast.makeText(context, "Player info saved!", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            } catch (e: Exception) {
                Toast.makeText(context, "Error saving: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class ChemistryAdapter(val onSelectionChanged: (String, Boolean) -> Unit) : ListAdapter<Player, ChemistryViewHolder>(PlayerDiff()) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChemistryViewHolder {
            val binding = ItemTransformBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ChemistryViewHolder(binding)
        }
        override fun onBindViewHolder(holder: ChemistryViewHolder, position: Int) {
            val player = getItem(position)
            val pid = player.id ?: ""
            holder.binding.textViewItemTransform.text = "${player.firstName} ${player.lastName}"
            
            // Reusing ItemTransformBinding for chemistry toggle (Simple visual indicator)
            val isSelected = selectedChemistryIds.contains(pid)
            holder.itemView.alpha = if (isSelected) 1.0f else 0.5f
            
            holder.itemView.setOnClickListener {
                val currentlySelected = selectedChemistryIds.contains(pid)
                onSelectionChanged(pid, !currentlySelected)
                holder.itemView.alpha = if (!currentlySelected) 1.0f else 0.5f
                notifyItemChanged(position)
            }
        }
    }
    class ChemistryViewHolder(val binding: ItemTransformBinding) : RecyclerView.ViewHolder(binding.root)
    class PlayerDiff : DiffUtil.ItemCallback<Player>() {
        override fun areItemsTheSame(old: Player, new: Player) = old.id == new.id
        override fun areContentsTheSame(old: Player, new: Player) = old == new
    }
}
