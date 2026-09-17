package com.example.teams.ui.coach

import android.content.ClipData
import android.content.ClipDescription
import android.os.Bundle
import android.view.*
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.teams.R
import com.example.teams.data.*
import com.example.teams.databinding.FragmentLineupBinding
import com.example.teams.databinding.ItemTransformBinding
import com.google.android.material.tabs.TabLayout
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.content.Context
import android.util.Log
import com.google.android.material.color.MaterialColors
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class LineupFragment : Fragment() {

    private var _binding: FragmentLineupBinding? = null
    private val binding get() = _binding!!
    private val repository = FirestoreRepository()
    private val auth = FirebaseAuth.getInstance()
    
    private var teamId: String = ""
    private var gameId: String? = null
    private var roster: List<Player> = emptyList()
    
    private var playersOnFieldCount = 11
    private var currentFormation: String = "1-4-4-2"
    
    // PeriodNumber -> (PositionID -> Player)
    private val assignmentsByPeriod = mutableMapOf<Int, MutableMap<String, Player>>()
    private var currentPeriod = 1

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLineupBinding.inflate(inflater, container, false)
        
        teamId = arguments?.getString("teamId") ?: ""
        gameId = arguments?.getString("gameId")
        
        setupTabs()
        setupRoster()
        setupField()
        
        binding.btnVerifyRules.setOnClickListener { verifyEveryonePlays() }
        binding.btnAiHelper.setOnClickListener { autoGenerateLineup() }
        binding.btnPrint.setOnClickListener { showPrintConfirmation() }
        binding.btnSaveLineup.setOnClickListener { saveLineupForGame() }
        
        if (gameId != null) {
            loadLineupForGame()
        }
        
        return binding.root
    }

    private fun loadLineupForGame() {
        val gid = gameId ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            // Roster needs to be loaded first (done in setupRoster, but we need it here too)
            roster = repository.getRoster(teamId)
            
            repository.getLineupForGame(gid).collect { snapshot ->
                val lineupDoc = snapshot.documents.firstOrNull()
                val lineup = lineupDoc?.toObject(Lineup::class.java)
                lineup?.periodAssignments?.forEach { pa ->
                    val periodMap = assignmentsByPeriod.getOrPut(pa.periodNumber ?: 1) { mutableMapOf() }
                    pa.positions?.forEach { (posId, playerId) ->
                        val player = roster.find { it.id == playerId }
                        if (player != null) {
                            periodMap[posId] = player
                        }
                    }
                }
                refreshField()
            }
        }
    }

    private fun saveLineupForGame() {
        val gid = gameId ?: return
        val periodAssignments = assignmentsByPeriod.map { (period, positions) ->
            PeriodAssignment(
                periodNumber = period,
                positions = positions.mapValues { it.value.id ?: "" }
            )
        }
        
        val lineup = Lineup(
            teamId = teamId,
            gameId = gid,
            divisionType = playersOnFieldCount,
            periodAssignments = periodAssignments,
            timestamp = Date()
        )
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                repository.saveLineup(lineup)
                Toast.makeText(context, "Lineup saved for match.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error saving lineup: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showPrintConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Print Lineup Card")
            .setMessage("Recommendation: Use card stock paper for durability during the match.\n\nNote: This print includes the Official Lineup Card and MUST be printed Double-Sided (Long Edge) to align correctly for the Referee and Coach guides.")
            .setPositiveButton("Proceed to Print") { _, _ ->
                printLineupCard()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun printLineupCard() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val team = repository.getTeamById(teamId) ?: return@launch
                val org = repository.getOrganizationById(team.organizationId ?: "")
                val coach = auth.currentUser?.uid?.let { repository.getUserProfile(it) }
                
                // Fetch division info for specific duration printing
                val divisions = repository.getDivisions(team.organizationId ?: "", team.seasonId)
                val teamDivision = divisions.find { it.name == team.divisionName } 
                    ?: divisions.find { team.name?.contains(it.name ?: "NONE") == true }

                val html = generateLineupHtml(team, org, coach, teamDivision)
                
                val webView = WebView(requireContext())
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        val printManager = requireContext().getSystemService(Context.PRINT_SERVICE) as PrintManager
                        val jobName = "${team.name}_LineupCard"
                        val printAdapter = webView.createPrintDocumentAdapter(jobName)
                        
                        val attributes = PrintAttributes.Builder()
                            .setMediaSize(PrintAttributes.MediaSize.NA_LETTER)
                            .setResolution(PrintAttributes.Resolution("res1", "Service", 300, 300))
                            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                            .setDuplexMode(PrintAttributes.DUPLEX_MODE_LONG_EDGE)
                            .build()
                        
                        printManager.print(jobName, printAdapter, attributes)
                    }
                }
                webView.loadDataWithBaseURL(null, html, "text/HTML", "UTF-8", null)
                
            } catch (e: Exception) {
                Toast.makeText(context, "Print Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun generateLineupHtml(team: Team, org: Organization?, coach: UserProfile?, division: Division?): String {
        val teamDisplayName = team.customName ?: team.name ?: "Unknown Team"
        val teamIdStr = team.name ?: "N/A"
        val ageGroup = division?.name ?: "${team.divisionName ?: ""}${team.gender?.first() ?: ""}"
        val region = org?.name ?: "1297"
        val coachName = coach?.name ?: ""
        val dateStr = SimpleDateFormat("MM/dd/yy", Locale.US).format(Date())
        
        val builder = StringBuilder()
        builder.append("<html><head><style>")
        builder.append("body { font-family: 'Arial Narrow', sans-serif; margin: 0; padding: 0; color: #000; background: #fff; }")
        builder.append(".page { width: 8.5in; height: 11in; page-break-after: always; display: flex; flex-direction: column; box-sizing: border-box; overflow: hidden; }")
        
        // Strip height: 3.5in (strictly 1/3 of the paper height)
        builder.append(".referee-strip { height: 3.5in; width: 100%; border-bottom: 2px dashed #000; position: relative; overflow: hidden; display: flex; align-items: center; justify-content: center; background: #fff; }")
        
        // The card: 3.3in wide x 8.2in tall. Rotated 90deg -> 8.2in wide x 3.3in tall.
        builder.append(".rotated-card { width: 3.3in; height: 8.2in; transform: rotate(90deg); position: absolute; display: flex; flex-direction: column; padding: 10px; box-sizing: border-box; }")
        
        // COACH SECTION - Remaining 2/3 (roughly 7.5in)
        builder.append(".coach-section { height: 7.5in; width: 100%; padding: 10px; box-sizing: border-box; display: flex; flex-direction: column; }")
        
        builder.append(".official-header { text-align: center; font-weight: bold; font-size: 11pt; margin: 0 0 4px 0; text-decoration: underline; }")
        builder.append(".info-line { display: flex; justify-content: space-between; font-size: 8pt; border-bottom: 1px solid #000; margin-bottom: 3px; min-height: 14px; }")
        builder.append(".label-text { font-weight: bold; font-size: 7.5pt; margin-right: 2px; }")
        
        // ENHANCED: Increased row spacing in roster table
        builder.append(".roster-table { width: 100%; border-collapse: collapse; font-size: 7.5pt; margin-top: 3px; }")
        builder.append(".roster-table th { background: #eee; font-weight: bold; border: 1px solid #000; padding: 2px; }")
        builder.append(".roster-table td { border: 1px solid #000; padding: 4px 2px; text-align: center; height: 22px; }")
        
        builder.append(".rules-row { width: 100%; border-collapse: collapse; font-size: 6.5pt; margin-top: 2px; border: 1.5px solid #000; }")
        builder.append(".rules-row td, .rules-row th { border: 1px solid #000; padding: 2px; text-align: center; }")
        
        builder.append(".report-grid { width: 100%; font-size: 7.5pt; border-collapse: collapse; margin-top: 2px; }")
        builder.append(".report-grid td { border-bottom: 1px solid #000; padding: 3px 2px; }")
        builder.append(".conduct-row { display: flex; justify-content: space-around; font-size: 7pt; margin-top: 5px; background: #f0f0f0; padding: 2px; font-weight: bold; }")
        builder.append(".incident-box { flex: 1; border: 1px solid #000; margin: 5px 0; background-image: repeating-linear-gradient(white, white 15px, #eee 16px); padding: 4px; font-size: 8pt; }")
        
        // Tactical Styling (Bottom 2/3)
        builder.append(".diagram-row { display: flex; flex-direction: row; justify-content: space-between; flex: 1; min-height: 0; align-items: stretch; }")
        builder.append(".tactical-box { width: 49.5%; border: 2px solid #000; display: flex; flex-direction: column; padding: 5px; box-sizing: border-box; height: 100%; }")
        builder.append(".soccer-pitch { position: relative; width: 100%; flex: 1; border: 2px solid #000; background: #fff; margin: 5px 0; overflow: hidden; min-height: 350px; }")
        builder.append(".p-center-line { position: absolute; top: 50%; left: 0; right: 0; border-top: 1px dashed #000; }")
        builder.append(".p-center-circ { position: absolute; top: 50%; left: 50%; width: 50px; height: 50px; border: 1px solid #000; border-radius: 50%; transform: translate(-50%, -50%); }")
        builder.append(".player-node { position: absolute; width: 22px; height: 22px; background: #000; color: #fff; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-weight: bold; font-size: 8pt; transform: translate(-50%, -50%); z-index: 10; }")
        builder.append(".pos-tag { position: absolute; font-size: 8pt; font-weight: bold; transform: translateX(-50%); text-align: center; line-height: 1.1; background: rgba(255,255,255,0.9); border-radius: 3px; padding: 1px 3px; }")
        
        builder.append("</style></head><body>")

        // PAGE 1: FRONT
        builder.append("<div class='page'>")
        builder.append("<div class='referee-strip'>")
        builder.append("<div class='rotated-card'>")
        builder.append("<div class='official-header'>OFFICIAL LINEUP CARD</div>")
        builder.append("<div class='info-line'><span><span class='label-text'>REGION:</span> $region</span><span><span class='label-text'>AGE GROUP:</span> $ageGroup</span><span><span class='label-text'>TEAM #:</span> $teamIdStr</span><span><span class='label-text'>DATE:</span> $dateStr</span></div>")
        builder.append("<div class='info-line'><span><span class='label-text'>TEAM NAME:</span> $teamDisplayName</span><span><span class='label-text'>OPPOSING TEAM:</span> _______________</span></div>")
        builder.append("<div class='info-line'><span><span class='label-text'>COACH:</span> $coachName</span><span><span class='label-text'>ASST:</span> _______________</span></div>")
        builder.append("<table class='roster-table'>")
        builder.append("<tr><th rowspan='2' width='20'>No.</th><th rowspan='2'>PRINT PLAYERS NAME</th><th rowspan='2' width='40'>Goals</th><th colspan='4'>Qtrs. Not Played</th></tr>")
        builder.append("<tr><th>1</th><th>2</th><th>3</th><th>4</th></tr>")
        val sortedRoster = roster.sortedBy { it.jerseyNumber?.toIntOrNull() ?: 99 }
        sortedRoster.forEach { p ->
            builder.append("<tr><td>${p.jerseyNumber ?: ""}</td><td style='text-align:left; padding-left:10px;'>${p.firstName} ${p.lastName}</td><td></td>")
            for (q in 1..4) {
                val assignments = assignmentsByPeriod[q]
                val isPlaying = assignments?.values?.any { it.id == p.id } == true
                val pos = assignments?.filterValues { it.id == p.id }?.keys?.firstOrNull()
                
                val mark = when {
                    !isPlaying -> "X"
                    pos == "GK" -> "G"
                    else -> ""
                }
                builder.append("<td style='color: #bbb;'>$mark</td>")
            }
            builder.append("</tr>")
        }
        for (i in roster.size until 15) { builder.append("<tr><td>&nbsp;</td><td></td><td></td><td></td><td></td><td></td><td></td></tr>") }
        builder.append("</table>")
        
        builder.append("<table class='rules-row'>")
        builder.append("<tr><th>Division: $ageGroup</th><td>${division?.halfDurationMinutes ?: "30"} min Half</td><td>${(division?.halfDurationMinutes ?: 30) * 2} min Game</td><td>Ball Size: ${division?.ballSize ?: "4"}</td></tr>")
        builder.append("</table>")
        builder.append("</div></div>")

        builder.append("<div class='coach-section'>")
        builder.append("<div style='font-weight:bold; font-size:11pt; text-align:center; margin-bottom:5px;'>COACH'S GAME GUIDE - FIRST HALF</div>")
        builder.append("<div class='diagram-row'>")
        builder.append(generateTacticalBoxHtml(1))
        builder.append(generateTacticalBoxHtml(2))
        builder.append("</div></div></div>")

        // PAGE 2: BACK
        builder.append("<div class='page'>")
        builder.append("<div class='referee-strip'>")
        builder.append("<div class='rotated-card' style='transform: rotate(-90deg);'>")
        builder.append("<div class='official-header' style='font-size:12pt; text-decoration:none;'>REFEREE GAME REPORT</div>")
        builder.append("<table class='report-grid'>")
        builder.append("<tr><td><span class='label-text'>Date:</span> $dateStr</td><td><span class='label-text'>Time:</span> ____</td><td><span class='label-text'>Field:</span> ____</td></tr>")
        builder.append("<tr><td colspan='2'><span class='label-text'>Home Team:</span> $teamDisplayName</td><td><span class='label-text'>Visitor:</span> ___________</td></tr>")
        builder.append("<tr><td><span class='label-text'>Halftime Score:</span> ____</td><td><span class='label-text'>Final Score:</span> ____</td><td><span class='label-text'>Winner:</span> ________</td></tr>")
        builder.append("</table>")
        
        builder.append("<div style='display:flex; justify-content:space-around; font-size:7.5pt; margin-top:5px; border-bottom:1px solid #000; padding:2px;'>")
        builder.append("<strong>Field Condition:</strong> <span>[ ] Good</span> <span>[ ] Fair</span> <span>[ ] Wet</span> <span>[ ] Holes</span> <span>[ ] Puddles</span>")
        builder.append("</div>")

        builder.append("<div style='font-weight:bold; text-align:center; font-size:7.5pt; margin-top:3px; background:#eee;'>Overall Conduct: Players [ ] Coaches [ ] Spectators [ ]</div>")
        
        builder.append("<div style='margin-top:5px; font-size:8.5pt;'>")
        builder.append("<div style='border-bottom:1px solid #000; padding:2px;'><strong>Referee:</strong> _________________________________ <strong>Phone/Email:</strong> _______________</div>")
        builder.append("<div style='border-bottom:1px solid #000; padding:2px;'><strong>1st AR:</strong> _________________________________ <strong>Phone/Email:</strong> _______________</div>")
        builder.append("<div style='border-bottom:1px solid #000; padding:2px;'><strong>2nd AR:</strong> _________________________________ <strong>Phone/Email:</strong> _______________</div>")
        builder.append("</div>")

        builder.append("<div style='font-weight:bold; text-align:center; font-size:11pt; margin-top:5px;'>Preliminary Incident Report</div>")
        builder.append("<div class='incident-box'><strong>INCIDENT REPORT / NOTES:</strong></div>")
        builder.append("<div style='display:flex; justify-content:space-between; font-size:8pt; margin-top:5px;'><span>Ref Sign: ________________</span><span>AR1 Sign: ________________</span><span>AR2 Sign: ________________</span></div>")
        builder.append("</div></div>")

        builder.append("<div class='coach-section'>")
        builder.append("<div style='font-weight:bold; font-size:11pt; text-align:center; margin-bottom:5px;'>COACH'S GAME GUIDE - SECOND HALF</div>")
        builder.append("<div class='diagram-row'>")
        builder.append(generateTacticalBoxHtml(3))
        builder.append(generateTacticalBoxHtml(4))
        builder.append("</div></div></div>")

        builder.append("</body></html>")
        return builder.toString()
    }

    private fun generateTacticalBoxHtml(period: Int): String {
        val card = StringBuilder()
        card.append("<div class='tactical-box'>")
        card.append("<h2 style='background:#000; color:#fff; font-size:11pt; margin:0; text-align:center;'>QUARTER $period</h2>")
        card.append("<div class='soccer-pitch'>")
        card.append("<div class='p-center-line'></div>")
        card.append("<div class='p-center-circ'></div>")
        val positions = getPositionsForFormation(currentFormation)
        positions.forEach { pos ->
            val player = assignmentsByPeriod[period]?.get(pos.id ?: "")
            if (player != null) {
                val x = (pos.xPercent ?: 0.5f) * 100
                val y = (pos.yPercent ?: 0.5f) * 100
                card.append("<div class='player-node' style='left: $x%; top: $y%;'>${player.jerseyNumber ?: ""}</div>")
                card.append("<div class='pos-tag' style='left:$x%; top:${y+6}%;'><strong>${pos.id}</strong><br/>${player.firstName}</div>")
            }
        }
        card.append("</div>")
        card.append("<div style='font-size:8.5pt; margin-top:auto;'><strong>SITTING:</strong> ")
        val sitting = roster.filter { p -> assignmentsByPeriod[period]?.values?.any { it.id == p.id } != true }
            .sortedBy { it.jerseyNumber?.toIntOrNull() ?: 99 }
            .map { "#${it.jerseyNumber ?: ""}" }
            .joinToString(", ")
        card.append(if (sitting.isNotEmpty()) sitting else "NONE")
        card.append("</div></div>")
        return card.toString()
    }

    private fun setupTabs() {
        for (i in 1..4) {
            binding.tabLayoutPeriods.addTab(binding.tabLayoutPeriods.newTab().setText("Period $i"))
            assignmentsByPeriod[i] = mutableMapOf()
        }
        
        binding.tabLayoutPeriods.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentPeriod = (tab?.position ?: 0) + 1
                refreshField()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupRoster() {
        val adapter = RosterAdapter { player, view ->
            val item = ClipData.Item(player.id)
            val dragData = ClipData(player.firstName, arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN), item)
            val shadow = View.DragShadowBuilder(view)
            view.startDragAndDrop(dragData, shadow, player, 0)
        }
        binding.recyclerviewRoster.adapter = adapter
        
        viewLifecycleOwner.lifecycleScope.launch {
            roster = repository.getRoster(teamId)
            adapter.submitList(roster)
        }
    }

    private fun setupField() {
        refreshField()
    }

    private fun refreshField() {
        viewLifecycleOwner.lifecycleScope.launch {
            val team = repository.getTeamById(teamId)
            val orgId = team?.organizationId ?: ""
            val seasonId = team?.seasonId
            
            val divisions = repository.getDivisions(orgId, seasonId)
            val teamName = team?.name ?: ""
            val assignedDiv = team?.divisionName ?: ""
            
            val teamDivision = divisions.find { 
                it.name?.isNotEmpty() == true && 
                (it.name.equals(assignedDiv, ignoreCase = true) || teamName.contains(it.name, ignoreCase = true))
            }

            if (teamDivision != null) {
                playersOnFieldCount = teamDivision.playersPerTeam?.toInt() ?: 11
            } else {
                playersOnFieldCount = when {
                    teamName.contains("12U", ignoreCase = true) -> 9
                    teamName.contains("10U", ignoreCase = true) -> 7
                    teamName.contains("8U", ignoreCase = true) -> 5
                    else -> 11
                }
                Log.d("Lineup", "Division match failed for $teamName. Using fallback count: $playersOnFieldCount")
            }
            
            setupFormationSelector()
            renderFieldPositions()
        }
    }

    private fun setupFormationSelector() {
        val formations = when (playersOnFieldCount) {
            11 -> listOf("1-4-4-2", "1-4-3-3", "1-3-5-2", "1-5-3-2")
            9 -> listOf("1-3-3-2", "1-3-4-1", "1-4-3-1")
            7 -> listOf("1-2-3-1", "1-3-2-1", "1-2-2-2")
            else -> listOf("1-2-1")
        }
        
        if (currentFormation !in formations) currentFormation = formations.first()
        
        context?.let { ctx ->
            val adapter = ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, formations)
            binding.autoCompleteFormation.setAdapter(adapter)
            binding.autoCompleteFormation.setText(currentFormation, false)
            
            binding.autoCompleteFormation.setOnItemClickListener { _, _, _, _ ->
                currentFormation = binding.autoCompleteFormation.text.toString()
                renderFieldPositions()
            }
        }
    }

    private fun renderFieldPositions() {
        binding.fieldContainer.removeAllViews()
        
        val positions = getPositionsForFormation(currentFormation)

        binding.fieldContainer.post {
            val width = binding.fieldContainer.width
            val height = binding.fieldContainer.height
            
            positions.forEach { pos ->
                val posView = createPositionView(pos)
                val params = FrameLayout.LayoutParams(120, 120)
                params.leftMargin = ((pos.xPercent ?: 0f) * width).toInt() - 60
                params.topMargin = ((pos.yPercent ?: 0f) * height).toInt() - 60
                binding.fieldContainer.addView(posView, params)
            }
        }
    }

    private fun getPositionsForFormation(formation: String): List<FieldPosition> {
        val posList = mutableListOf<FieldPosition>()
        posList.add(FieldPosition("GK", "Goalie", 0.5f, 0.9f))

        val parts = formation.split("-").drop(1).map { it.toInt() } // e.g. [4, 4, 2]
        
        val rows = parts.size
        val rowSpacing = 0.8f / (rows + 1)
        
        parts.forEachIndexed { rowIndex, playerCount ->
            val y = 0.9f - (rowSpacing * (rowIndex + 1))
            val xSpacing = 1.0f / (playerCount + 1)
            
            for (i in 1..playerCount) {
                val x = xSpacing * i
                val label = when(rowIndex) {
                    0 -> { // Defenders
                        when (playerCount) {
                            1 -> "CB"
                            2 -> if (i == 1) "LB" else "RB"
                            3 -> if (i == 1) "LB" else if (i == 2) "CB" else "RB"
                            4 -> if (i == 1) "LB" else if (i == 2) "CB1" else if (i == 3) "CB2" else "RB"
                            else -> "D$i"
                        }
                    }
                    1 -> { // Midfielders or Forwards
                        if (rows == 2) { // Just Def and Fwd
                             when (playerCount) {
                                1 -> "CF"
                                2 -> if (i == 1) "LF" else "RF"
                                3 -> if (i == 1) "LF" else if (i == 2) "CF" else "RF"
                                else -> "F$i"
                            }
                        } else { // Midfielders
                            when (playerCount) {
                                1 -> "CM"
                                2 -> if (i == 1) "LM" else "RM"
                                3 -> if (i == 1) "LM" else if (i == 2) "CM" else "RM"
                                4 -> if (i == 1) "LM" else if (i == 2) "CM1" else if (i == 3) "CM2" else "RM"
                                5 -> if (i == 1) "LM" else if (i == 2) "LCM" else if (i == 3) "CM" else if (i == 4) "RCM" else "RM"
                                else -> "M$i"
                            }
                        }
                    }
                    2 -> { // Forwards
                        when (playerCount) {
                            1 -> "CF"
                            2 -> if (i == 1) "LF" else "RF"
                            3 -> if (i == 1) "LF" else if (i == 2) "CF" else "RF"
                            else -> "F$i"
                        }
                    }
                    else -> "P$i"
                }
                posList.add(FieldPosition(label, label, x, y))
            }
        }
        return posList
    }

    private fun createPositionView(pos: FieldPosition): View {
        val card = CardView(requireContext()).apply {
            radius = 60f
            elevation = 8f
            setCardBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface))
        }
        
        val text = TextView(requireContext()).apply {
            gravity = Gravity.CENTER
            textSize = 10f
            setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface))
            val assigned = assignmentsByPeriod[currentPeriod]?.get(pos.id ?: "")
            text = if (assigned != null) {
                "#${assigned.jerseyNumber ?: ""}"
            } else {
                pos.id ?: ""
            }
        }
        card.addView(text)

        card.setOnDragListener { v, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> true
                DragEvent.ACTION_DRAG_ENTERED -> { v.alpha = 0.5f; true }
                DragEvent.ACTION_DRAG_EXITED -> { v.alpha = 1.0f; true }
                DragEvent.ACTION_DROP -> {
                    val player = event.localState as Player
                    assignmentsByPeriod[currentPeriod]?.set(pos.id ?: "", player)
                    text.text = "#${player.jerseyNumber ?: ""}"
                    v.alpha = 1.0f
                    true
                }
                else -> false
            }
        }
        return card
    }

    private fun autoGenerateLineup() {
        if (roster.isEmpty()) return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val team = repository.getTeamById(teamId) ?: return@launch
                val orgId = team.organizationId ?: return@launch
                val divisions = repository.getDivisions(orgId, team.seasonId)
                val teamDivision = divisions.find { it.name == team.divisionName }
                
                // FIXED: Corrected reference to class variable
                val currentMaxOnField = teamDivision?.playersPerTeam ?: playersOnFieldCount

                // 1. Reset assignments
                for (i in 1..4) { assignmentsByPeriod[i]?.clear() }

                val gamePlayedCounts = mutableMapOf<String, Int>()
                roster.forEach { gamePlayedCounts[it.id ?: ""] = 0 }

                // 2. Simple Balanced Rotation Algorithm
                val formationPositions = getPositionsForFormation(currentFormation)
                
                for (period in 1..4) {
                    val available = roster.toMutableList()
                    val assigned = mutableMapOf<String, Player>()

                    // Place players into ALL available formation positions
                    formationPositions.forEach { pos ->
                        if (available.isNotEmpty()) {
                            // Prioritize players who have played the least THIS game
                            // and then those who have sat the most historically (for season balance)
                            val candidate = available.minWithOrNull(compareBy<Player> { 
                                gamePlayedCounts[it.id] ?: 0 
                            }.thenByDescending { 
                                it.satPeriodsTotal ?: 0 
                            })

                            candidate?.let { p ->
                                assigned[pos.id ?: ""] = p
                                available.remove(p)
                                gamePlayedCounts[p.id ?: ""] = (gamePlayedCounts[p.id] ?: 0) + 1
                            }
                        }
                    }
                    assignmentsByPeriod[period]?.putAll(assigned)
                }

                refreshField()
                Toast.makeText(context, "AI suggested a balanced lineup based on season history!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "AI Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun verifyEveryonePlays() {
        viewLifecycleOwner.lifecycleScope.launch {
            val team = repository.getTeamById(teamId) ?: return@launch
            val orgId = team.organizationId ?: return@launch
            
            // 1. Get Organization Rule (Default)
            val org = repository.getOrganizationById(orgId)
            var criteria = org?.playCriteria ?: PlayCriteria.NONE
            var gkRequirement = org?.gkFieldRequirement ?: GKFieldRequirement.NONE
            
            // 2. Match Team to Division Rule from Referee App data
            val divisions = repository.getDivisions(orgId, team.seasonId)
            val teamDivision = divisions.find { it.name == team.divisionName } 
            
            // Division rule overrides organization rule if set
            teamDivision?.playCriteria?.let { criteria = it }
            teamDivision?.gkFieldRequirement?.let { gkRequirement = it }
            
            val minPeriods = if (criteria == PlayCriteria.HALF_GAME) 2 else if (criteria == PlayCriteria.THREE_QUARTERS) 3 else 0
            val minFieldPeriods = if (gkRequirement == GKFieldRequirement.ONE_QUARTER) 1 else if (gkRequirement == GKFieldRequirement.HALF_GAME) 2 else 0

            val violations = mutableListOf<String>()

            roster.forEach { player ->
                var periodsAsGK = 0
                var periodsOnField = 0
                
                assignmentsByPeriod.forEach { (_, positions) ->
                    val posId = positions.filterValues { it.id == player.id }.keys.firstOrNull()
                    if (posId != null) {
                        if (posId == "GK") periodsAsGK++
                        else periodsOnField++
                    }
                }

                val totalPlayed = periodsAsGK + periodsOnField

                // Check Everyone Plays
                if (criteria != PlayCriteria.NONE && totalPlayed < minPeriods) {
                    violations.add("${player.firstName}: Short on total time ($totalPlayed/$minPeriods periods)")
                }

                // Check GK Field Time (only if they played GK at least once)
                if (gkRequirement != GKFieldRequirement.NONE && periodsAsGK > 0 && periodsOnField < minFieldPeriods) {
                    violations.add("${player.firstName}: GK needs field time ($periodsOnField/$minFieldPeriods field periods)")
                }
            }

            if (violations.isEmpty()) {
                val ruleText = "Rules: $criteria | GK Field: $gkRequirement"
                AlertDialog.Builder(requireContext())
                    .setTitle("Lineup Verified!")
                    .setMessage("Everyone meets requirements.\n$ruleText\n\nWould you like to save this game's playing time to history for season balancing?")
                    .setPositiveButton("Save Stats") { _, _ -> saveGameStats() }
                    .setNegativeButton("Close", null)
                    .show()
            } else {
                val list = violations.joinToString("\n")
                AlertDialog.Builder(requireContext())
                    .setTitle("Rule Violations Found")
                    .setMessage(list)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun saveGameStats() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                roster.forEach { player ->
                    var periodsPlayedInThisGame = 0
                    assignmentsByPeriod.forEach { (_, positions) ->
                        if (positions.values.any { it.id == player.id }) periodsPlayedInThisGame++
                    }
                    val periodsSatInThisGame = 4 - periodsPlayedInThisGame

                    val updatedPlayer = player.copy(
                        playedPeriodsTotal = (player.playedPeriodsTotal ?: 0) + periodsPlayedInThisGame,
                        satPeriodsTotal = (player.satPeriodsTotal ?: 0) + periodsSatInThisGame
                    )
                    repository.savePlayer(updatedPlayer)
                }
                Toast.makeText(context, "Season history updated!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error saving history: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    class RosterAdapter(val onLongClick: (Player, View) -> Unit) : ListAdapter<Player, RosterViewHolder>(PlayerDiff()) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RosterViewHolder {
            val binding = ItemTransformBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return RosterViewHolder(binding)
        }
        override fun onBindViewHolder(holder: RosterViewHolder, position: Int) {
            val player = getItem(position)
            holder.binding.textViewItemTransform.text = "#${player.jerseyNumber ?: "0"} | ${player.firstName} ${player.lastName}\n${player.position ?: "No Pos"}"
            holder.itemView.setOnLongClickListener { onLongClick(player, it); true }
        }
    }
    class RosterViewHolder(val binding: ItemTransformBinding) : RecyclerView.ViewHolder(binding.root)
    class PlayerDiff : DiffUtil.ItemCallback<Player>() {
        override fun areItemsTheSame(old: Player, new: Player) = old.id == new.id
        override fun areContentsTheSame(old: Player, new: Player) = old == new
    }
}
