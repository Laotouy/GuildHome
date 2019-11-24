package com.github.bryanser.guildhome.service

import com.github.bryanser.guildhome.Channel
import com.github.bryanser.guildhome.StringManager
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.messaging.PluginMessageListener


class BukkitListener : PluginMessageListener {
    override fun onPluginMessageReceived(channel: String, player: Player?, message: ByteArray) {
        if (Service.DEBUG)
            Bukkit.getLogger().info("接收信道: $channel")
        val str = String(message)
        val json = StringManager.fromJson(str)
        if (Service.DEBUG) {
            Bukkit.getLogger().info("接收信道信息: $str")
        }
        val service = json["Service"] as String
        val ser = Service.services[service] ?: return
        if (ser.bukkitSend) {
            throw IllegalStateException("这个数据包只能由Bungee接收")
        }
        ser.onReceive(json)

    }
}