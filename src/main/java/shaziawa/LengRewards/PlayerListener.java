package shaziawa.LengRewards;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;

public class PlayerListener implements Listener {
    private final Main plugin;

    public PlayerListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // 初始化会话时间
        plugin.getSessionStartTimes().put(uuid, System.currentTimeMillis());
        
        // 加载玩家数据（如果不存在会初始化为0）
        plugin.loadPlayerData(uuid, player.getName());
        
        // 检查是否需要重置连续小时数（新的一天）
        plugin.checkDailyReset(uuid);  // 现在这个方法已经是 public 了
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // 检查是否有会话时间记录
        if (plugin.getSessionStartTimes().containsKey(uuid)) {
            // 更新未奖励时间（安全获取，避免NPE）
            long currentUnrewarded = plugin.getUnrewardedTime().getOrDefault(uuid, 0L);
            long sessionTime = System.currentTimeMillis() - plugin.getSessionStartTimes().get(uuid);
            long totalUnrewarded = currentUnrewarded + sessionTime;
            
            // 保存到数据库（包括未奖励时间）
            plugin.savePlayerData(uuid, totalUnrewarded);
            
            // 检查时间，决定是否重置连续小时数
            LocalTime now = LocalTime.now(ZoneId.of("Asia/Shanghai"));
            if (now.getHour() != 23 || now.getMinute() < 58) {
                // 不是23:58之后，立即重置连续在线小时数
                plugin.resetConsecutiveHoursOnQuit(uuid);
            }
            
            // 清除内存数据
            plugin.getSessionStartTimes().remove(uuid);
            plugin.getUnrewardedTime().remove(uuid);
            plugin.getConsecutiveHours().remove(uuid); // 从内存中移除
        }
    }
}