package shaziawa.LengRewards;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime; 
import java.time.ZoneId;   
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Objects;

public class Main extends JavaPlugin {
    private Connection connection;
    private final Map<UUID, Long> sessionStartTimes = new HashMap<>();
    private final Map<UUID, Long> unrewardedTime = new HashMap<>();
    private final Map<UUID, Integer> consecutiveHours = new HashMap<>(); // 连续在线小时数
    private final Map<UUID, String> lastRewardDate = new HashMap<>(); // 最后奖励日期（用于每日重置）
    
    public static final String PREFIX = "§a[§b奖咪§a] ";
    public static final String SPONSOR_PERMISSION = "CFC.zanzhu";
    public static final long REWARD_INTERVAL = 60 * 60 * 1000; // 1小时(毫秒)
    public static final int BASE_REWARD = 200; // 普通玩家基础奖励
    public static final int SPONSOR_REWARD = 800; // 赞助玩家基础奖励
    public static final int REWARD_INCREMENT = 100; // 每连续一小时增加
    public static final int MAX_REWARD = 1800; // 每小时最高奖励
    public static final int MAX_CONSECUTIVE_HOURS = (MAX_REWARD - BASE_REWARD) / REWARD_INCREMENT + 1; // 最大连续小时数
    
    private static final String[] REWARD_MESSAGES = {
        "§a您已在线满1小时，获得奖励: §e%amount% §a硬币 §7(连续%hours%小时)",
        "§b٩(◕‿◕｡)۶ 在线奖励送达！ §e%amount% §b硬币到手～",
        "§d(●´ω｀●) 小天使给您送奖励啦～ §e%amount% §d硬币请收好",
        "§aヾ(●´∀｀●) 勤奋的冒险家！ §e%amount% §a硬币奖励发放中",
        "§b(っ◕‿◕)っ ✨ 在线奖励魔法！ §e%amount% §b硬币GET！",
        "§dฅ^•ﻌ•^ฅ 喵～奖励时间到！ §e%amount% §d硬币奉上",
        "§a(◕‿◕)♡ 您的坚持有回报！ §e%amount% §a硬币已发放",
        "§b✧*｡٩(ˊᗜˋ*)و✧*｡ 闪闪发光的奖励！ §e%amount% §b硬币",
        "§d(´･ω･`) 诶嘿～奖励来咯！ §e%amount% §d硬币请查收",
        "§a(￣▽￣)ノ 在线时间兑换成功！ §e%amount% §a硬币",
        "§b( •̀ ω •́ )✧ 坚持就是胜利！ §e%amount% §b硬币奖励",
        "§d(≧∇≦)ﾉ 开心！又到奖励时间～ §e%amount% §d硬币",
        "§a(＾▽＾) 恭喜您获得在线奖励！ §e%amount% §a硬币",
        "§b(～￣▽￣)～ 奖励小包裹！ §e%amount% §b硬币请签收",
        "§d(´▽`ʃ♡ƪ) 努力终有回报～ §e%amount% §d硬币到手",
        "§a(๑•̀ㅂ•́)و✧ 在线成就达成！ §e%amount% §a硬币奖励",
        "§b(◍•ᴗ•◍)❤ 温暖的奖励时间～ §e%amount% §b硬币",
        "§d(´｡• ᵕ •｡`) ♡ 小小心意请收下～ §e%amount% §d硬币",
        "§a(๑˃ᴗ˂)ﻭ 棒棒哒！在线奖励 §e%amount% §a硬币",
        "§b( ˙꒳˙ ) 叮咚～奖励到账！ §e%amount% §b硬币",
        "§d(=ↀωↀ=)✧ 猫猫给您送奖励！ §e%amount% §d硬币",
        "§a( • ̀ω•́ )✧ 再接再厉！ §e%amount% §a硬币奖励",
        "§b(´• ω •`) ♡ 感谢您的陪伴～ §e%amount% §b硬币",
        "§d(๑╹◡╹)ﾉ\"\"\" 奖励发放！ §e%amount% §d硬币",
        "§a(◕‿◕✿) 花花送给努力的您！ §e%amount% §a硬币",
        "§b( ˘ ³˘)♥ 爱您哟～这是奖励 §e%amount% §b硬币",
        "§d(´･ᴗ･`) 稳稳的幸福～ §e%amount% §d硬币奖励",
        "§a(＾ω＾) 开心游戏开心奖励！ §e%amount% §a硬币",
        "§b(„• ֊ •„) 歪头杀与奖励～ §e%amount% §b硬币",
        "§d( ˙꒳˙ ) ฅ 猫爪按印发奖励！ §e%amount% §d硬币",
        "§a(≧◡≦) ♡ 么么哒～奖励 §e%amount% §a硬币",
        "§b(„• ᴗ •„) 开心收下吧！ §e%amount% §b硬币",
        "§d(◕‿◕)♡ 奖励不会缺席～ §e%amount% §d硬币",
        "§a(๑•̀ㅂ•́)و 冲鸭！奖励 §e%amount% §a硬币"
    };

