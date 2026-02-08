package shaziawa.LengRewards;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;

public class DailyResetTask extends BukkitRunnable {
    private final Main plugin;
    private boolean resetExecutedToday = false;
    private boolean preResetExecuted = false;

    public DailyResetTask(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        LocalTime now = LocalTime.now(ZoneId.of("Asia/Shanghai"));
        
        // 23:58 执行预重置（发放未结算的奖励）
        if (now.getHour() == 23 && now.getMinute() >= 58 && !preResetExecuted) {
            executePreReset();
            preResetExecuted = true;
            plugin.getLogger().info(Main.PREFIX + "§a每日预重置执行完成");
        }
        
        // 00:00 执行正式重置
        if (now.getHour() == 0 && now.getMinute() < 1 && !resetExecutedToday) {
            executeDailyReset();
            resetExecutedToday = true;
            preResetExecuted = false;
            plugin.getLogger().info(Main.PREFIX + "§a每日重置执行完成");
        }
        
        // 重置标志（过了0点后重新允许执行）
        if (now.getHour() == 1) {
            resetExecutedToday = false;
        }
    }
    
    private void executePreReset() {
        // 在23:58分发放在线玩家还未满1小时的剩余奖励
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            
            if (plugin.getSessionStartTimes().containsKey(uuid)) {
                long totalUnrewarded = plugin.getTotalUnrewardedTime(uuid);
                
                // 如果有未奖励时间（即使不足1小时），按比例发放
                if (totalUnrewarded > 0) {
                    double proportion = (double) totalUnrewarded / Main.REWARD_INTERVAL;
                    if (proportion > 0.1) { // 至少在线6分钟才发放
                        boolean isSponsor = player.hasPermission(Main.SPONSOR_PERMISSION);
                        int consecutiveHours = plugin.getConsecutiveHours(uuid);
                        
                        // 如果内存中没有连续小时数，从数据库加载
                        if (consecutiveHours == 0) {
                            consecutiveHours = loadConsecutiveHoursFromDB(uuid);
                        }
                        
                        int baseReward = plugin.calculateReward(uuid, isSponsor);
                        int proportionalReward = (int) (baseReward * proportion);
                        
                        if (proportionalReward > 0) {
                            // 发放按比例计算的奖励
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), 
                                "money give " + player.getName() + " " + proportionalReward);
                            
                            player.sendMessage(Main.PREFIX + "§e⚠ 即将重置每日奖励，提前发放在线" + 
                                String.format("%.1f", proportion * 60) + "分钟的奖励: §6" + 
                                proportionalReward + "§e硬币");
                        }
                    }
                    
                    // 保存数据，清空未奖励时间
                    plugin.savePlayerData(uuid, 0);
                    plugin.getUnrewardedTime().put(uuid, 0L);
                    plugin.getSessionStartTimes().put(uuid, System.currentTimeMillis());
                }
            }
        }
    }

    private int loadConsecutiveHoursFromDB(UUID uuid) {
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(
                "SELECT consecutive_hours FROM player_time WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("consecutive_hours");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("从数据库加载连续小时数时出错: " + e.getMessage());
        }
        return 0;
    }
    
    private void executeDailyReset() {
        // 重置所有在线玩家的连续在线计数
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            plugin.setConsecutiveHours(uuid, 0);
            plugin.setLastRewardDate(uuid, plugin.getCurrentDate());
            
            // 同时更新数据库中的连续小时数
            try (PreparedStatement stmt = plugin.getConnection().prepareStatement(
                    "UPDATE player_time SET consecutive_hours = 0, last_reward_date = ? WHERE uuid = ?")) {
                stmt.setString(1, plugin.getCurrentDate());
                stmt.setString(2, uuid.toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("每日重置时更新数据库出错: " + e.getMessage());
            }
            
            // 发送重置通知
            player.sendMessage(Main.PREFIX + "§b✨ 新的一天开始啦！连续在线奖励已重置，重新开始累积吧！");
        }
        
        // 广播服务器通知
        Bukkit.broadcastMessage(Main.PREFIX + "§6✦ ✦ ✦ 新的一天开始，在线奖励已重置！ ✦ ✦ ✦");
    }
}