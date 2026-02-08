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
                // 发放奖励
                giveReward(player);

                // 扣除已奖励的时间
                long newUnrewarded = totalUnrewarded - Main.REWARD_INTERVAL;
                plugin.getUnrewardedTime().put(uuid, newUnrewarded);
                plugin.saveOnlineTime(uuid, newUnrewarded);

                // 重置会话开始时间
                plugin.getSessionStartTimes().put(uuid, System.currentTimeMillis());
            }
        }
    }

    private void giveReward(Player player) {
        boolean isSponsor = player.hasPermission(Main.SPONSOR_PERMISSION);
        int rewardAmount = isSponsor ? Main.SPONSOR_REWARD : Main.BASE_REWARD;
        String multiplierText = isSponsor ? "§d(x2.0 赞助倍率)" : "§7(x1.0 基础倍率)";

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "money give " + player.getName() + " " + rewardAmount);

        String rewardMsg = Main.PREFIX + "§a您已在线满1小时，获得奖励: §e" + rewardAmount + " §a硬币 " + multiplierText;
        player.sendMessage(rewardMsg);
    }
}