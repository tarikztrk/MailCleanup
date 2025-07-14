package com.tarik.mailcleanup.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tarik.mailcleanup.data.Subscription
import com.tarik.mailcleanup.databinding.ItemSubscriptionBinding

class SubscriptionAdapter(
    private var subscriptions: List<Subscription>,
    private val onUnsubscribeClicked: (Subscription) -> Unit
) : RecyclerView.Adapter<SubscriptionAdapter.SubscriptionViewHolder>() {

    // Yükleniyor durumundaki e-postanın adresini tutacak değişken
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

    override fun getItemCount(): Int = subscriptions.size

    override fun onBindViewHolder(holder: SubscriptionViewHolder, position: Int) {
        val subscription = subscriptions[position]
        holder.binding.senderNameTextView.text = subscription.senderName
        holder.binding.senderEmailTextView.text = subscription.senderEmail
        
        // --- YENİ: İKONU AYARLA ---
        // Gönderen adının ilk harfini al, boş değilse.
        val initial = subscription.senderName.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
        holder.binding.senderIconTextView.text = initial
        // ------------------------

        // --- YENİ MANTIK: Yüklenme Durumunu Kontrol Et ---
        if (subscription.senderEmail == processingEmail) {
            // Bu öğe şu an işleniyor
            holder.binding.unsubscribeButton.visibility = View.INVISIBLE // Butonu tamamen yok etmek yerine görünmez yap
            holder.binding.itemProgressBar.visibility = View.VISIBLE
        } else {
            // Normal durum
            holder.binding.unsubscribeButton.visibility = View.VISIBLE
            holder.binding.itemProgressBar.visibility = View.GONE
        }
        // ---------------------------------------------

        holder.binding.unsubscribeButton.setOnClickListener {
            onUnsubscribeClicked(subscription)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newSubscriptions: List<Subscription>) {
        this.subscriptions = newSubscriptions
        // İşlem bittiğinde yükleniyor durumunu sıfırla
        this.processingEmail = null
        notifyDataSetChanged()
    }

    // --- YENİ FONKSİYON: Belirli bir öğeyi yükleniyor olarak işaretle ---
    fun setProcessingState(email: String) {
        this.processingEmail = email
        // Değişikliği yansıtmak için listeyi güncelle
        notifyDataSetChanged()
    }

    // --- YENİ FONKSİYON: Mevcut listeyi döndür ---
    fun getSubscriptions(): List<Subscription> {
        return this.subscriptions
    }
}