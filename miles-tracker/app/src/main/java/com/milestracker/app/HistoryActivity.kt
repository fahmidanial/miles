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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = LocationRepository(this)

        setupToolbar()
        setupRecyclerView()
        loadHistory()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel) // Using standard Close icon
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = HistoryAdapter(emptyList()) { trip ->
            exportTrip(trip)
        }
        binding.historyRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.historyRecyclerView.adapter = adapter
    }

    private fun loadHistory() {
        binding.progressBar.visibility = View.VISIBLE
        binding.emptyView.visibility = View.GONE
        
        lifecycleScope.launch {
            try {
                val trips = repository.getTripHistory()
                adapter.updateData(trips)
                
                if (trips.isEmpty()) {
                    binding.emptyView.visibility = View.VISIBLE
                } else {
                    binding.emptyView.visibility = View.GONE
                }
            } catch (e: Exception) {
                Toast.makeText(this@HistoryActivity, "Failed to load history", Toast.LENGTH_SHORT).show()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
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
