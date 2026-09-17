package com.example.teams.ui.admin

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.teams.R
import com.example.teams.data.*
import com.example.teams.databinding.FragmentOrganizationSettingsBinding
import com.example.teams.databinding.ItemSeasonBinding
import com.example.teams.databinding.DialogAddTeamToSeasonBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class OrganizationSettingsFragment : Fragment() {

    private var _binding: FragmentOrganizationSettingsBinding? = null
    private val binding get() = _binding!!
    private val repository = FirestoreRepository()
    private val auth = FirebaseAuth.getInstance()
    private var organizationId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOrganizationSettingsBinding.inflate(inflater, container, false)
        
        fetchAdminOrg()

        binding.btnSaveOrgRules.setOnClickListener { saveRules() }
        binding.btnAddSeason.setOnClickListener { showAddSeasonDialog() }
        
        return binding.root
    }

    private fun fetchAdminOrg() {
        viewLifecycleOwner.lifecycleScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            val profile = repository.getUserProfile(uid)
            organizationId = profile?.organizationId
            
            organizationId?.let { id ->
                val org = repository.getOrganizationById(id)
                org?.let {
                    when (it.playCriteria) {
                        PlayCriteria.HALF_GAME -> binding.radioOrgHalf.isChecked = true
                        PlayCriteria.THREE_QUARTERS -> binding.radioOrgThreeQuarters.isChecked = true
                        else -> binding.radioOrgNone.isChecked = true
                    }

                    when (it.gkFieldRequirement) {
                        GKFieldRequirement.ONE_QUARTER -> binding.radioGkOneQuarter.isChecked = true
                        GKFieldRequirement.HALF_GAME -> binding.radioGkHalf.isChecked = true
                        else -> binding.radioGkNone.isChecked = true
                    }
                }
                loadSeasons(id)
            }
        }
    }

    private fun loadSeasons(orgId: String) {
        val adapter = SeasonAdapter { season -> showAddTeamDialog(season) }
        binding.recyclerviewSeasons.adapter = adapter

        FirebaseFirestore.getInstance().collection("seasons")
            .whereEqualTo("organizationId", orgId)
            .addSnapshotListener { snapshots, _ ->
                snapshots?.let {
                    val list = it.documents.mapNotNull { doc -> doc.toObject(Season::class.java)?.copy(id = doc.id) }
                    adapter.submitList(list)
                }
            }
    }

    private fun saveRules() {
        val id = organizationId ?: return
        val criteria = when (binding.radioGroupOrgCriteria.checkedRadioButtonId) {
            R.id.radio_org_half -> PlayCriteria.HALF_GAME
            R.id.radio_org_three_quarters -> PlayCriteria.THREE_QUARTERS
            else -> PlayCriteria.NONE
        }

        val gkRequirement = when (binding.radioGroupGkRequirement.checkedRadioButtonId) {
            R.id.radio_gk_one_quarter -> GKFieldRequirement.ONE_QUARTER
            R.id.radio_gk_half -> GKFieldRequirement.HALF_GAME
            else -> GKFieldRequirement.NONE
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repository.updateOrganizationRules(id, criteria, gkRequirement)
            Toast.makeText(context, "Organization rules updated!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAddSeasonDialog() {
        val input = EditText(requireContext()).apply { hint = "Season Name (e.g. Fall 2026)" }
        AlertDialog.Builder(requireContext())
            .setTitle("Create Draft Season")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString()
                if (name.isNotEmpty()) createSeason(name)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createSeason(name: String) {
        val orgId = organizationId ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            repository.saveSeason(Season(
                name = name, 
                organizationId = orgId,
                isActive = true,
                live = false
            ))
            Toast.makeText(context, "Draft Season created!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAddTeamDialog(season: Season) {
        val dialogBinding = DialogAddTeamToSeasonBinding.inflate(layoutInflater)
        
        val divisions = listOf("8U", "10U", "12U", "14U", "16U", "19U")
        dialogBinding.autoCompleteBuilderDiv.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, divisions))
        
        val genders = listOf("Boys", "Girls", "Coed")
        dialogBinding.autoCompleteBuilderGen.setAdapter(ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, genders))

        fun updateId() {
            val d = dialogBinding.autoCompleteBuilderDiv.text.toString()
            val g = dialogBinding.autoCompleteBuilderGen.text.toString()
            val n = dialogBinding.editBuilderNum.text.toString()
            if (d.isNotEmpty() && g.isNotEmpty() && n.isNotEmpty()) {
                val formattedN = if (n.length == 1) "0$n" else n
                dialogBinding.textGeneratedId.text = "Team ID: $d${g.first()}-$formattedN"
            }
        }

        dialogBinding.autoCompleteBuilderDiv.setOnItemClickListener { _, _, _, _ -> updateId() }
        dialogBinding.autoCompleteBuilderGen.setOnItemClickListener { _, _, _, _ -> updateId() }
        dialogBinding.editBuilderNum.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { updateId() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        AlertDialog.Builder(requireContext())
            .setTitle("Add Team to ${season.name}")
            .setView(dialogBinding.root)
            .setPositiveButton("Add Team") { _, _ ->
                val div = dialogBinding.autoCompleteBuilderDiv.text.toString()
                val gen = dialogBinding.autoCompleteBuilderGen.text.toString()
                val num = dialogBinding.editBuilderNum.text.toString()

                if (div.isNotEmpty() && gen.isNotEmpty() && num.isNotEmpty()) {
                    val formattedN = if (num.length == 1) "0$num" else num
                    val name = "$div${gen.first()}-$formattedN"
                    
                    viewLifecycleOwner.lifecycleScope.launch {
                        repository.saveTeam(Team(
                            name = name,
                            divisionName = div,
                            gender = gen,
                            organizationId = organizationId,
                            seasonId = season.id
                        ))
                        Toast.makeText(context, "Team $name added to draft!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    class SeasonAdapter(val onClick: (Season) -> Unit) : ListAdapter<Season, SeasonViewHolder>(SeasonDiff()) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SeasonViewHolder {
            val binding = ItemSeasonBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return SeasonViewHolder(binding)
        }
        override fun onBindViewHolder(holder: SeasonViewHolder, position: Int) {
            val season = getItem(position)
            holder.binding.textSeasonName.text = season.name
            
            val status = when {
                season.archivedAt != null -> "Archived"
                season.live == true -> "LIVE"
                else -> "Draft"
            }
            holder.binding.textSeasonStatus.text = status
            
            // Allow adding teams to Draft and Live seasons, but maybe not archived ones?
            holder.binding.btnAddTeamToSeason.visibility = if (season.archivedAt == null) View.VISIBLE else View.GONE
            holder.binding.btnAddTeamToSeason.setOnClickListener { onClick(season) }
        }
    }
    class SeasonViewHolder(val binding: ItemSeasonBinding) : RecyclerView.ViewHolder(binding.root)
    class SeasonDiff : DiffUtil.ItemCallback<Season>() {
        override fun areItemsTheSame(old: Season, new: Season) = old.id == new.id
        override fun areContentsTheSame(old: Season, new: Season) = old == new
    }
}
