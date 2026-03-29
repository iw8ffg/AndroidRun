package com.androidrun.autostartblocker.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.androidrun.autostartblocker.databinding.ItemAppBinding
import com.androidrun.autostartblocker.model.AppInfo

class AppListAdapter(
    private val onToggle: OnAppToggleListener
) : ListAdapter<AppInfo, AppListAdapter.AppViewHolder>(AppDiffCallback()) {

    fun interface OnAppToggleListener {
        fun onAppToggled(packageName: String, isBlocked: Boolean)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AppViewHolder(
        private val binding: ItemAppBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(appInfo: AppInfo) {
            binding.apply {
                ivAppIcon.setImageDrawable(appInfo.icon)
                tvAppName.text = appInfo.appName
                tvPackageName.text = appInfo.packageName

                // Remove listener before setting checked to avoid triggering callback
                cbBlocked.setOnCheckedChangeListener(null)
                cbBlocked.isChecked = appInfo.isBlocked

                cbBlocked.setOnCheckedChangeListener { _, isChecked ->
                    onToggle.onAppToggled(appInfo.packageName, isChecked)
                }

                root.setOnClickListener {
                    cbBlocked.isChecked = !cbBlocked.isChecked
                }
            }
        }
    }

    class AppDiffCallback : DiffUtil.ItemCallback<AppInfo>() {
        override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem.packageName == newItem.packageName &&
                oldItem.appName == newItem.appName &&
                oldItem.isBlocked == newItem.isBlocked
        }
    }
}
