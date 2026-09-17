package com.example.teams

import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import android.view.View
import android.widget.TextView
import com.example.teams.data.FirestoreRepository
import com.example.teams.data.UserProfile
import com.example.teams.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.appBarMain.toolbar)

        binding.appBarMain.fab?.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null)
                .setAnchorView(R.id.fab).show()
        }

        val navHostFragment =
            (supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment?)!!
        val navController = navHostFragment.navController

        // Initialize AppBarConfiguration with all top-level destinations and the drawer
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_login, 
                R.id.nav_admin_dashboard, 
                R.id.nav_coach_team, 
                R.id.nav_add_team, 
                R.id.nav_manage_organizations, 
                R.id.nav_settings,
                R.id.nav_profile
            ),
            binding.drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)

        binding.navView?.let {
            it.setupWithNavController(navController)
        }

        binding.appBarMain.contentMain.bottomNavView?.let {
            it.setupWithNavController(navController)
        }
        
        // Listen for destination changes to hide/show navigation components
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val isAuth = destination.id == R.id.nav_login || destination.id == R.id.nav_coach_auth
            
            // Hide global mailbox FAB on screens that have their own FAB or don't need it
            val hideGlobalFab = destination.id == R.id.nav_roster || 
                               destination.id == R.id.nav_admin_dashboard || 
                               destination.id == R.id.nav_coach_team ||
                               destination.id == R.id.nav_lineup ||
                               isAuth
            
            if (hideGlobalFab) {
                binding.appBarMain.fab?.hide()
            } else {
                binding.appBarMain.fab?.show()
            }

            if (isAuth) {
                // Hide side drawer and bottom nav on Login/Signup
                binding.drawerLayout?.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                binding.navView?.visibility = View.GONE
                binding.appBarMain.contentMain.bottomNavView?.visibility = View.GONE
                supportActionBar?.setDisplayHomeAsUpEnabled(false)
            } else {
                // Show navigation components on other screens
                binding.drawerLayout?.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_UNLOCKED)
                binding.navView?.visibility = View.VISIBLE
                binding.appBarMain.contentMain.bottomNavView?.visibility = View.VISIBLE
                supportActionBar?.setDisplayHomeAsUpEnabled(true)
                
                // Role-based menu visibility
                val uid = FirebaseAuth.getInstance().currentUser?.uid
                if (uid != null) {
                    lifecycleScope.launch {
                        try {
                            val profile = FirestoreRepository().getUserProfile(uid)
                            profile?.let { prof ->
                                val isSystemAdmin = prof.isSystemAdmin()
                                val isCoachAdmin = prof.isCoachAdmin()
                                
                                Log.d("TeamsRole", "Email: ${prof.email}, isSystem: $isSystemAdmin, isCoach: $isCoachAdmin")

                                // Safely access menus - checking activity still exists
                                if (isFinishing || isDestroyed) return@launch

                                val navView = binding.navView
                                val bottomNavView = binding.appBarMain.contentMain.bottomNavView
                                
                                val navMenu = navView?.menu
                                val bottomMenu = try { bottomNavView?.menu } catch(e: Exception) { null }
                                
                                // System Admin Features
                                navMenu?.findItem(R.id.nav_manage_organizations)?.let { it.isVisible = isSystemAdmin }
                                bottomMenu?.findItem(R.id.nav_manage_organizations)?.let { it.isVisible = isSystemAdmin }

                                navMenu?.let { menu ->
                                    menu.findItem(R.id.nav_organization_settings)?.let { it.isVisible = isCoachAdmin }
                                    menu.findItem(R.id.nav_admin_dashboard)?.let { it.isVisible = isCoachAdmin }
                                    menu.findItem(R.id.nav_add_team)?.let { it.isVisible = isCoachAdmin }
                                }
                                
                                bottomMenu?.let { menu ->
                                    menu.findItem(R.id.nav_admin_dashboard)?.let { it.isVisible = isCoachAdmin }
                                    menu.findItem(R.id.nav_add_team)?.let { it.isVisible = isCoachAdmin }
                                }

                                // Shared Features
                                val isCoachOrParent = prof.isCoach() || prof.isParent()
                                navView?.menu?.findItem(R.id.nav_coach_team)?.let { it.isVisible = isCoachOrParent }
                                bottomNavView?.menu?.findItem(R.id.nav_coach_team)?.let { it.isVisible = isCoachOrParent }
                                
                                navView?.menu?.findItem(R.id.nav_profile)?.let { it.isVisible = true }
                                navView?.menu?.findItem(R.id.nav_settings)?.let { it.isVisible = true }
                                navView?.menu?.findItem(R.id.nav_roster)?.let { it.isVisible = true }
                                navView?.menu?.findItem(R.id.nav_team_wall)?.let { it.isVisible = true }

                                // Update Top Bar and Sidebar Header
                                updateToolbarInfo(prof)
                                updateNavHeader(prof)
                            }
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Error updating menus", e)
                        }
                    }
                }
            }
        }
    }

    private fun updateNavHeader(profile: UserProfile) {
        val navView: NavigationView? = binding.navView
        val headerView = navView?.getHeaderView(0) ?: return
        
        val nameText: TextView? = headerView.findViewById(R.id.text_nav_header_name)
        val emailText: TextView? = headerView.findViewById(R.id.text_nav_header_email)
        
        nameText?.text = profile.name ?: "User"
        emailText?.text = profile.email ?: ""
    }

    private fun updateToolbarInfo(profile: UserProfile) {
        lifecycleScope.launch {
            try {
                val repository = FirestoreRepository()
                val orgId = profile.organizationId
                if (!orgId.isNullOrBlank()) {
                    val org = repository.getOrganizationById(orgId)
                    val season = repository.getLiveSeason(orgId)
                    
                    val orgName = org?.name ?: "No Organization"
                    val seasonName = season?.name ?: "No Live Season"
                    
                    // Concatenate to title for maximum visibility in the blue bar
                    supportActionBar?.title = "$orgName | $seasonName"
                } else {
                    supportActionBar?.title = "Teams App"
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error fetching toolbar info", e)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Hide overflow menu completely on login/signup
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        if (navController.currentDestination?.id == R.id.nav_login || 
            navController.currentDestination?.id == R.id.nav_coach_auth) {
            return false
        }
        val result = super.onCreateOptionsMenu(menu)
        // Using findViewById because NavigationView exists in different layout files
        // between w600dp and w1240dp
        val navView: NavigationView? = findViewById(R.id.nav_view)
        if (navView == null) {
            // The navigation drawer already has the items including the items in the overflow menu
            // We only inflate the overflow menu if the navigation drawer isn't visible
            menuInflater.inflate(R.menu.overflow, menu)
        }
        return result
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_settings -> {
                val navController = findNavController(R.id.nav_host_fragment_content_main)
                navController.navigate(R.id.nav_settings)
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}