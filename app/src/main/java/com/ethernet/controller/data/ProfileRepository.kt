package com.ethernet.controller.data

import android.content.Context
import android.content.SharedPreferences
import com.ethernet.controller.model.EthernetProfile
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ProfileRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "ethernet_profiles_prefs"
        private const val KEY_PROFILES = "profiles_list"
        private const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"

        val DEFAULT_PROFILE_OF = EthernetProfile(
            id = "profile_ont_of",
            name = "ONT OF",
            isDhcp = false,
            ip = "192.168.1.10",
            netmask = "255.255.255.0",
            gateway = "192.168.1.1",
            dns = "8.8.8.8",
            isDefault = true
        )

        val DEFAULT_PROFILE_SKY = EthernetProfile(
            id = "profile_ont_sky",
            name = "ONT SKY",
            isDhcp = false,
            ip = "192.168.100.10",
            netmask = "255.255.255.0",
            gateway = "192.168.100.1",
            dns = "8.8.8.8",
            isDefault = true
        )

        val DEFAULT_PROFILE_DHCP = EthernetProfile(
            id = "profile_dhcp",
            name = "DHCP (Auto)",
            isDhcp = true,
            isDefault = true
        )
    }

    init {
        if (!prefs.contains(KEY_PROFILES)) {
            saveProfiles(listOf(DEFAULT_PROFILE_OF, DEFAULT_PROFILE_SKY, DEFAULT_PROFILE_DHCP))
        }
    }

    fun getProfiles(): List<EthernetProfile> {
        val json = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        val type = object : TypeToken<List<EthernetProfile>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getProfileById(id: String): EthernetProfile? {
        return getProfiles().find { it.id == id }
    }

    fun saveProfiles(profiles: List<EthernetProfile>) {
        val json = gson.toJson(profiles)
        prefs.edit().putString(KEY_PROFILES, json).apply()
    }

    fun addOrUpdateProfile(profile: EthernetProfile) {
        val current = getProfiles().toMutableList()
        val index = current.indexOfFirst { it.id == profile.id }
        if (index >= 0) {
            current[index] = profile
        } else {
            current.add(profile)
        }
        saveProfiles(current)
    }

    fun deleteProfile(id: String) {
        val current = getProfiles().filterNot { it.id == id }
        saveProfiles(current)
    }

    fun getActiveProfileId(): String? {
        return prefs.getString(KEY_ACTIVE_PROFILE_ID, null)
    }

    fun setActiveProfileId(id: String) {
        prefs.edit().putString(KEY_ACTIVE_PROFILE_ID, id).apply()
    }
}
