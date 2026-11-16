package cl.inacap.riegoiot

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import cl.inacap.riegoiot.databinding.ItemActionHistoryBinding

class ActionHistoryAdapter(
    private var items: List<ActionLog> = emptyList()
) : RecyclerView.Adapter<ActionHistoryAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemActionHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemActionHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.tvActionText.text = item.toFormattedText()
        holder.binding.tvActionTimestamp.text = item.toFormattedTimestamp()
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<ActionLog>) {
        items = newItems
        notifyDataSetChanged()
    }
}
