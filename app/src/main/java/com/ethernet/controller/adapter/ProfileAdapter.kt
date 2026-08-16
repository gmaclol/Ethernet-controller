package com.ethernet.controller.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ethernet.controller.databinding.ItemProfileBinding
import com.ethernet.controller.model.EthernetProfile

class ProfileAdapter(
    private var profiles: List<EthernetProfile>,
    private val onApplyClick: (EthernetProfile) -> Unit,
    private val onEditClick: (EthernetProfile) -> Unit,
    private val onDeleteClick: (EthernetProfile) -> Unit
) : RecyclerView.Adapter<ProfileAdapter.ProfileViewHolder>() {

    fun updateData(newProfiles: List<EthernetProfile>) {
        profiles = newProfiles
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileViewHolder {
        val binding = ItemProfileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ProfileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) {
        holder.bind(profiles[position])
    }

    override fun getItemCount(): Int = profiles.size

    inner class ProfileViewHolder(private val binding: ItemProfileBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(profile: EthernetProfile) {
            binding.tvProfileName.text = profile.name
            binding.tvProfileType.text = if (profile.isDhcp) "DHCP" else "IP STATICO"

            if (profile.isDhcp) {
                binding.layoutStaticDetails.visibility = View.GONE
            } else {
                binding.layoutStaticDetails.visibility = View.VISIBLE
                binding.tvProfileIp.text = profile.ip
                binding.tvProfileNetmask.text = profile.netmask
                binding.tvProfileGateway.text = profile.gateway
                binding.tvProfileDns.text = profile.dns
            }

            binding.btnEditProfile.setOnClickListener {
                onEditClick(profile)
            }

            binding.btnDeleteProfile.visibility = if (profile.isDefault) View.GONE else View.VISIBLE
            binding.btnDeleteProfile.setOnClickListener {
                onDeleteClick(profile)
            }

            binding.btnApplyProfile.setOnClickListener {
                onApplyClick(profile)
            }
        }
    }
}
