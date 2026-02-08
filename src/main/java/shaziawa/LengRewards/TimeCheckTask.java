package shaziawa.LengRewards;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

public class TimeCheckTask extends BukkitRunnable {
    private final Main plugin;

    public TimeCheckTask(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();

            // 确保玩家有会话时间记录
            if (!plugin.getSessionStartTimes().containsKey(uuid)) {
                plugin.getSessionStartTimes().put(uuid, System.currentTimeMillis());
                continue;
            }

            // 计算总未奖励时间
            long totalUnrewarded = plugin.getTotalUnrewardedTime(uuid);

            // 检查是否达到奖励间隔
            if (totalUnrewarded >= Main.REWARD_INTERVAL) {
                // 获取玩家信息
                boolean isSponsor = player.hasPermission(Main.SPONSOR_PERMISSION);
                int consecutiveHours = plugin.getConsecutiveHours(uuid);
                int rewardAmount = plugin.calculateReward(uuid, isSponsor);
                
                // 发放奖励
                giveReward(player, rewardAmount, consecutiveHours);
                
                // 更新连续在线小时数（+1）
                plugin.setConsecutiveHours(uuid, consecutiveHours + 1);
                
                // 更新最后奖励日期
                plugin.setLastRewardDate(uuid, plugin.getCurrentDate());
                
                // 扣除已奖励的时间
                long newUnrewarded = totalUnrewarded - Main.REWARD_INTERVAL;
                plugin.getUnrewardedTime().put(uuid, newUnrewarded);
                plugin.savePlayerData(uuid, newUnrewarded);

                // 重置会话开始时间
                plugin.getSessionStartTimes().put(uuid, System.currentTimeMillis());
            }
        }
    }

    private void giveReward(Player player, int rewardAmount, int consecutiveHours) {
        boolean isSponsor = player.hasPermission(Main.SPONSOR_PERMISSION);
        String multiplierText = isSponsor ? "§d(x2.0 赞助倍率)" : "§7(x1.0 基础倍率)";
        
        // 添加连续奖励信息
        String consecutiveText = "§e(连续" + (consecutiveHours + 1) + "小时)";
        
        // 执行奖励命令
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "money give " + player.getName() + " " + rewardAmount);

        // 随机选择一条二次元风格消息
        String rewardMsg = plugin.getRandomRewardMessage(rewardAmount, consecutiveHours);
        player.sendMessage(rewardMsg + " " + consecutiveText + " " + multiplierText);
        
        // 如果是最高奖励，发送特殊消息
        if (rewardAmount >= Main.MAX_REWARD) {
            player.sendMessage(Main.PREFIX + "§6✦ ✦ ✦ 已达到当日最高奖励！每小时" + Main.MAX_REWARD + "硬币！ ✦ ✦ ✦");
        }
    }
}