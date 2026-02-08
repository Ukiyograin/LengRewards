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

private void giveReward(Player player, int rewardAmount, int consecutiveHoursBeforeReward) {
    boolean isSponsor = player.hasPermission(Main.SPONSOR_PERMISSION);
    String multiplierText = isSponsor ? "§d(x2.0 赞助倍率)" : "§7(x1.0 基础倍率)";
    
    int currentHour = consecutiveHoursBeforeReward + 1;
    String consecutiveText = "§e(连续" + currentHour + "小时)";
    
    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "money give " + player.getName() + " " + rewardAmount);

    String rewardMsg = plugin.getRandomRewardMessage(rewardAmount, consecutiveHoursBeforeReward);
    player.sendMessage(rewardMsg + " " + consecutiveText + " " + multiplierText);
    
    // 计算最大连续小时数
    int baseReward = isSponsor ? Main.SPONSOR_REWARD : Main.BASE_REWARD;
    int maxConsecutiveHours = (Main.MAX_REWARD - baseReward) / Main.REWARD_INCREMENT;
    
    // 检查是否达到最高奖励
    if (rewardAmount >= Main.MAX_REWARD) {
        // 首次达到最高奖励时发送消息
        if (consecutiveHoursBeforeReward == maxConsecutiveHours) {
            player.sendMessage(Main.PREFIX + "§6✦ ✦ ✦ 恭喜！已达到当日最高奖励！");
            player.sendMessage(Main.PREFIX + "§6从现在起，每小时都将获得" + Main.MAX_REWARD + "硬币！ ✦ ✦ ✦");
        }
    }
}
}