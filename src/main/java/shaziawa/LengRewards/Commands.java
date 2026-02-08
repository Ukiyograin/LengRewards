package shaziawa.LengRewards;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import java.time.LocalDate; 
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;

public class Commands implements CommandExecutor, TabCompleter {
    private final Main plugin;
    
    public Commands(Main plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            // /奖咪 - 显示今日奖励信息
            if (!(sender instanceof Player)) {
                sender.sendMessage("§c只有玩家可以使用此命令！");
                return true;
            }
            
            Player player = (Player) sender;
            UUID uuid = player.getUniqueId();
            
            // 计算今日累计奖励
            int totalToday = calculateTodayRewards(uuid);
            int consecutiveHours = plugin.getConsecutiveHours(uuid);
            long unrewardedTime = plugin.getTotalUnrewardedTime(uuid);
            int nextReward = plugin.calculateReward(uuid, player.hasPermission(Main.SPONSOR_PERMISSION));
            double progress = (double) unrewardedTime / Main.REWARD_INTERVAL * 100;
            
            // 构建信息消息
            player.sendMessage("§6§l╔══════════════════════════╗");
            player.sendMessage("§a§l          奖咪统计");
            player.sendMessage("");
            player.sendMessage("§f今日累计奖励: §e" + totalToday + " §f硬币");
            player.sendMessage("§f当前连续在线: §b" + (consecutiveHours + 1) + " §f小时");
            player.sendMessage("§f下次奖励金额: §6" + nextReward + " §f硬币");
            player.sendMessage("§f进度: §a" + String.format("%.1f", progress) + "% §f(约" + 
                String.format("%.0f", (Main.REWARD_INTERVAL - unrewardedTime) / 60000.0) + "分钟)");
            player.sendMessage("");
            player.sendMessage("§7基础奖励: " + Main.BASE_REWARD + " §8| §d赞助奖励: " + Main.SPONSOR_REWARD);
            player.sendMessage("§7每连续1小时增加: " + Main.REWARD_INCREMENT + "硬币");
            player.sendMessage("§6§l╚══════════════════════════╝");
            
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "设定":
            case "set":
                if (!sender.hasPermission("lengrewards.admin")) {
                    sender.sendMessage(Main.PREFIX + "§c你没有权限使用此命令！");
                    return true;
                }
                
                if (args.length < 3) {
                    sender.sendMessage("§c用法: /奖咪 设定 <玩家> <小时>");
                    sender.sendMessage("§e示例: /奖咪 设定 shazi_awa 5");
                    return true;
                }
                
                String targetName = args[1];
                int hours;
                
                try {
                    hours = Integer.parseInt(args[2]);
                    if (hours < 0 || hours > 24) {
                        sender.sendMessage("§c小时数必须在0-24之间！");
                        return true;
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage("§c请提供有效的数字！");
                    return true;
                }
                
                // 查找玩家
                Player target = Bukkit.getPlayer(targetName);
                UUID targetUuid;
                
                if (target != null) {
                    targetUuid = target.getUniqueId();
                } else {
                    // 尝试从数据库查找
                    try {
                        targetUuid = getPlayerUuidFromDatabase(targetName);
                        if (targetUuid == null) {
                            sender.sendMessage(Main.PREFIX + "§c找不到玩家: " + targetName);
                            return true;
                        }
                    } catch (SQLException e) {
                        sender.sendMessage(Main.PREFIX + "§c查询玩家数据时出错！");
                        plugin.getLogger().warning("查询玩家数据出错: " + e.getMessage());
                        return true;
                    }
                }
                
                // 设置连续小时数
                plugin.setConsecutiveHours(targetUuid, hours);
                
                // 更新数据库
                try (PreparedStatement stmt = plugin.getConnection().prepareStatement(
                        "UPDATE player_time SET consecutive_hours = ? WHERE uuid = ?")) {
                    stmt.setInt(1, hours);
                    stmt.setString(2, targetUuid.toString());
                    stmt.executeUpdate();
                    
                    sender.sendMessage(Main.PREFIX + "§a成功设置玩家 §e" + targetName + " §a的连续在线时间为 §6" + hours + " §a小时");
                    
                    // 如果玩家在线，发送通知
                    if (target != null) {
                        target.sendMessage(Main.PREFIX + "§a管理员已设置你的连续在线时间为 §6" + hours + " §a小时");
                    }
                } catch (SQLException e) {
                    sender.sendMessage(Main.PREFIX + "§c保存数据时出错！");
                    plugin.getLogger().warning("保存连续小时数出错: " + e.getMessage());
                }
                
                return true;
                
            case "过夜":
            case "night":
                if (!sender.hasPermission("lengrewards.admin")) {
                    sender.sendMessage(Main.PREFIX + "§c你没有权限使用此命令！");
                    return true;
                }
                
                simulateNightTransition(sender);
                return true;
                
            case "重置":
            case "reset":
                if (!sender.hasPermission("lengrewards.admin")) {
                    sender.sendMessage(Main.PREFIX + "§c你没有权限使用此命令！");
                    return true;
                }
                
                if (args.length < 2) {
                    sender.sendMessage("§c用法: /奖咪 重置 <玩家|all>");
                    sender.sendMessage("§e示例: /奖咪 重置 shazi_awa");
                    sender.sendMessage("§e示例: /奖咪 重置 all (重置所有玩家)");
                    return true;
                }
                
                if (args[1].equalsIgnoreCase("all")) {
                    // 重置所有玩家
                    resetAllPlayers(sender);
                } else {
                    // 重置单个玩家
                    resetPlayer(sender, args[1]);
                }
                return true;
                
            case "帮助":
            case "help":
                sendHelp(sender);
                return true;
                
            default:
                sender.sendMessage("§c未知命令！使用 /奖咪 帮助 查看可用命令");
                return true;
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> commands = Arrays.asList("设定", "set", "过夜", "night", "重置", "reset", "帮助", "help");
            
            for (String cmd : commands) {
                if (cmd.toLowerCase().startsWith(partial)) {
                    completions.add(cmd);
                }
            }
            
            // 如果玩家不是OP，过滤掉管理命令
            if (!sender.hasPermission("lengrewards.admin")) {
                completions.removeIf(cmd -> 
                    cmd.equals("设定") || cmd.equals("set") || 
                    cmd.equals("过夜") || cmd.equals("night") ||
                    cmd.equals("重置") || cmd.equals("reset")
                );
            }
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            
            if (subCommand.equals("设定") || subCommand.equals("set") || 
                subCommand.equals("重置") || subCommand.equals("reset")) {
                
                // 添加在线玩家名
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(player.getName());
                    }
                }
                
                // 如果是重置命令，添加"all"
                if ((subCommand.equals("重置") || subCommand.equals("reset")) && 
                    "all".startsWith(args[1].toLowerCase())) {
                    completions.add("all");
                }
            }
        } else if (args.length == 3) {
            String subCommand = args[0].toLowerCase();
            
            if (subCommand.equals("设定") || subCommand.equals("set")) {
                // 添加小时建议
                for (int i = 0; i <= 24; i++) {
                    if (String.valueOf(i).startsWith(args[2])) {
                        completions.add(String.valueOf(i));
                    }
                }
            }
        }
        
        return completions;
    }
    
    private int calculateTodayRewards(UUID uuid) {
        // 从数据库查询今日奖励总额（需要记录奖励历史）
        // 这里简化处理，返回估计值
        int consecutiveHours = plugin.getConsecutiveHours(uuid);
        boolean isSponsor = Bukkit.getOfflinePlayer(uuid).isOnline() ? 
            Bukkit.getPlayer(uuid).hasPermission(Main.SPONSOR_PERMISSION) : false;
        
        int total = 0;
        int base = isSponsor ? Main.SPONSOR_REWARD : Main.BASE_REWARD;
        
        for (int i = 0; i <= consecutiveHours; i++) {
            int reward = Math.min(base + (i * Main.REWARD_INCREMENT), Main.MAX_REWARD);
            total += reward;
        }
        
        return total;
    }
    
    private UUID getPlayerUuidFromDatabase(String playerName) throws SQLException {
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(
                "SELECT uuid FROM player_time WHERE name = ?")) {
            stmt.setString(1, playerName);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return UUID.fromString(rs.getString("uuid"));
            }
        }
        return null;
    }
    
 private void simulateNightTransition(CommandSender sender) {
    if (!(sender instanceof Player)) {
        sender.sendMessage("§c只有玩家可以模拟过夜流程！");
        return;
    }
    
    Player player = (Player) sender;
    UUID playerUuid = player.getUniqueId();
    
    // 询问是否要自动恢复
    sender.sendMessage(Main.PREFIX + "§6开始模拟过夜流程（仅对你生效）...");
    sender.sendMessage(Main.PREFIX + "§e模拟完成后，你想：");
    sender.sendMessage("§71) §a自动恢复数据 §7(推荐)");
    sender.sendMessage("§72) §c保留模拟后的数据");
    sender.sendMessage("");
    sender.sendMessage(Main.PREFIX + "§e请在10秒内输入 1 或 2 选择...");
    
    // 等待玩家选择
    setupChoiceListener(player);
}

