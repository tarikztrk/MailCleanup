package com.tarik.mailcleanup.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tarik.mailcleanup.R
import com.tarik.mailcleanup.databinding.ItemSearchResultBinding
import com.tarik.mailcleanup.domain.model.Subscription

class SearchResultAdapter(
    private val onViewClicked: (Subscription) -> Unit
) : PagingDataAdapter<Subscription, SearchResultAdapter.SearchResultViewHolder>(SubscriptionDiffCallback()) {

    inner class SearchResultViewHolder(
        private val binding: ItemSearchResultBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Subscription) {
            binding.iconTextView.text = item.senderName.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
            binding.titleTextView.text = item.senderName
            binding.subtitleTextView.text = buildSubtitle(item)
            binding.verifiedDot.visibility = if (item.senderName.contains("brew", ignoreCase = true)) android.view.View.VISIBLE else android.view.View.GONE

            val category = buildCategory(item)
            val frequency = buildFrequency(item)
            binding.categoryTagTextView.text = category
            binding.frequencyTagTextView.text = frequency

            if (category == "CREATIVE") {
                binding.categoryTagTextView.background = ContextCompat.getDrawable(itemView.context, R.drawable.bg_tag_red_soft)
                binding.categoryTagTextView.setTextColor(Color.parseColor("#A31621"))
            } else {
                binding.categoryTagTextView.background = ContextCompat.getDrawable(itemView.context, R.drawable.bg_tag_blue_soft)
                binding.categoryTagTextView.setTextColor(ContextCompat.getColor(itemView.context, R.color.brand_primary))
            }

            binding.viewButton.setOnClickListener { onViewClicked(item) }
            binding.root.setOnClickListener { onViewClicked(item) }
        }

        private fun buildSubtitle(item: Subscription): String {
            val domain = item.senderEmail.substringAfter("@", item.senderEmail)
            return "A curated stream from $domain"
        }

        private fun buildCategory(item: Subscription): String {
            val text = "${item.senderName} ${item.senderEmail}".lowercase()
            return when {
                text.contains("tech") || text.contains("byte") -> "TECH"
                text.contains("creative") || text.contains("art") || text.contains("design") -> "CREATIVE"
                text.contains("biz") || text.contains("finance") || text.contains("market") || text.contains("brew") -> "BUSINESS"
                else -> "DAILY"
            }
        }

        private fun buildFrequency(item: Subscription): String {
            return when {
                item.emailCount >= 5 -> "DAILY"
                item.emailCount >= 2 -> "WEEKLY"
                else -> "MONTHLY"
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchResultViewHolder {
        val binding = ItemSearchResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SearchResultViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SearchResultViewHolder, position: Int) {
        getItem(position)?.let(holder::bind)
    }
}
