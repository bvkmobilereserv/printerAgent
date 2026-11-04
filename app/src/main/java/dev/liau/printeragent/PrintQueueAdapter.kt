package dev.liau.printeragent

import android.R
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.liau.printeragent.databinding.RowItemQueueBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PrintQueueAdapter(
    private val onItemClick: ((PrintEntity) -> Unit)? = null,
) : ListAdapter<PrintEntity, PrintQueueAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(
        private val binding: RowItemQueueBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())

        fun bind(item: PrintEntity) {
            binding.titleQueue.text = item.fileName
            binding.timestampQueue.text = dateFormat.format(Date(item.createdAt))
            binding.statusQueue.text = item.status

            val context = binding.root.context
            val statusColor = when (item.status.uppercase(Locale.getDefault())) {
                "SUCCESS" -> ContextCompat.getColor(context, R.color.holo_green_dark)
                "PRINTING" -> ContextCompat.getColor(context, R.color.holo_blue_dark)
                "FAILED" -> ContextCompat.getColor(context, R.color.holo_red_dark)
                else -> ContextCompat.getColor(context, R.color.holo_orange_dark) // PENDING
            }
            binding.statusQueue.setTextColor(statusColor)

            binding.root.setOnClickListener {
                onItemClick?.invoke(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = RowItemQueueBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<PrintEntity>() {
            override fun areItemsTheSame(oldItem: PrintEntity, newItem: PrintEntity): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: PrintEntity, newItem: PrintEntity): Boolean {
                return oldItem == newItem
            }
        }
    }
}