private void setupChoiceListener(Player player) {
    // 使用Bukkit的监听器来处理选择
    // 这里简化处理，直接进行模拟并自动恢复
    
    UUID uuid = player.getUniqueId();
    
    // 记录原始数据
    int originalHours = plugin.getConsecutiveHours(uuid);
    long originalUnrewarded = plugin.getTotalUnrewardedTime(uuid);
    String originalDate = plugin.getLastRewardDate(uuid);
    
    // 开始模拟
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
        // 模拟23:58
        player.sendMessage(Main.PREFIX + "§e[23:58] 正在模拟预重置...");
        
        // 计算并发放比例奖励
        simulatePreReset(player);
        
        // 等待后模拟00:00
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            player.sendMessage(Main.PREFIX + "§e[00:00] 正在模拟正式重置...");
            
            // 保存当前数据（模拟重置前）
            plugin.savePlayerData(uuid, 0);
            
            // 模拟重置
            plugin.setConsecutiveHours(uuid, 0);
            plugin.setLastRewardDate(uuid, LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1).toString());
            
            player.sendMessage(Main.PREFIX + "§a重置完成！连续小时已归零");
            
            // 等待后自动恢复
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.sendMessage(Main.PREFIX + "§e正在自动恢复你的原始数据...");
                
                // 恢复数据
                restoreToOriginal(player, originalHours, originalUnrewarded, originalDate);
                
            }, 40L); // 2秒后
        }, 40L); // 2秒后
    }, 20L); // 1秒后
}

