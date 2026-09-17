package com.example.teams.ui.coach

import android.app.DatePickerDialog
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
import com.example.teams.data.ScheduleItem
import com.example.teams.data.ScheduleItemType
import com.example.teams.databinding.FragmentAddScheduleItemBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AddScheduleItemFragment : Fragment() {

    private var _binding: FragmentAddScheduleItemBinding? = null
    private val binding get() = _binding!!
    private val repository = FirestoreRepository()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddScheduleItemBinding.inflate(inflater, container, false)
        
        val teamId = arguments?.getString("teamId") ?: ""

        val calendar = Calendar.getInstance()
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.US)

        binding.editDate.setOnClickListener {
            DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    calendar.set(year, month, day)
                    binding.editDate.setText(sdf.format(calendar.time))
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        binding.checkRecurring.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutWeeks.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        
        binding.btnSaveItem.setOnClickListener {
            Toast.makeText(context, "Saving items in 24h format...", Toast.LENGTH_SHORT).show()
            saveItem(teamId, calendar.time)
        }
        
        return binding.root
    }

    private fun saveItem(teamId: String, selectedDate: Date) {
        val title = binding.editTitle.text.toString()
        val start = binding.editStartTime.text.toString()
        val end = binding.editEndTime.text.toString()
        val location = binding.editLocation.text.toString()
        
        if (title.isEmpty() || start.isEmpty()) {
            Toast.makeText(context, "Please fill required fields", Toast.LENGTH_SHORT).show()
            return
        }

        val type = if (binding.radioPractice.isChecked) ScheduleItemType.PRACTICE else ScheduleItemType.EVENT
        val isRecurring = binding.checkRecurring.isChecked
        val weeks = binding.editRecurringWeeks.text.toString().toIntOrNull() ?: 1

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val cal = Calendar.getInstance()
                cal.time = selectedDate

                val count = if (isRecurring) weeks else 1
                repeat(count) {
                    val item = ScheduleItem(
                        teamId = teamId,
                        type = type,
                        title = title,
                        date = cal.time,
                        startTime = start,
                        endTime = end,
                        location = location,
                        isRecurring = isRecurring
                    )
                    repository.createScheduleItem(item)
                    cal.add(Calendar.WEEK_OF_YEAR, 1)
                }

                Toast.makeText(context, "Schedule updated!", Toast.LENGTH_SHORT).show()
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
}
