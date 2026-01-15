package com.milestracker.app

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.milestracker.app.databinding.ItemTripHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.core.content.ContextCompat
import android.graphics.PorterDuff
import android.content.res.ColorStateList

class HistoryAdapter(
    private var trips: List<LocationRepository.Route>,
    private val onExportClick: (LocationRepository.Route) -> Unit,
    private val onDeleteClick: (LocationRepository.Route) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemTripHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTripHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val trip = trips[position]
        
        // Date Format
        val date = Date(trip.startTimestamp)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd • h:mm a", Locale.getDefault())
        holder.binding.dateText.text = dateFormat.format(date)

        // Calculate Stats (Recalculating here for display, ideally should be stored in Route object or helper)
        var totalDistanceMeters = 0.0
        val points = trip.points
        if (points.isNotEmpty()) {
             for (i in 0 until points.size - 1) {
                totalDistanceMeters += calculateDistance(
                    points[i].latitude, points[i].longitude,
                    points[i+1].latitude, points[i+1].longitude
                )
            }
            
            val durationMillis = points.last().timestamp - points.first().timestamp
            val durationHours = durationMillis / (1000.0 * 60.0 * 60.0)
            
            holder.binding.distanceText.text = "%.2f km".format(totalDistanceMeters / 1000.0)
            holder.binding.durationText.text = "%.1f hr".format(durationHours)
        } else {
             holder.binding.distanceText.text = "0.00 km"
             holder.binding.durationText.text = "0.0 hr"
        }

        holder.binding.exportButton.setOnClickListener {
            onExportClick(trip)
        }
        
        holder.itemView.setOnLongClickListener {
            onDeleteClick(trip)
            true
        }
        
        // Visual indicator for classification
        // Visual indicator for classification
        val context = holder.itemView.context
        val (colorRes, bgRes, label) = when (trip.classification) {
            "Personal" -> Triple(R.color.type_personal, R.color.type_personal_bg, "Personal")
            "Work" -> Triple(R.color.type_work, R.color.type_work_bg, "Work")
            else -> Triple(R.color.type_unclassified, R.color.type_unclassified_bg, "Unclassified")
        }
        
        val color = ContextCompat.getColor(context, colorRes)
        val bgColor = ContextCompat.getColor(context, bgRes)
        
        holder.binding.statusIndicator.setBackgroundColor(color)
        holder.binding.classificationTag.text = label
        holder.binding.classificationTag.setTextColor(color)
        holder.binding.classificationTag.background.setTint(bgColor)
        
        // Ensure card background is consistent
        holder.binding.cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.card_background))
    }

    override fun getItemCount() = trips.size

    fun updateData(newTrips: List<LocationRepository.Route>) {
        trips = newTrips
        notifyDataSetChanged()
    }

    fun updateItem(position: Int, trip: LocationRepository.Route) {
        val mutableList = trips.toMutableList()
        mutableList[position] = trip
        trips = mutableList
        notifyItemChanged(position)
    }

    fun getTripAt(position: Int): LocationRepository.Route {
        return trips[position]
    }
    
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371e3 // Earth radius in meters
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaPhi = Math.toRadians(lat2 - lat1)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2) +
                Math.cos(phi1) * Math.cos(phi2) *
                Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return r * c
    }
}