    @Override
    public void onEnable() {
        initializeDatabase();
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        new TimeCheckTask(this).runTaskTimer(this, 100L, 100L);
        // 启动每日重置任务
        new DailyResetTask(this).runTaskTimer(this, 6000L, 6000L); 
        Objects.requireNonNull(getCommand("奖咪")).setExecutor(new Commands(this));
        Objects.requireNonNull(getCommand("奖咪")).setTabCompleter(new Commands(this));
        getLogger().info(PREFIX + "§a插件已启用！§bauthor:shazi_awa");
    }

    @Override
    public void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            updateUnrewardedTime(uuid);
            savePlayerData(uuid, getTotalUnrewardedTime(uuid));
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
            // 创建基础表（如果不存在）
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS player_time (" +
                "uuid TEXT PRIMARY KEY, " +
                "name TEXT, " +
                "unrewarded_millis INTEGER DEFAULT 0, " +
                "consecutive_hours INTEGER DEFAULT 0, " +
                "last_reward_date TEXT)"
            );
            
            // 检查并添加缺失的列
            checkAndAddColumns(stmt);
        }
    } catch (ClassNotFoundException | SQLException e) {
        getLogger().severe("初始化数据库时出错: " + e.getMessage());
        Bukkit.getPluginManager().disablePlugin(this);
    }
}

private void checkAndAddColumns(Statement stmt) throws SQLException {
    // 检查 consecutive_hours 列是否存在
    try {
        stmt.executeQuery("SELECT consecutive_hours FROM player_time LIMIT 1");
    } catch (SQLException e) {
        // 如果列不存在，添加它
        getLogger().info(PREFIX + "检测到旧版数据库，正在添加 consecutive_hours 列...");
        stmt.executeUpdate("ALTER TABLE player_time ADD COLUMN consecutive_hours INTEGER DEFAULT 0");
        getLogger().info(PREFIX + "已成功添加 consecutive_hours 列");
    }
    
    // 检查 last_reward_date 列是否存在
    try {
        stmt.executeQuery("SELECT last_reward_date FROM player_time LIMIT 1");
    } catch (SQLException e) {
        // 如果列不存在，添加它
        getLogger().info(PREFIX + "检测到旧版数据库，正在添加 last_reward_date 列...");
        stmt.executeUpdate("ALTER TABLE player_time ADD COLUMN last_reward_date TEXT");
        
        // 为新列设置默认值（当前日期）
        String currentDate = getCurrentDate();
        try (PreparedStatement updateStmt = connection.prepareStatement(
                "UPDATE player_time SET last_reward_date = ? WHERE last_reward_date IS NULL")) {
            updateStmt.setString(1, currentDate);
            updateStmt.executeUpdate();
        }
        getLogger().info(PREFIX + "已成功添加 last_reward_date 列并设置默认值");
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
                "SELECT unrewarded_millis, consecutive_hours, last_reward_date FROM player_time WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                unrewardedTime.put(uuid, rs.getLong("unrewarded_millis"));
                consecutiveHours.put(uuid, rs.getInt("consecutive_hours"));
                lastRewardDate.put(uuid, rs.getString("last_reward_date"));
                
                // 检查是否需要每日重置
                checkDailyReset(uuid);
            } else {
                // 新玩家初始化
                try (PreparedStatement insertStmt = connection.prepareStatement(
                        "INSERT INTO player_time (uuid, name, unrewarded_millis, consecutive_hours, last_reward_date) VALUES (?, ?, 0, 0, ?)")) {
                    insertStmt.setString(1, uuid.toString());
                    insertStmt.setString(2, playerName);
                    insertStmt.setString(3, getCurrentDate());
                    insertStmt.executeUpdate();
                }
                unrewardedTime.put(uuid, 0L);
                consecutiveHours.put(uuid, 0);
                lastRewardDate.put(uuid, getCurrentDate());
            }
        } catch (SQLException e) {
            getLogger().warning("加载玩家数据时出错: " + e.getMessage());
            unrewardedTime.put(uuid, 0L);
            consecutiveHours.put(uuid, 0);
            lastRewardDate.put(uuid, getCurrentDate());
        }
    }

