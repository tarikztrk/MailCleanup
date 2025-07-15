package com.tarik.mailcleanup.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tarik.mailcleanup.data.Subscription
import com.tarik.mailcleanup.databinding.ItemSubscriptionBinding

class SubscriptionAdapter(
    private val onUnsubscribeClicked: (Subscription) -> Unit,
    private val onKeepClicked: (Subscription) -> Unit
) : ListAdapter<Subscription, SubscriptionAdapter.SubscriptionViewHolder>(SubscriptionDiffCallback()) {

    private var processingEmail: String? = null

    inner class SubscriptionViewHolder(val binding: ItemSubscriptionBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubscriptionViewHolder {
        val binding = ItemSubscriptionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SubscriptionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SubscriptionViewHolder, position: Int) {
        val subscription = getItem(position) // getItem() metodunu kullanıyoruz
        holder.binding.senderNameTextView.text = subscription.senderName
        holder.binding.senderEmailTextView.text = subscription.senderEmail
        holder.binding.emailCountChip.text = subscription.emailCount.toString()
        val initial = subscription.senderName.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
        holder.binding.senderIconTextView.text = initial

        if (subscription.senderEmail == processingEmail) {
            // YENİ MANTIK: Tüm aksiyon alanını gizle
            holder.binding.actionLayout.visibility = View.INVISIBLE
            holder.binding.itemProgressBar.visibility = View.VISIBLE
        } else {
            // Normal durum
            holder.binding.actionLayout.visibility = View.VISIBLE
            holder.binding.itemProgressBar.visibility = View.GONE
        }

        holder.binding.unsubscribeButton.setOnClickListener {
            onUnsubscribeClicked(subscription)
        }
        holder.binding.keepButton.setOnClickListener {
            onKeepClicked(subscription)
        }
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

// DiffUtil, iki liste arasındaki farkı hesaplar.
class SubscriptionDiffCallback : DiffUtil.ItemCallback<Subscription>() {
    override fun areItemsTheSame(oldItem: Subscription, newItem: Subscription): Boolean {
        // Öğelerin kimlikleri aynı mı? (Örn: Benzersiz ID)
        return oldItem.senderEmail == newItem.senderEmail
    }

    override fun areContentsTheSame(oldItem: Subscription, newItem: Subscription): Boolean {
        // Öğelerin içeriği (görsel olarak etkileyen her şey) aynı mı?
        return oldItem == newItem
    }
}