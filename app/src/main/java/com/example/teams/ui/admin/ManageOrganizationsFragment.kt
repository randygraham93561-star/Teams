package com.example.teams.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.teams.R
import com.example.teams.data.Organization
import com.example.teams.databinding.FragmentManageOrganizationsBinding
import com.example.teams.databinding.ItemOrganizationBinding

class ManageOrganizationsFragment : Fragment() {

    private var _binding: FragmentManageOrganizationsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val adminViewModel = ViewModelProvider(requireActivity()).get(AdminViewModel::class.java)
        _binding = FragmentManageOrganizationsBinding.inflate(inflater, container, false)
        
        val adapter = OrgAdapter { org ->
            val bundle = Bundle().apply { putString("orgId", org.id) }
            findNavController().navigate(R.id.nav_edit_organization, bundle)
        }
        binding.recyclerviewOrgs.adapter = adapter
        
        // Trigger global system admin sync
        adminViewModel.setAccessLevel(null, true)
        
        adminViewModel.organizations.observe(viewLifecycleOwner) { orgs ->
            adapter.submitList(orgs)
        }
        
        binding.fabAddOrg.setOnClickListener {
            findNavController().navigate(R.id.nav_edit_organization)
        }
        
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    class OrgAdapter(val onClick: (Organization) -> Unit) : ListAdapter<Organization, OrgViewHolder>(OrgDiff()) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrgViewHolder {
            val binding = ItemOrganizationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return OrgViewHolder(binding)
        }
        override fun onBindViewHolder(holder: OrgViewHolder, position: Int) {
            val org = getItem(position)
            holder.binding.textOrgItemName.text = org.name ?: "Unknown Organization"
            holder.binding.textOrgItemTier.text = "Tier: ${org.tier ?: "Free"}"
            holder.binding.textOrgItemAdmin.text = "Contact: ${org.contactEmail ?: "No Contact"}"
            holder.itemView.setOnClickListener { onClick(org) }
        }
    }
    class OrgViewHolder(val binding: ItemOrganizationBinding) : RecyclerView.ViewHolder(binding.root)
    class OrgDiff : DiffUtil.ItemCallback<Organization>() {
        override fun areItemsTheSame(old: Organization, new: Organization) = old.id == new.id
        override fun areContentsTheSame(old: Organization, new: Organization) = old == new
    }
}
