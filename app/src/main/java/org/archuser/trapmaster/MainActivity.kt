package org.archuser.trapmaster

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import org.archuser.trapmaster.data.GameRecord
import org.archuser.trapmaster.data.GameStorage
import org.archuser.trapmaster.data.RoundRecord
import org.archuser.trapmaster.databinding.ActivityMainBinding
import org.archuser.trapmaster.databinding.ItemGameHistoryBinding
import org.archuser.trapmaster.databinding.ItemSummaryRoundBinding
import org.archuser.trapmaster.ui.RtspUriDialogFragment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt
import kotlin.text.Charsets

class MainActivity : AppCompatActivity(), RtspUriDialogFragment.Callback {

    private lateinit var binding: ActivityMainBinding
    private val storage by lazy { GameStorage(this) }

    private var currentScreen: Screen = Screen.HOME
    private var mutableGame: MutableGame? = null
    private var summaryGame: GameRecord? = null
    private var cachedGames: List<GameRecord> = emptyList()
    private var pendingExportCsv: String? = null

    private val isoFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val exportDateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val historyFormatter = SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault())

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        val csv = pendingExportCsv
        pendingExportCsv = null
        if (uri != null && csv != null) {
            contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(csv.toByteArray())
            }
            showSnackbar(getString(R.string.export_success))
        }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { handleImportUri(it) }
    }

    private val importMimeTypes = arrayOf("text/csv", "text/comma-separated-values", "application/csv")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()
        setupRoundSelector()
        setupListeners()
        refreshHistory()
        showScreen(Screen.HOME)

        if (savedInstanceState == null) {
            intent?.let { handleIncomingIntent(it) }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIncomingIntent(it) }
    }

    private fun setupWindowInsets() {
        val headerPadding = binding.headerContainer.paddingTop
        val homePadding = binding.homeScreen.paddingBottom
        val asYouGoPadding = binding.asYouGoScreen.paddingBottom
        val recordPadding = binding.recordRoundScreen.paddingBottom
        val summaryPadding = binding.summaryScreen.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.headerContainer.updatePadding(top = headerPadding + systemBars.top)
            binding.homeScreen.updatePadding(bottom = homePadding + systemBars.bottom)
            binding.asYouGoScreen.updatePadding(bottom = asYouGoPadding + systemBars.bottom)
            binding.recordRoundScreen.updatePadding(bottom = recordPadding + systemBars.bottom)
            binding.summaryScreen.updatePadding(bottom = summaryPadding + systemBars.bottom)
            insets
        }

        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun setupListeners() {
        binding.startAsYouGoButton.setOnClickListener {
            mutableGame = createNewGame()
            summaryGame = null
            showScreen(Screen.AS_YOU_GO)
        }
        binding.startRecordRoundButton.setOnClickListener {
            mutableGame = createNewGame()
            summaryGame = null
            showScreen(Screen.RECORD_ROUND)
        }
        binding.openRtspButton.setOnClickListener { showRtspDialog() }
        binding.homeButton.setOnClickListener { navigateHome() }
        binding.newGameButton.setOnClickListener { navigateHome() }
        binding.hitButton.setOnClickListener { recordShot(1) }
        binding.lossButton.setOnClickListener { recordShot(0) }
        binding.exportButton.setOnClickListener { exportGames() }
        binding.importButton.setOnClickListener { confirmImport() }
        binding.resetStatsButton.setOnClickListener { confirmReset() }
    }

    private fun showRtspDialog(initialUri: String? = null) {
        val existing = supportFragmentManager.findFragmentByTag(RtspUriDialogFragment.TAG)
        if (existing == null) {
            RtspUriDialogFragment.newInstance(initialUri)
                .show(supportFragmentManager, RtspUriDialogFragment.TAG)
        }
    }

    override fun onRtspUriSelected(uri: Uri) {
        handleRtspUri(uri)
    }

    private fun setupRoundSelector() {
        binding.roundSelector.removeAllViews()
        for (hits in 0..5) {
            val button = MaterialButton(this).apply {
                text = hits.toString()
                textSize = 20f
                isAllCaps = false
                cornerRadius = dp(20)
                backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.md_sys_color_secondary_container))
                setTextColor(ContextCompat.getColor(context, R.color.md_sys_color_on_secondary_container))
                setOnClickListener { recordRound(hits) }
            }
            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            binding.roundSelector.addView(button, params)
        }
    }

    private fun createNewGame(): MutableGame = MutableGame(
        rounds = MutableList(5) { MutableList<Int?>(5) { null } },
        currentRound = 0,
        currentShot = 0,
        startTimeIso = isoFormatter.format(Date())
    )

    private fun navigateHome() {
        mutableGame = null
        summaryGame = null
        showScreen(Screen.HOME)
    }

    private fun showScreen(screen: Screen) {
        currentScreen = screen
        binding.homeScreen.visibility = if (screen == Screen.HOME) View.VISIBLE else View.GONE
        binding.asYouGoScreen.visibility = if (screen == Screen.AS_YOU_GO) View.VISIBLE else View.GONE
        binding.recordRoundScreen.visibility = if (screen == Screen.RECORD_ROUND) View.VISIBLE else View.GONE
        binding.summaryScreen.visibility = if (screen == Screen.SUMMARY) View.VISIBLE else View.GONE
        binding.homeButton.visibility = if (screen == Screen.HOME) View.GONE else View.VISIBLE

        when (screen) {
            Screen.HOME -> refreshHistory()
            Screen.AS_YOU_GO -> updateAsYouGoStatus()
            Screen.RECORD_ROUND -> updateRecordRoundStatus()
            Screen.SUMMARY -> summaryGame?.let { renderSummary(it) }
        }
    }

    private fun refreshHistory() {
        cachedGames = storage.loadGames()
        binding.gameHistoryContainer.removeAllViews()
        if (cachedGames.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = getString(R.string.no_games_recorded)
                setTextColor(ContextCompat.getColor(context, R.color.md_sys_color_on_surface_variant))
                textSize = 14f
                gravity = Gravity.CENTER
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                setPadding(0, 0, 0, dp(8))
            }
            binding.gameHistoryContainer.addView(emptyText)
            return
        }
        cachedGames.asReversed().forEach { game ->
            val itemBinding = ItemGameHistoryBinding.inflate(layoutInflater, binding.gameHistoryContainer, false)
            itemBinding.historyScore.text = getString(R.string.score_out_of_25, game.totalHits())
            itemBinding.historyDate.text = formatGameDate(game.startTimeIso)
            itemBinding.root.setOnClickListener {
                summaryGame = game
                showScreen(Screen.SUMMARY)
            }
            binding.gameHistoryContainer.addView(itemBinding.root)
        }
    }

    private fun recordShot(result: Int) {
        val game = mutableGame ?: return
        game.rounds[game.currentRound][game.currentShot] = result
        advanceShot(game)
    }

    private fun recordRound(hits: Int) {
        val game = mutableGame ?: return
        for (index in 0 until 5) {
            game.rounds[game.currentRound][index] = if (index < hits) 1 else 0
        }
        if (!advanceRound(game)) {
            updateRecordRoundStatus()
        }
    }

    private fun advanceShot(game: MutableGame) {
        if (game.currentShot < 4) {
            game.currentShot += 1
            updateAsYouGoStatus()
        } else {
            advanceRound(game)
        }
    }

    private fun advanceRound(game: MutableGame): Boolean {
        return if (game.currentRound < 4) {
            game.currentRound += 1
            game.currentShot = 0
            when (currentScreen) {
                Screen.AS_YOU_GO -> updateAsYouGoStatus()
                Screen.RECORD_ROUND -> updateRecordRoundStatus()
                else -> Unit
            }
            false
        } else {
            finishGame(game)
            true
        }
    }

    private fun finishGame(game: MutableGame) {
        val record = game.toGameRecord() ?: return
        storage.saveGame(record)
        summaryGame = record
        mutableGame = null
        refreshHistory()
        showScreen(Screen.SUMMARY)
    }

    private fun updateAsYouGoStatus() {
        val game = mutableGame ?: return
        binding.asYouGoStatus.text = getString(R.string.round_status, game.currentRound + 1, game.currentShot + 1)
    }

    private fun updateRecordRoundStatus() {
        val game = mutableGame ?: return
        binding.recordRoundStatus.text = getString(R.string.record_round_title, game.currentRound + 1)
    }

    private fun renderSummary(game: GameRecord) {
        binding.finalScore.text = getString(R.string.total_score, game.totalHits())
        binding.summaryDetails.removeAllViews()
        game.rounds.forEachIndexed { index, round ->
            val itemBinding = ItemSummaryRoundBinding.inflate(layoutInflater, binding.summaryDetails, false)
            itemBinding.roundTitle.text = getString(R.string.round_label, index + 1)
            itemBinding.roundScore.text = getString(R.string.round_score, round.hitsCount())
            itemBinding.roundShotsContainer.removeAllViews()
            round.shots.forEach { shot ->
                val icon = ImageView(this).apply {
                    val size = dp(32)
                    layoutParams = LinearLayout.LayoutParams(size, size).apply {
                        setMargins(dp(4), 0, dp(4), 0)
                    }
                    setImageResource(if (shot == 1) R.drawable.ic_check_circle_24 else R.drawable.ic_cancel_24)
                    contentDescription = getString(
                        if (shot == 1) R.string.hit_icon_description else R.string.miss_icon_description
                    )
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    setPadding(dp(2), dp(2), dp(2), dp(2))
                }
                itemBinding.roundShotsContainer.addView(icon)
            }
            binding.summaryDetails.addView(itemBinding.root)
        }
    }

    private fun confirmImport() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.confirm_import_title)
            .setMessage(R.string.confirm_import_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.confirm_import_positive) { _, _ ->
                importLauncher.launch(importMimeTypes)
            }
            .show()
    }

    private fun handleImportUri(uri: Uri) {
        try {
            val csv = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            if (csv.isNullOrBlank()) {
                showSnackbar(getString(R.string.import_failed))
                return
            }
            val importedGames = parseGamesFromCsv(csv)
            if (importedGames.isEmpty()) {
                showSnackbar(getString(R.string.import_no_games_found))
                return
            }
            val merged = LinkedHashMap<String, GameRecord>()
            storage.loadGames().forEach { merged[it.startTimeIso] = it }
            importedGames.forEach { merged[it.startTimeIso] = it }
            val mergedList = merged.values.sortedBy { it.startTimeIso }
            storage.saveGames(mergedList)
            refreshHistory()
            showSnackbar(resources.getQuantityString(R.plurals.import_success, importedGames.size, importedGames.size))
        } catch (ignored: Exception) {
            showSnackbar(getString(R.string.import_failed))
        }
    }

    private fun parseGamesFromCsv(csv: String): List<GameRecord> {
        val lines = csv.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (lines.isEmpty()) return emptyList()
        val dataLines = if (lines.first().lowercase(Locale.US).startsWith("game_start_time")) lines.drop(1) else lines
        val grouped = mutableMapOf<String, MutableList<ShotEntry>>()
        dataLines.forEach { line ->
            val parts = line.split(",")
            if (parts.size < 4) return@forEach
            val startTime = parts[0].trim()
            val roundNumber = parts[1].trim().toIntOrNull() ?: return@forEach
            val shotNumber = parts[2].trim().toIntOrNull() ?: return@forEach
            val result = parts[3].trim().toIntOrNull() ?: return@forEach
            if (startTime.isEmpty() || roundNumber !in 1..5 || shotNumber !in 1..5 || result !in 0..1) return@forEach
            grouped.getOrPut(startTime) { mutableListOf() }.add(ShotEntry(roundNumber, shotNumber, result))
        }
        val games = mutableListOf<GameRecord>()
        grouped.forEach { (start, entries) ->
            buildGameFromShots(start, entries)?.let { games += it }
        }
        return games
    }

    private fun buildGameFromShots(startTime: String, shots: List<ShotEntry>): GameRecord? {
        if (shots.size != 25) return null
        val rounds = mutableListOf<RoundRecord>()
        val grouped = shots.groupBy { it.round }
        if (grouped.size != 5) return null
        for (roundIndex in 1..5) {
            val roundShots = grouped[roundIndex] ?: return null
            if (roundShots.size != 5) return null
            val shotValues = arrayOfNulls<Int>(5)
            roundShots.forEach { entry ->
                val shotPosition = entry.shot - 1
                if (shotPosition !in 0..4 || shotValues[shotPosition] != null) return null
                shotValues[shotPosition] = entry.result
            }
            if (shotValues.any { it == null }) return null
            rounds += RoundRecord(shotValues.map { it!! })
        }
        return GameRecord(rounds, startTime)
    }

    private fun confirmReset() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.confirm_reset_title)
            .setMessage(R.string.confirm_reset_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.confirm_reset_positive) { _, _ ->
                resetStats()
            }
            .show()
    }

    private fun resetStats() {
        storage.clearGames()
        cachedGames = emptyList()
        summaryGame = null
        if (currentScreen == Screen.SUMMARY) {
            showScreen(Screen.HOME)
        } else {
            refreshHistory()
        }
        showSnackbar(getString(R.string.reset_success))
    }

    private fun formatGameDate(isoString: String): String {
        return try {
            val date = isoFormatter.parse(isoString)
            if (date != null) historyFormatter.format(date) else isoString
        } catch (ignored: Exception) {
            isoString
        }
    }

    private fun exportGames() {
        val games = storage.loadGames()
        if (games.isEmpty()) {
            showSnackbar(getString(R.string.no_games_to_export))
            return
        }
        val csvRows = buildString {
            append("game_start_time,round_number,shot_number,result\r\n")
            games.forEach { game ->
                game.toCsvRows().forEach { row ->
                    append(row).append("\r\n")
                }
            }
        }
        pendingExportCsv = csvRows
        val fileName = getString(R.string.export_file_name, exportDateFormatter.format(Date()))
        exportLauncher.launch(fileName)
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    private fun handleIncomingIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW) {
            val uri = intent.data
            if (RtspUriDialogFragment.isSupportedUri(uri)) {
                handleRtspUri(uri!!)
            } else if (uri != null) {
                showSnackbar(getString(R.string.rtsp_uri_error_invalid))
            }
        }
    }

    private fun handleRtspUri(uri: Uri) {
        showSnackbar(getString(R.string.rtsp_uri_received, uri.toString()))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private enum class Screen { HOME, AS_YOU_GO, RECORD_ROUND, SUMMARY }

    private data class ShotEntry(val round: Int, val shot: Int, val result: Int)

    private data class MutableGame(
        val rounds: MutableList<MutableList<Int?>>,
        var currentRound: Int,
        var currentShot: Int,
        val startTimeIso: String
    ) {
        fun toGameRecord(): GameRecord? {
            val roundRecords = rounds.map { roundShots ->
                if (roundShots.any { it == null }) return null
                RoundRecord(roundShots.map { it ?: 0 })
            }
            return GameRecord(roundRecords, startTimeIso)
        }
    }
}