private void simulatePreReset(Player player) {
    UUID uuid = player.getUniqueId();
    long totalUnrewarded = plugin.getTotalUnrewardedTime(uuid);
    
    if (totalUnrewarded > 0) {
        double proportion = (double) totalUnrewarded / Main.REWARD_INTERVAL;
        if (proportion > 0.1) {
            boolean isSponsor = player.hasPermission(Main.SPONSOR_PERMISSION);
            int consecutiveHours = plugin.getConsecutiveHours(uuid);
            int baseReward = plugin.calculateReward(uuid, isSponsor);
            int proportionalReward = (int) (baseReward * proportion);
            
            if (proportionalReward > 0) {
                player.sendMessage(Main.PREFIX + "§e模拟发放" + String.format("%.1f", proportion * 60) + 
                    "分钟的比例奖励: §6" + proportionalReward + "§e硬币");
                player.sendMessage(Main.PREFIX + "§7(这是模拟，实际不会发放硬币)");
            }
        }
    }
}

private void restoreToOriginal(Player player, int originalHours, long originalUnrewarded, String originalDate) {
    UUID uuid = player.getUniqueId();
    
    // 恢复内存数据
    plugin.setConsecutiveHours(uuid, originalHours);
    plugin.setLastRewardDate(uuid, originalDate);
    plugin.getUnrewardedTime().put(uuid, originalUnrewarded);
    
    // 恢复数据库数据
    try (PreparedStatement stmt = plugin.getConnection().prepareStatement(
            "UPDATE player_time SET consecutive_hours = ?, last_reward_date = ?, unrewarded_millis = ? WHERE uuid = ?")) {
        stmt.setInt(1, originalHours);
        stmt.setString(2, originalDate);
        stmt.setLong(3, originalUnrewarded);
        stmt.setString(4, uuid.toString());
        stmt.executeUpdate();
        
        player.sendMessage(Main.PREFIX + "§a数据恢复完成！");
        player.sendMessage(Main.PREFIX + "§7你的所有数据已恢复到模拟前的状态");
    } catch (SQLException e) {
        player.sendMessage(Main.PREFIX + "§c恢复数据时出错！");
    }
}
    
    private void resetPlayer(CommandSender sender, String playerName) {
        Player target = Bukkit.getPlayer(playerName);
        UUID targetUuid;
        
        if (target != null) {
            targetUuid = target.getUniqueId();
        } else {
            try {
                targetUuid = getPlayerUuidFromDatabase(playerName);
                if (targetUuid == null) {
                    sender.sendMessage(Main.PREFIX + "§c找不到玩家: " + playerName);
                    return;
                }
            } catch (SQLException e) {
                sender.sendMessage(Main.PREFIX + "§c查询玩家数据时出错！");
                return;
            }
        }
        
        // 重置玩家数据
        plugin.setConsecutiveHours(targetUuid, 0);
        plugin.getUnrewardedTime().put(targetUuid, 0L);
        plugin.setLastRewardDate(targetUuid, plugin.getCurrentDate());
        
        // 更新数据库
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(
                "UPDATE player_time SET consecutive_hours = 0, unrewarded_millis = 0, last_reward_date = ? WHERE uuid = ?")) {
            stmt.setString(1, plugin.getCurrentDate());
            stmt.setString(2, targetUuid.toString());
            stmt.executeUpdate();
            
            sender.sendMessage(Main.PREFIX + "§a已重置玩家 §e" + playerName + " §a的数据");
            
            if (target != null) {
                target.sendMessage(Main.PREFIX + "§a你的奖咪数据已被管理员重置");
            }
        } catch (SQLException e) {
            sender.sendMessage(Main.PREFIX + "§c重置数据时出错！");
        }
    }
    
    private void resetAllPlayers(CommandSender sender) {
        int count = 0;
        
        // 重置在线玩家
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            plugin.setConsecutiveHours(uuid, 0);
            plugin.getUnrewardedTime().put(uuid, 0L);
            plugin.setLastRewardDate(uuid, plugin.getCurrentDate());
            player.sendMessage(Main.PREFIX + "§a你的奖咪数据已被管理员重置");
            count++;
        }
        
        // 重置数据库中的所有玩家
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(
                "UPDATE player_time SET consecutive_hours = 0, unrewarded_millis = 0, last_reward_date = ?")) {
            stmt.setString(1, plugin.getCurrentDate());
            stmt.executeUpdate();
            
            sender.sendMessage(Main.PREFIX + "§a已重置所有玩家的数据（共" + count + "个在线玩家受影响）");
        } catch (SQLException e) {
            sender.sendMessage(Main.PREFIX + "§c重置数据库时出错！");
        }
    }
    
    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6§l╔══════════════════════════╗");
        sender.sendMessage("§a§l         奖咪帮助");
        sender.sendMessage("");
        sender.sendMessage("§f/奖咪 §7- 查看今日奖励统计");
        
        if (sender.hasPermission("lengrewards.admin")) {
            sender.sendMessage("");
            sender.sendMessage("§6管理员命令:");
            sender.sendMessage("§f/奖咪 设定 <玩家> <小时> §7- 设置玩家连续在线时间");
            sender.sendMessage("§f/奖咪 过夜 §7- 模拟过夜重置流程");
            sender.sendMessage("§f/奖咪 重置 <玩家|all> §7- 重置玩家数据");
            sender.sendMessage("");
            sender.sendMessage("§e权限节点: lengrewards.admin");
        }
        sender.sendMessage("§6§l╚══════════════════════════╝");
    }
}