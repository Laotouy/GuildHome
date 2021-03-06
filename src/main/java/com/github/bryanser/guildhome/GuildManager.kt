package com.github.bryanser.guildhome

import com.github.bryanser.guildhome.bungee.GuildSetManager
import com.github.bryanser.guildhome.database.Career
import com.github.bryanser.guildhome.database.DatabaseHandler
import com.github.bryanser.guildhome.service.impl.BroadcastMessageService
import java.util.*

object GuildManager {
    fun getAllGuild(): List<GuildInfo> {
        val guilds = mutableListOf<GuildInfo>()
        DatabaseHandler.sql {
            val ps = this.prepareStatement("SELECT * FROM V_Guild")
            val rs = ps.executeQuery()
            while (rs.next()) {
                guilds += GuildInfo(
                        rs.getInt(1),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getInt(4),
                        rs.getInt(5),
                        rs.getInt(6),
                        rs.getString(7),
                        rs.getString(8),
                        rs.getInt(9)
                )
            }
        }
        return guilds
    }

    fun getGuild(id: Int): Guild? {
        var g: Guild? = null
        DatabaseHandler.sql {
            val ps = this.prepareStatement("SELECT * FROM ${DatabaseHandler.TABLE_GUILDHOME} WHERE ID = ? LIMIT 1")
            ps.setInt(1, id)
            val rs = ps.executeQuery()
            if (rs.next()) {
                g = Guild(
                        id,
                        rs.getString("NAME"),
                        rs.getInt("LEVEL"),
                        rs.getInt("CONTRIBUTION"),
                        rs.getString("MOTD"),
                        rs.getString("ICON")
                )
            }
        }
        return g
    }

    fun getGuildByName(name: String): Int? {
        var id: Int? = null
        DatabaseHandler.sql {
            val ps = this.prepareStatement("SELECT ID FROM ${DatabaseHandler.TABLE_GUILDHOME} WHERE NAME = ? LIMIT 1")
            ps.setString(1, name)
            val rs = ps.executeQuery()
            if (rs.next()) {
                id = rs.getInt("ID")
            }
        }
        return id
    }

    fun getMember(uuid: UUID): Member? {
        var r: Member? = null
        DatabaseHandler.sql {
            val ps = this.prepareStatement(
                    "SELECT * FROM ${DatabaseHandler.TABLE_GUILD_MEMBER} WHERE NAME = ? LIMIT 1"
            )
            ps.setString(1, uuid.toString())
            val rs = ps.executeQuery()
            if (rs.next()) {
                val id = rs.getInt("GID")
                val career = Career.valueOf(rs.getString("CAREER"))
                r = Member(uuid, id, career, rs.getInt("CONTRIBUTION"))
            }
        }
        return r
    }

    fun getMembers(gid: Int): List<Member> {
        val list = mutableListOf<Member>()
        DatabaseHandler.sql {
            val ps = this.prepareStatement("SELECT * FROM ${DatabaseHandler.TABLE_GUILD_MEMBER} WHERE GID = ? ORDER BY CONTRIBUTION DESC")
            ps.setInt(1, gid)
            val rs = ps.executeQuery()
            while (rs.next()) {
                val uuid = UUID.fromString(rs.getString("NAME"))
                val id = rs.getInt("GID")
                val career = Career.valueOf(rs.getString("CAREER"))
                list += Member(uuid, id, career, rs.getInt("CONTRIBUTION"))
            }
        }
        return list
    }

    fun getMemberSize(gid: Int): Int {
        var size = 0
        DatabaseHandler.sql {
            val ps = this.prepareStatement("SELECT COUNT(*) FROM ${DatabaseHandler.TABLE_GUILD_MEMBER} WHERE GID = ?")
            ps.setInt(1, gid)
            val rs = ps.executeQuery()
            if (rs.next()) {
                size = rs.getInt(1)
            }
        }
        return size
    }

