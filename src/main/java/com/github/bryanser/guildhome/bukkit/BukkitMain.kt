package com.github.bryanser.guildhome.bukkit

import com.github.bryanser.guildhome.Channel
import com.github.bryanser.guildhome.database.DatabaseHandler
import com.github.bryanser.guildhome.service.BukkitListener
import com.github.bryanser.guildhome.service.Service
import com.zaxxer.hikari.HikariConfig
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class BukkitMain : JavaPlugin() {

    override fun onEnable() {
        Plugin = this
        Service.bukkit = true
        connectSQL()
        register()
    }

    fun register() {
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, Channel.BUKKIT2BUNGEE)
        Bukkit.getMessenger().registerIncomingPluginChannel(this, Channel.BUNGEE2BUKKIT, BukkitListener())
        Channel.sendProxy = {it,p->
            (p as Player).sendPluginMessage(this, Channel.BUKKIT2BUNGEE, it.toByteArray())
        }
    }

    fun connectSQL() {
        val f = File(this.dataFolder, "config.yml")
        if (!f.exists()) {
            this.saveDefaultConfig()
        }
        val cfg = YamlConfiguration.loadConfiguration(f)
        val db = cfg.getConfigurationSection("Mysql")
        val sb = StringBuilder(String.format("jdbc:mysql://%s:%d/%s?user=%s&password=%s",
                db.getString("host"),
                db.getInt("port"),
                db.getString("database"),
                db.getString("user"),
                db.getString("password")
        ))
        for (s in db.getStringList("options")) {
            sb.append('&')
            sb.append(s)
        }
        val config = HikariConfig()
        config.jdbcUrl = sb.toString()
        DatabaseHandler.init(config)
    }

    override fun onDisable() {
        // Plugin shutdown logic
    }

    companion object{
        lateinit var Plugin: BukkitMain
    }
}