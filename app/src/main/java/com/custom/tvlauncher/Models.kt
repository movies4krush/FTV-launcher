package com.custom.tvlauncher

data class CustomRow(
    val id: String,
    var title: String,
    val packages: MutableList<String> = mutableListOf(),
    val isChannelRow: Boolean = false,
    val channelItems: MutableList<ChannelCard> = mutableListOf()
)

data class ChannelCard(
    val title: String,
    val imageUrl: String?,
    val intentUri: String?,
    val packageName: String
)
