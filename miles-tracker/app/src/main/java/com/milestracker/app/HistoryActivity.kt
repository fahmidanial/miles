package com.milestracker.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.milestracker.app.databinding.ActivityHistoryBinding
import kotlinx.coroutines.launch

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var repository: LocationRepository
    private lateinit var adapter: HistoryAdapter

    private var allTrips: List<LocationRepository.Route> = emptyList()
    private var displayedTrips: List<LocationRepository.Route> = emptyList()

    private val createCsvLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let {
            lifecycleScope.launch {
                try {
                    val csvContent = generateBulkCsv()
                     contentResolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(csvContent.toByteArray())
                    }
                    Toast.makeText(this@HistoryActivity, "Export saved successfully", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this@HistoryActivity, "Failed to save file", Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = LocationRepository(this)

        setupToolbar()
        setupChips()
        setupRecyclerView()
        setupExportFab()
        loadHistory()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel) // Using standard Close icon
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }
    
    // ... setupChips and filtering logic ...
    private fun setupChips() {
        binding.filterChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            
            val chipId = checkedIds.first()
            filterTrips(chipId)
        }
    }
    
    private fun filterTrips(chipId: Int) {
        val calendar = java.util.Calendar.getInstance()
        val now = System.currentTimeMillis()
        
        // Reset calendar to start of today
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val startOfToday = calendar.timeInMillis
        
        displayedTrips = when (chipId) {
            binding.chipToday.id -> {
               allTrips.filter { it.startTimestamp >= startOfToday }
            }
            binding.chipWeek.id -> {
                calendar.set(java.util.Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                val startOfWeek = calendar.timeInMillis
                allTrips.filter { it.startTimestamp >= startOfWeek }
            }
            binding.chipMonth.id -> {
                calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
                val startOfMonth = calendar.timeInMillis
                 allTrips.filter { it.startTimestamp >= startOfMonth }
            }
            binding.chipYear.id -> {
                calendar.set(java.util.Calendar.DAY_OF_YEAR, 1)
                val startOfYear = calendar.timeInMillis
                 allTrips.filter { it.startTimestamp >= startOfYear }
            }
            else -> allTrips
        }
        
        adapter.updateData(displayedTrips)
        updateEmptyView()
    }

    private fun setupRecyclerView() {
        adapter = HistoryAdapter(emptyList()) { trip ->
            exportTrip(trip)
        }
        binding.historyRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.historyRecyclerView.adapter = adapter
    }
    
    private fun setupExportFab() {
        binding.exportFab.setOnClickListener {
            if (displayedTrips.isNotEmpty()) {
                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                val dateStr = dateFormat.format(java.util.Date())
                val fileName = "miles_history_$dateStr.csv"
                createCsvLauncher.launch(fileName)
            } else {
                 Toast.makeText(this, "No trips to export", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadHistory() {
        binding.progressBar.visibility = View.VISIBLE
        binding.emptyView.visibility = View.GONE
        
        lifecycleScope.launch {
            try {
                allTrips = repository.getTripHistory()
                // Default to All or trigger current filter
                val currentChipId = binding.filterChipGroup.checkedChipId
                if (currentChipId != View.NO_ID) {
                    filterTrips(currentChipId)
                } else {
                     displayedTrips = allTrips
                     adapter.updateData(displayedTrips)
                     updateEmptyView()
                }
            } catch (e: Exception) {
                Toast.makeText(this@HistoryActivity, "Failed to load history", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }
    
    private fun updateEmptyView() {
        if (displayedTrips.isEmpty()) {
            binding.emptyView.visibility = View.VISIBLE
        } else {
            binding.emptyView.visibility = View.GONE
        }
    }
    
    private suspend fun generateBulkCsv(): String {
        val sb = StringBuilder()
        displayedTrips.forEach { trip ->
            val line = repository.exportRouteToCsv(trip)
            if (line != null) {
                sb.append(line).append("\n")
            }
        }
        return sb.toString()
    }

    private fun exportTrip(trip: LocationRepository.Route) {
        lifecycleScope.launch {
            val csvData = repository.exportRouteToCsv(trip)
            if (csvData != null) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Miles Tracker Route Export")
                    putExtra(Intent.EXTRA_TEXT, csvData)
                }
                startActivity(Intent.createChooser(shareIntent, "Export Trip CSV"))
            } else {
                Toast.makeText(this@HistoryActivity, "Failed to generate CSV", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
