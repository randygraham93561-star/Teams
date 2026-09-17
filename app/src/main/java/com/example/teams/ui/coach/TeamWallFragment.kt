package com.example.teams.ui.coach

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.teams.data.*
import com.example.teams.databinding.FragmentTeamWallBinding
import com.example.teams.databinding.ItemTeamPostBinding
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class TeamWallFragment : Fragment() {

    private var _binding: FragmentTeamWallBinding? = null
    private val binding get() = _binding!!
    private val auth = FirebaseAuth.getInstance()
    private val repository = FirestoreRepository()
    private var currentTeamId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTeamWallBinding.inflate(inflater, container, false)
        
        val adapter = PostAdapter()
        binding.recyclerviewPosts.adapter = adapter
        
        loadTeamAndPosts(adapter)
        
        binding.btnSendPost.setOnClickListener {
            sendPost()
        }
        
        return binding.root
    }

    private fun loadTeamAndPosts(adapter: PostAdapter) {
        viewLifecycleOwner.lifecycleScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            val profile = repository.getUserProfile(uid) ?: return@launch
            
            // Coaches have teamId, Parents have playerIds -> TeamIds
            val teamId = if (profile.isCoach()) {
                profile.teamId
            } else {
                repository.getPlayersForParent(uid).firstOrNull()?.teamId
            }
            
            currentTeamId = teamId
            
            if (profile.isParent()) {
                binding.layoutPostInput.visibility = View.GONE
            }

            teamId?.let { id ->
                repository.getTeamPosts(id).addSnapshotListener { snapshot, _ ->
                    val posts = snapshot?.documents?.mapNotNull { it.toObject(TeamPost::class.java) } ?: emptyList()
                    adapter.submitList(posts.sortedByDescending { it.timestamp })
                }
            }
        }
    }

    private fun sendPost() {
        val content = binding.editPostContent.text.toString()
        val teamId = currentTeamId
        val uid = auth.currentUser?.uid
        
        if (content.isEmpty() || teamId == null || uid == null) return
        
        viewLifecycleOwner.lifecycleScope.launch {
            val post = TeamPost(
                teamId = teamId,
                coachUid = uid,
                content = content,
                timestamp = Date()
            )
            repository.createTeamPost(post)
            binding.editPostContent.text?.clear()
            Toast.makeText(context, "Post shared!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    class PostAdapter : ListAdapter<TeamPost, PostViewHolder>(PostDiff()) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
            val binding = ItemTeamPostBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return PostViewHolder(binding)
        }
        override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
            val post = getItem(position)
            holder.binding.textPostContent.text = post.content ?: ""
            val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            holder.binding.textPostTime.text = post.timestamp?.let { sdf.format(it) } ?: ""
            // In a real app, you'd fetch the coach's name based on coachUid
            holder.binding.textPostAuthor.text = "Coach Update"
        }
    }
    class PostViewHolder(val binding: ItemTeamPostBinding) : RecyclerView.ViewHolder(binding.root)
    class PostDiff : DiffUtil.ItemCallback<TeamPost>() {
        override fun areItemsTheSame(old: TeamPost, new: TeamPost) = old.id == new.id
        override fun areContentsTheSame(old: TeamPost, new: TeamPost) = old == new
    }
}
