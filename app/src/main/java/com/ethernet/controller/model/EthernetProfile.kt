package com.ethernet.controller.model

data class EthernetProfile(
    val id: String,
    val name: String,
    val isDhcp: Boolean,
    val ip: String = "",
    val netmask: String = "255.255.255.0",
    val gateway: String = "",
    val dns: String = "8.8.8.8",
    val isDefault: Boolean = false
)