    fun addApply(gid: Int, from: UUID): String {
        DatabaseHandler.sql {
            val ps = this.prepareStatement("SELECT * FROM ${DatabaseHandler.TABLE_GUILD_APPLY} WHERE GID = ? AND NAME = ?")
            ps.setInt(1, gid)
            ps.setString(2, from.toString())
            val rs = ps.executeQuery()
            if (rs.next()) {
                return "§c§l申请失败,你已经向该公会申请过了"
            }
        }
        DatabaseHandler.sql {
            val ps = this.prepareStatement("INSERT INTO ${DatabaseHandler.TABLE_GUILD_APPLY} VALUES (?, ?, ?)")
            ps.setString(1, from.toString())
            ps.setInt(2, gid)
            ps.setLong(3, System.currentTimeMillis())
            ps.executeUpdate()
        }

        return "§a§l申请成功,请等待公会管理处理吧"
    }

    fun getApplySize(gid: Int): Int {
        var size = 0
        DatabaseHandler.sql {
            val ps = this.prepareStatement("SELECT COUNT(*) FROM ${DatabaseHandler.TABLE_GUILD_APPLY} WHERE GID = ?")
            ps.setInt(1, gid)
            val rs = ps.executeQuery()
            if (rs.next()) {
                size = rs.getInt(1)
            }
        }
        return size
    }

    fun getApplys(gid: Int): List<Pair<UUID, Long>> {
        val list = mutableListOf<Pair<UUID, Long>>()
        DatabaseHandler.sql {
            val ps = this.prepareStatement("SELECT * FROM ${DatabaseHandler.TABLE_GUILD_APPLY} WHERE GID = ?")
            ps.setInt(1, gid)
            val rs = ps.executeQuery()
            while (rs.next()) {
                list += UUID.fromString(rs.getString("NAME")) to rs.getLong("TIME")
            }
        }
        return list
    }

    fun apply(gid: Int, uuid: UUID, name: String): String {
        val m = this.getMember(uuid)
        if (m != null) {
            DatabaseHandler.sql(false) {
                val ps = this.prepareStatement("DELETE FROM ${DatabaseHandler.TABLE_GUILD_APPLY} WHERE NAME = ?")
                ps.setString(1, uuid.toString())
                ps.executeQuery()
            }
            return "§c请求处理失败: §e§l对方已经加入了别的公会"
        }
        val guild = this.getGuild(gid) ?: return "§c请求处理失败: §e找不到该公会"
        val size = this.getMemberSize(gid)
        if (size >= Guild.getMaxMemberSize(guild.level)) {
            return "§c请求处理失败: §e公会人数已满"
        }
        DatabaseHandler.sql(true) {
            val ps = this.prepareStatement("DELETE FROM ${DatabaseHandler.TABLE_GUILD_APPLY} WHERE NAME = ?")
            ps.setString(1, uuid.toString())
            ps.executeUpdate()
            GuildSetManager.setMember(uuid, gid, Career.MEMBER)
        }
        BroadcastMessageService.broadcast(gid,
                "§6========§c[公会公告]§6========",
                "§c§l成员${name}加入了公会"
        )
        return "§6§l对方同意了你的邀请"
    }

    fun refuse(gid: Int, uuid: UUID): String {
        val m = this.getMember(uuid)
        if (m != null) {
            DatabaseHandler.sql(false) {
                val ps = this.prepareStatement("DELETE FROM ${DatabaseHandler.TABLE_GUILD_APPLY} WHERE NAME = ?")
                ps.setString(1, uuid.toString())
                ps.executeUpdate()
            }
            return "§c请求处理失败: §a对方已经加入了别的公会"
        }
        this.getGuild(gid) ?: return "§c请求处理失败: §b找不到该公会"
        DatabaseHandler.sql(false) {
            val ps = this.prepareStatement("DELETE FROM ${DatabaseHandler.TABLE_GUILD_APPLY} WHERE NAME = ? AND GID = ?")
            ps.setString(1, uuid.toString())
            ps.setInt(2, gid)
            ps.executeUpdate()
        }
        return "§6请求拒绝成功"
    }
}