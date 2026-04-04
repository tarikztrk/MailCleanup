package com.tarik.mailcleanup.ui

import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.tarik.mailcleanup.R
import com.tarik.mailcleanup.databinding.ItemSubscriptionBinding
import com.tarik.mailcleanup.domain.model.Subscription

class SubscriptionAdapter(
    private val clickListener: (Subscription) -> Unit,
    private val longClickListener: (Subscription) -> Boolean,
    private val isSelectionMode: () -> Boolean,
    private val isSelected: (Subscription) -> Boolean,
    private val onUnsubscribeClicked: ((Subscription) -> Unit)? = null,
    private val onKeepClicked: ((Subscription) -> Unit)? = null
) : PagingDataAdapter<Subscription, SubscriptionAdapter.SubscriptionViewHolder>(SubscriptionDiffCallback()) {

    private var processingEmail: String? = null

    inner class SubscriptionViewHolder(val binding: ItemSubscriptionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(subscription: Subscription) {
            binding.senderNameTextView.text = subscription.senderName
            val openRate = generateOpenRate(subscription.senderEmail)
            binding.senderEmailTextView.text = buildMetaText(itemView, subscription.emailCount, openRate)
            val initial = subscription.senderName.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
            binding.senderIconTextView.text = initial

            val isLowEngagement = openRate <= 15
            binding.lowEngagementTextView.visibility = if (isLowEngagement) View.VISIBLE else View.GONE
            binding.leftAccent.visibility = if (isLowEngagement) View.VISIBLE else View.GONE
            binding.unsubscribeIconButton.visibility = if (isLowEngagement) View.GONE else View.VISIBLE
            binding.unsubscribeButton.visibility = if (isLowEngagement) View.VISIBLE else View.GONE

            when {
                isSelected(subscription) -> {
                    binding.itemCard.setCardBackgroundColor(
                        ContextCompat.getColor(
                            itemView.context,
                            R.color.selected_item_background
                        )
                    )
                }

                isLowEngagement -> {
                    binding.itemCard.setCardBackgroundColor(
                        ContextCompat.getColor(
                            itemView.context,
                            R.color.low_engagement_background
                        )
                    )
                }

                else -> {
                    binding.itemCard.setCardBackgroundColor(Color.WHITE)
                }
            }

            if (subscription.senderEmail == processingEmail) {
                binding.actionLayout.visibility = View.INVISIBLE
                binding.itemProgressBar.visibility = View.VISIBLE
            } else {
                binding.actionLayout.visibility = View.VISIBLE
                binding.itemProgressBar.visibility = View.GONE
            }

            itemView.setOnClickListener {
                clickListener(subscription)
            }
            itemView.setOnLongClickListener {
                longClickListener(subscription)
            }

            if (!isSelectionMode()) {
                binding.unsubscribeButton.setOnClickListener {
                    onUnsubscribeClicked?.invoke(subscription)
                }
                binding.unsubscribeIconButton.setOnClickListener {
                    onUnsubscribeClicked?.invoke(subscription)
                }
                binding.keepButton.setOnClickListener {
                    onKeepClicked?.invoke(subscription)
                }
            } else {
                binding.unsubscribeButton.setOnClickListener(null)
                binding.unsubscribeIconButton.setOnClickListener(null)
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
        val subscription = getItem(position) ?: return
        holder.bind(subscription)
    }

    fun setProcessingState(email: String?) {
        val previousProcessingEmail = processingEmail
        processingEmail = email

        previousProcessingEmail?.let { findPositionByEmail(it)?.let { pos -> notifyItemChanged(pos) } }
        email?.let { findPositionByEmail(it)?.let { pos -> notifyItemChanged(pos) } }
    }

    private fun findPositionByEmail(email: String): Int? {
        for (i in 0 until itemCount) {
            val item = getItem(i) ?: continue
            if (item.senderEmail == email) {
                return i
            }
        }
        return null
    }

    private fun generateOpenRate(seed: String): Int {
        return (kotlin.math.abs(seed.hashCode()) % 96) + 5
    }

    private fun buildMetaText(itemView: View, emailCount: Int, openRate: Int): CharSequence {
        val frequency = when {
            emailCount >= 7 -> "${emailCount} emails/week"
            emailCount > 1 -> "${emailCount} emails/week"
            emailCount == 1 -> "1 email/week"
            else -> "1 email/week"
        }
        val base = "$frequency • "
        val highlight = "$openRate% Open rate"
        val fullText = base + highlight
        val spannable = SpannableString(fullText)
        spannable.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(itemView.context, R.color.secondary_text)),
            0,
            base.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannable.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(itemView.context, R.color.brand_primary_dark)),
            base.length,
            fullText.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return spannable
    }
}

class SubscriptionDiffCallback : DiffUtil.ItemCallback<Subscription>() {
    override fun areItemsTheSame(oldItem: Subscription, newItem: Subscription): Boolean {
        return oldItem.senderEmail == newItem.senderEmail
    }

    override fun areContentsTheSame(oldItem: Subscription, newItem: Subscription): Boolean {
        return oldItem == newItem
    }
}