public void savePlayerData(UUID uuid, long millis) {
    try (PreparedStatement stmt = connection.prepareStatement(
            "UPDATE player_time SET unrewarded_millis = ?, consecutive_hours = ?, last_reward_date = ?, name = ? WHERE uuid = ?")) {
        stmt.setLong(1, millis);
        stmt.setInt(2, getConsecutiveHours(uuid));
        stmt.setString(3, getLastRewardDate(uuid));
        stmt.setString(4, Bukkit.getOfflinePlayer(uuid).getName());
        stmt.setString(5, uuid.toString());
        stmt.executeUpdate();
    } catch (SQLException e) {
        getLogger().warning("保存玩家数据时出错: " + e.getMessage());
    }
}

public void checkDailyReset(UUID uuid) {
    String currentDate = getCurrentDate();
    String lastDate = getLastRewardDate(uuid);
    
    if (!currentDate.equals(lastDate)) {
        // 日期变化，重置连续在线计数
        consecutiveHours.put(uuid, 0);
        lastRewardDate.put(uuid, currentDate);
        
        // 更新数据库
        try (PreparedStatement stmt = connection.prepareStatement(
                "UPDATE player_time SET consecutive_hours = 0, last_reward_date = ? WHERE uuid = ?")) {
            stmt.setString(1, currentDate);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            getLogger().warning("重置玩家连续小时数时出错: " + e.getMessage());
        }
        
        getLogger().info(PREFIX + "已重置玩家 " + uuid + " 的连续在线计数（新的一天）");
    }
}

public String getCurrentDate() {
    return LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
}

// 计算第N小时的奖励，N = consecutiveHours + 1
public int calculateReward(UUID uuid, boolean isSponsor) {
    int base = isSponsor ? SPONSOR_REWARD : BASE_REWARD;
    int hours = getConsecutiveHours(uuid); // 已经获得奖励的小时数
    int increment = Math.min(hours, MAX_CONSECUTIVE_HOURS - 1) * REWARD_INCREMENT;
    return Math.min(base + increment, MAX_REWARD);
}

    // Getters and Setters
    public Map<UUID, Long> getSessionStartTimes() { return sessionStartTimes; }
    public Map<UUID, Long> getUnrewardedTime() { return unrewardedTime; }
    public Map<UUID, Integer> getConsecutiveHours() { return consecutiveHours; }
    public Map<UUID, String> getLastRewardDate() { return lastRewardDate; }
    
    public int getConsecutiveHours(UUID uuid) {
        return consecutiveHours.getOrDefault(uuid, 0);
    }
    
    public void setConsecutiveHours(UUID uuid, int hours) {
        consecutiveHours.put(uuid, hours);
    }
    
    public String getLastRewardDate(UUID uuid) {
        return lastRewardDate.getOrDefault(uuid, getCurrentDate());
    }
    
    public void setLastRewardDate(UUID uuid, String date) {
        lastRewardDate.put(uuid, date);
    }
    
public String getRandomRewardMessage(int amount, int consecutiveHours) {
    String template = REWARD_MESSAGES[(int) (Math.random() * REWARD_MESSAGES.length)];
    int currentHour = consecutiveHours + 1; // 当前是第几小时
    return PREFIX + template.replace("%amount%", String.valueOf(amount))
                           .replace("%hours%", String.valueOf(currentHour));
}
    
    public Connection getConnection() { return connection; }
    
public void resetConsecutiveHoursOnQuit(UUID uuid) {
    // 检查是否是23:58之后，如果是，不立即重置（等待0点统一重置）
    LocalTime now = LocalTime.now(ZoneId.of("Asia/Shanghai"));
    if (now.getHour() == 23 && now.getMinute() >= 58) {
        // 23:58之后不重置，等待0点统一重置
        getLogger().info(PREFIX + "玩家 " + uuid + " 在23:58之后退出，延迟重置连续小时数");
        return;
    }
    
    consecutiveHours.put(uuid, 0);
    try (PreparedStatement stmt = connection.prepareStatement(
            "UPDATE player_time SET consecutive_hours = 0 WHERE uuid = ?")) {
        stmt.setString(1, uuid.toString());
        stmt.executeUpdate();
    } catch (SQLException e) {
        getLogger().warning("重置玩家退出时连续小时数出错: " + e.getMessage());
    }
}
public DailyResetTask getDailyResetTask() {
    return new DailyResetTask(this);
}
}