package com.tarik.mailcleanup.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tarik.mailcleanup.R
import com.tarik.mailcleanup.data.Subscription
import com.tarik.mailcleanup.databinding.ItemSubscriptionBinding

class SubscriptionAdapter(
    private val clickListener: (Subscription) -> Unit,
    private val longClickListener: (Subscription) -> Boolean,
    private val isSelectionMode: () -> Boolean,
    private val isSelected: (Subscription) -> Boolean,
    private val onUnsubscribeClicked: ((Subscription) -> Unit)? = null,
    private val onKeepClicked: ((Subscription) -> Unit)? = null
) : ListAdapter<Subscription, SubscriptionAdapter.SubscriptionViewHolder>(SubscriptionDiffCallback()) {

    private var processingEmail: String? = null

    inner class SubscriptionViewHolder(val binding: ItemSubscriptionBinding) :
        RecyclerView.ViewHolder(binding.root) {
        
        fun bind(subscription: Subscription) {
            binding.senderNameTextView.text = subscription.senderName
            binding.senderEmailTextView.text = subscription.senderEmail
            binding.emailCountChip.text = subscription.emailCount.toString()
            val initial = subscription.senderName.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
            binding.senderIconTextView.text = initial
            
            // --- GÖRSEL GERİ BİLDİRİM ---
            if (isSelected(subscription)) {
                binding.root.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.selected_item_background))
            } else {
                binding.root.setBackgroundColor(Color.TRANSPARENT)
            }
            // --- BİTTİ ---

            // Processing durumu yönetimi
            if (subscription.senderEmail == processingEmail) {
                // YENİ MANTIK: Tüm aksiyon alanını gizle
                binding.actionLayout.visibility = View.INVISIBLE
                binding.itemProgressBar.visibility = View.VISIBLE
            } else {
                // Normal durum
                binding.actionLayout.visibility = View.VISIBLE
                binding.itemProgressBar.visibility = View.GONE
            }

            // Tıklama mantığını yönet
            itemView.setOnClickListener {
                clickListener(subscription)
            }
            itemView.setOnLongClickListener {
                longClickListener(subscription)
            }

            // Buton tıklamalarını sadece normal modda aktif et
            if (!isSelectionMode()) {
                binding.unsubscribeButton.setOnClickListener {
                    onUnsubscribeClicked?.invoke(subscription)
                }
                binding.keepButton.setOnClickListener {
                    onKeepClicked?.invoke(subscription)
                }
            } else {
                binding.unsubscribeButton.setOnClickListener(null)
                binding.keepButton.setOnClickListener(null)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubscriptionViewHolder {
        val binding = ItemSubscriptionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SubscriptionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SubscriptionViewHolder, position: Int) {
        val subscription = getItem(position)
        holder.bind(subscription)
    }

    fun setProcessingState(email: String?) {
        val previousProcessingEmail = processingEmail
        processingEmail = email
        
        // Sadece değişen öğeleri güncellemek için daha verimli bir yol
        previousProcessingEmail?.let { findPositionByEmail(it)?.let { pos -> notifyItemChanged(pos) } }
        email?.let { findPositionByEmail(it)?.let { pos -> notifyItemChanged(pos) } }
    }
    
    private fun findPositionByEmail(email: String): Int? {
        for (i in 0 until itemCount) {
            if (getItem(i).senderEmail == email) {
                return i
            }
        }
        return null
    }
}

class SubscriptionDiffCallback : DiffUtil.ItemCallback<Subscription>() {
    override fun areItemsTheSame(oldItem: Subscription, newItem: Subscription): Boolean {
        return oldItem.senderEmail == newItem.senderEmail
    }

    override fun areContentsTheSame(oldItem: Subscription, newItem: Subscription): Boolean {
        // Seçim durumunu da karşılaştırmaya ekleyebiliriz ama şimdilik bu yeterli.
        return oldItem == newItem
    }
}