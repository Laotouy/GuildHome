package com.github.bryanser.guildhome.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

object DatabaseHandler {
    const val TABLE_GUILDHOME = "GuildHome"
    const val TABLE_GUILD_MEMBER = "GuildMember"
    const val TABLE_GUILD_APPLY = "GuildApply"


    class Selecting(
            private val preparedStatement: PreparedStatement
    ) {
        lateinit var set: ResultSet

        fun where(): PreparedStatement {
            return preparedStatement
        }

        fun select() {
            set = preparedStatement.executeQuery()
        }

        fun next(): Boolean {
            return set.next()
        }

        fun <V> get(key: Key<V>): V {
            val g = key.getter
            return set.g(key.name)
        }
    }

    inline fun select(sql: String, func: Selecting.() -> Unit) {
        sql {
            val ps = this.prepareStatement(sql)
            val s = Selecting(ps)
            s.func()
        }
    }

    lateinit var pool: HikariDataSource

    fun init(config: HikariConfig) {
        config.addDataSourceProperty("cachePrepStmts", "true")
        config.addDataSourceProperty("prepStmtCacheSize", "250")
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
        config.idleTimeout = 60000
        config.connectionTimeout = 60000
        config.validationTimeout = 3000
        config.maxLifetime = 60000
        pool = HikariDataSource(config)
        createTable()
    }

    fun createTable() {
        sql {
            val sta = createStatement()
            sta.execute("""
                CREATE TABLE IF NOT EXISTS GuildHome(
                    ID INT PRIMARY KEY AUTO_INCREMENT,
                    NAME VARCHAR(30) NOT NULL,
                    LEVEL INT NOT NULL DEFAULT 1,
                    CONTRIBUTION INT NOT NULL DEFAULT 0,
                    MOTD TEXT NOT NULL,
                    ICON TEXT DEFAULT NULL
                ) ENGINE = InnoDB DEFAULT CHARSET=utf8mb4
            """)
            sta.execute("""
                CREATE TABLE IF NOT EXISTS GuildMember(
                    NAME VARCHAR(80) NOT NULL PRIMARY KEY,
                    GID INT NOT NULL,
                    CAREER VARCHAR(15) NOT NULL DEFAULT 'MEMBER',
                    CONTRIBUTION INT NOT NULL DEFAULT 0,
                    FOREIGN KEY (GID) REFERENCES GuildHome(ID)
                ) ENGINE = InnoDB DEFAULT CHARSET=utf8mb4
            """)
            sta.execute("""
               CREATE TABLE IF NOT EXISTS GuildApply(
                    NAME VARCHAR(80) NOT NULL,
                    GID INT NOT NULL,
                    TIME LONG NOT NULL,
                    PRIMARY KEY (NAME,GID),
                    FOREIGN KEY (GID) REFERENCES GuildHome(ID)
               ) ENGINE = InnoDB DEFAULT CHARSET=utf8mb4
            """)
            sta.execute("""
               CREATE TABLE IF NOT EXISTS GuildUserName(
                    UUID VARCHAR(80) NOT NULL PRIMARY KEY,
                    NAME VARCHAR(80)
               )  ENGINE = InnoDB DEFAULT CHARSET=utf8mb4
            """)
            try {
                sta.execute("""
                CREATE VIEW V_GuildSize(GID, SIZE) AS(
                    SELECT GuildMember.GID,COUNT(*) FROM GuildMember GROUP BY GID
                )
                """)
                sta.execute("""
                CREATE VIEW V_Guild(GID, GUILD_NAME, MEMBER_NAME, LEVEL, GUILD_CONTRIBUTION, SIZE, ICON, MOTD, SCORE) AS(
                    SELECT GuildHome.ID, GuildHome.NAME, GuildMember.NAME, GuildHome.LEVEL, GuildHome.CONTRIBUTION, V_GuildSize.SIZE, GuildHome.ICON, GuildHome.MOTD, (GuildHome.LEVEL * 100 + GuildHome.CONTRIBUTION + V_GuildSize.SIZE * 500) AS SCORE
                    FROM GuildMember, GuildHome, V_GuildSize WHERE GuildHome.ID = GuildMember.GID AND V_GuildSize.GID = GuildMember.GID AND GuildMember.CAREER = 'PRESIDENT' ORDER BY SCORE DESC
                )
                """)
            } catch (e: Throwable) {
            }
            sta.close()
        }
    }


    inline fun sql(debug: Boolean = true, func: Connection.() -> Unit) {
        val conn = pool.connection
        try {
            conn.func()
        } catch (e: Throwable) {
            if (debug)
                e.printStackTrace()
        } finally {
            pool.evictConnection(conn)
        }
    }
}