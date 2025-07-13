package com.tarik.mailcleanup.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tarik.mailcleanup.data.Subscription
import com.tarik.mailcleanup.databinding.ItemSubscriptionBinding

class SubscriptionAdapter(
    private var subscriptions: List<Subscription>
) : RecyclerView.Adapter<SubscriptionAdapter.SubscriptionViewHolder>() {

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

    override fun getItemCount(): Int = subscriptions.size

    override fun onBindViewHolder(holder: SubscriptionViewHolder, position: Int) {
        val subscription = subscriptions[position]
        holder.binding.senderNameTextView.text = subscription.senderName
        holder.binding.senderEmailTextView.text = subscription.senderEmail
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newSubscriptions: List<Subscription>) {
        this.subscriptions = newSubscriptions
        notifyDataSetChanged()
    }
}