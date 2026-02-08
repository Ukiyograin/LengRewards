package shaziawa.LengRewards;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Main extends JavaPlugin {
    private Connection connection;
    private final Map<UUID, Long> sessionStartTimes = new HashMap<>();
    private final Map<UUID, Long> unrewardedTime = new HashMap<>();
    
    public static final String PREFIX = "§a[§b奖咪§a] ";
    public static final String SPONSOR_PERMISSION = "CFC.zanzhu";
    public static final long REWARD_INTERVAL = 60 * 60 * 1000; // 1小时(毫秒)
    public static final int BASE_REWARD = 500; // 普通玩家奖励
    public static final int SPONSOR_REWARD = 1000; // 赞助玩家奖励

    @Override
    public void onEnable() {
        initializeDatabase();
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        new TimeCheckTask(this).runTaskTimer(this, 100L, 100L);
        getLogger().info(PREFIX + "§a插件已启用！§bauthor:shazi_awa");
    }

    @Override
    public void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            updateUnrewardedTime(uuid);
            saveOnlineTime(uuid, getTotalUnrewardedTime(uuid));
        }
        
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            getLogger().warning("关闭数据库连接时出错: " + e.getMessage());
        }
        
        getLogger().info(PREFIX + "§c插件已禁用！§bauthor:shazi_awa");
    }

    private void initializeDatabase() {
        try {
            getDataFolder().mkdirs();
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + getDataFolder().getAbsolutePath() + "/player_data.db");
            
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS player_time (" +
                    "uuid TEXT PRIMARY KEY, " +
                    "name TEXT, " +
                    "unrewarded_millis INTEGER DEFAULT 0)"
                );
            }
        } catch (ClassNotFoundException | SQLException e) {
            getLogger().severe("初始化数据库时出错: " + e.getMessage());
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    public void updateUnrewardedTime(UUID uuid) {
        if (sessionStartTimes.containsKey(uuid)) {
            long sessionTime = System.currentTimeMillis() - sessionStartTimes.get(uuid);
            unrewardedTime.put(uuid, unrewardedTime.getOrDefault(uuid, 0L) + sessionTime);
            sessionStartTimes.put(uuid, System.currentTimeMillis());
        }
    }

    public long getTotalUnrewardedTime(UUID uuid) {
        long total = unrewardedTime.getOrDefault(uuid, 0L);
        if (sessionStartTimes.containsKey(uuid)) {
            total += System.currentTimeMillis() - sessionStartTimes.get(uuid);
        }
        return total;
    }

public void loadPlayerData(UUID uuid, String playerName) {
    try (PreparedStatement stmt = connection.prepareStatement(
            "SELECT unrewarded_millis FROM player_time WHERE uuid = ?")) {
        stmt.setString(1, uuid.toString());
        ResultSet rs = stmt.executeQuery();
        
        if (rs.next()) {
            unrewardedTime.put(uuid, rs.getLong("unrewarded_millis"));
        } else {
            // 新玩家初始化
            try (PreparedStatement insertStmt = connection.prepareStatement(
                    "INSERT INTO player_time (uuid, name, unrewarded_millis) VALUES (?, ?, 0)")) {
                insertStmt.setString(1, uuid.toString());
                insertStmt.setString(2, playerName);
                insertStmt.executeUpdate();
            }
            unrewardedTime.put(uuid, 0L); // 确保Map中有值
        }
    } catch (SQLException e) {
        getLogger().warning("加载玩家数据时出错: " + e.getMessage());
        unrewardedTime.put(uuid, 0L); // 出错时也初始化
    }
}

public void saveOnlineTime(UUID uuid, long millis) {
    try (PreparedStatement stmt = connection.prepareStatement(
            "UPDATE player_time SET unrewarded_millis = ?, name = ? WHERE uuid = ?")) {
        stmt.setLong(1, millis);
        stmt.setString(2, Bukkit.getOfflinePlayer(uuid).getName());
        stmt.setString(3, uuid.toString());
        stmt.executeUpdate();
    } catch (SQLException e) {
        getLogger().warning("保存在线时间时出错: " + e.getMessage());
    }
}

    // Getters
    public Map<UUID, Long> getSessionStartTimes() { return sessionStartTimes; }
    public Map<UUID, Long> getUnrewardedTime() { return unrewardedTime; }
    public Connection getConnection() { return connection; }
}