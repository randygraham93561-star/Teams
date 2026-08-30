package com.example.teams.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.teams.R
import com.example.teams.data.PlayCriteria
import com.example.teams.data.Team
import com.example.teams.databinding.FragmentAddTeamBinding

class AddTeamFragment : Fragment() {

    private var _binding: FragmentAddTeamBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddTeamBinding.inflate(inflater, container, false)
        
        binding.btnSave.setOnClickListener {
            saveTeam()
        }
        
        return binding.root
    }

    private fun saveTeam() {
        val teamId = binding.editTeamId.text.toString()
        val coachEmail = binding.editCoachEmail.text.toString()
        
        if (teamId.isEmpty() || coachEmail.isEmpty()) {
            Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
            return
        }
        
        val criteria = when (binding.radioGroupCriteria.checkedRadioButtonId) {
            R.id.radio_half -> PlayCriteria.HALF_GAME
            R.id.radio_three_quarters -> PlayCriteria.THREE_QUARTERS
            else -> PlayCriteria.NONE
        }
        
        val newTeam = Team(
            id = teamId,
            coachEmail = coachEmail,
            playCriteria = criteria
        )
        
        // Placeholder for saving logic
        Toast.makeText(context, "Team $teamId saved for $coachEmail with $criteria", Toast.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
