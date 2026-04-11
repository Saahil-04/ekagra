package com.ekagra.app.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ekagra.app.databinding.ItemSettingsActionBinding
import com.ekagra.app.databinding.ItemSettingsHeaderBinding
import com.ekagra.app.model.SettingsItem

class SettingsAdapter(
    private val onActionClick: (SettingsItem.ActionRow) -> Unit
) : ListAdapter<SettingsItem, RecyclerView.ViewHolder>(DiffCallback()) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is SettingsItem.Header    -> TYPE_HEADER
        is SettingsItem.ActionRow -> TYPE_ACTION
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(
                ItemSettingsHeaderBinding.inflate(inflater, parent, false)
            )
            else -> ActionViewHolder(
                ItemSettingsActionBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is SettingsItem.Header    -> (holder as HeaderViewHolder).bind(item)
            is SettingsItem.ActionRow -> (holder as ActionViewHolder).bind(item)
        }
    }

    // ── ViewHolders ──────────────────────────────────────────────────────────

    inner class HeaderViewHolder(
        private val binding: ItemSettingsHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SettingsItem.Header) {
            binding.tvSectionHeader.text = item.title
        }
    }

    inner class ActionViewHolder(
        private val binding: ItemSettingsActionBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SettingsItem.ActionRow) {
            binding.tvTitle.text = item.title
            binding.tvSubtitle.text = item.subtitle
            binding.tvValue.text = item.value ?: ""
            binding.root.setOnClickListener { onActionClick(item) }
        }
    }

    // ── Diff ─────────────────────────────────────────────────────────────────

    private class DiffCallback : DiffUtil.ItemCallback<SettingsItem>() {
        override fun areItemsTheSame(old: SettingsItem, new: SettingsItem): Boolean =
            when {
                old is SettingsItem.Header    && new is SettingsItem.Header    -> old.title == new.title
                old is SettingsItem.ActionRow && new is SettingsItem.ActionRow -> old.id == new.id
                else -> false
            }

        override fun areContentsTheSame(old: SettingsItem, new: SettingsItem): Boolean =
            old == new
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ACTION = 1
    }
}