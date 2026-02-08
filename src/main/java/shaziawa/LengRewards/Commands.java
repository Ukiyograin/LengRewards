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
    private Map<UUID, Boolean> playerChoiceMap = new HashMap<>();
    
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
                return handleSetCommand(sender, args);
                
            case "过夜":
            case "night":
                return handleNightCommand(sender);
                
            case "恢复":
            case "restore":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§c只有玩家可以恢复数据！");
                    return true;
                }
                Player restorePlayer = (Player) sender;
                restorePlayerData(restorePlayer);
                return true;
                
            case "确认":
            case "confirm":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§c只有玩家可以使用此命令！");
                    return true;
                }
                Player confirmPlayer = (Player) sender;
                handleNightChoice(confirmPlayer, args.length > 1 ? args[1] : "");
                return true;
                
            case "保持":
            case "keep":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§c只有玩家可以使用此命令！");
                    return true;
                }
                Player keepPlayer = (Player) sender;
                keepPlayer.sendMessage(Main.PREFIX + "§a已保持当前模拟状态。");
                return true;
                
            case "发放":
            case "give":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§c只有玩家可以使用此命令！");
                    return true;
                }
                Player givePlayer = (Player) sender;
                giveActualReward(givePlayer);
                return true;
                
            case "跳过":
            case "skip":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§c只有玩家可以使用此命令！");
                    return true;
                }
                Player skipPlayer = (Player) sender;
                skipPlayer.sendMessage(Main.PREFIX + "§a已跳过实际奖励发放，继续模拟。");
                return true;
                
            case "重置":
            case "reset":
                return handleResetCommand(sender, args);
                
            case "帮助":
            case "help":
                sendHelp(sender);
                return true;
                
            default:
                sender.sendMessage("§c未知命令！使用 /奖咪 帮助 查看可用命令");
                return true;
        }
    }
    
    private boolean handleSetCommand(CommandSender sender, String[] args) {
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
            
            // 计算当前应该获得的奖励金额
            boolean isSponsor = target != null ? 
                target.hasPermission(Main.SPONSOR_PERMISSION) : 
                false; // 离线玩家假设不是赞助
            
            int rewardAmount = plugin.calculateReward(targetUuid, isSponsor);
            
            sender.sendMessage(Main.PREFIX + "§a成功设置玩家 §e" + targetName + " §a的连续在线时间为 §6" + hours + " §a小时");
            sender.sendMessage(Main.PREFIX + "§7当前应得奖励: §e" + rewardAmount + " §7硬币/小时");
            
            // 如果玩家在线，立即发放一次奖励
            if (target != null) {
                // 发放奖励
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "money give " + target.getName() + " " + rewardAmount);
                
                // 发送奖励消息
                String multiplierText = isSponsor ? "§d(x2.0 赞助倍率)" : "§7(x1.0 基础倍率)";
                String consecutiveText = "§e(连续" + (hours + 1) + "小时)";
                String rewardMsg = plugin.getRandomRewardMessage(rewardAmount, hours);
                
                target.sendMessage(rewardMsg + " " + consecutiveText + " " + multiplierText);
                target.sendMessage(Main.PREFIX + "§a管理员已设置你的连续在线时间为 §6" + hours + " §a小时，并发放当前小时奖励");
            }
        } catch (SQLException e) {
            sender.sendMessage(Main.PREFIX + "§c保存数据时出错！");
            plugin.getLogger().warning("保存连续小时数出错: " + e.getMessage());
        }
        
        return true;
    }
    
    private boolean handleNightCommand(CommandSender sender) {
        if (!sender.hasPermission("lengrewards.admin")) {
            sender.sendMessage(Main.PREFIX + "§c你没有权限使用此命令！");
            return true;
        }
        
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c只有玩家可以模拟过夜流程！");
            return true;
        }
        
        Player player = (Player) sender;
        
        // 清除之前的等待（如果有）
        cancelPreviousChoice(player);
        
        // 显示选择菜单
        showNightChoiceMenu(player);
        return true;
    }
    
    private boolean handleResetCommand(CommandSender sender, String[] args) {
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
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> commands = Arrays.asList("设定", "set", "过夜", "night", "重置", "reset", "帮助", "help", 
                                                   "恢复", "restore", "确认", "confirm", "保持", "keep", "发放", "give", "跳过", "skip");
            
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
                    cmd.equals("重置") || cmd.equals("reset") ||
                    cmd.equals("保持") || cmd.equals("keep") ||
                    cmd.equals("发放") || cmd.equals("give") ||
                    cmd.equals("跳过") || cmd.equals("skip")
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
            } else if (subCommand.equals("确认") || subCommand.equals("confirm")) {
                // 确认命令的选项
                List<String> choices = Arrays.asList("1", "2", "3", "4");
                for (String choice : choices) {
                    if (choice.startsWith(args[1])) {
                        completions.add(choice);
                    }
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
    
    private void showNightChoiceMenu(Player player) {
        UUID uuid = player.getUniqueId();
        
        // 保存当前数据
        int originalHours = plugin.getConsecutiveHours(uuid);
        long originalUnrewarded = plugin.getTotalUnrewardedTime(uuid);
        String originalDate = plugin.getLastRewardDate(uuid);
        
        // 存储原始数据供后续使用
        player.sendMessage(Main.PREFIX + "§6§m══════════════════════════");
        player.sendMessage(Main.PREFIX + "§a§l模拟过夜流程");
        player.sendMessage("");
        player.sendMessage("§f你的当前数据：");
        player.sendMessage("§7- 连续小时: §e" + originalHours);
        player.sendMessage("§7- 未奖励时间: §e" + (originalUnrewarded / 60000) + "分钟");
        player.sendMessage("§7- 最后奖励日期: §e" + originalDate);
        player.sendMessage("");
        player.sendMessage("§e请选择模拟选项：");
        player.sendMessage("§a1) §f完整模拟 §7(预重置+正式重置)");
        player.sendMessage("§a2) §f仅模拟预重置 §7(23:58发放比例奖励)");
        player.sendMessage("§a3) §f仅模拟正式重置 §7(00:00重置计数)");
        player.sendMessage("§c4) §f取消模拟");
        player.sendMessage("");
        player.sendMessage(Main.PREFIX + "§e输入 '/奖咪 确认 <编号>' 来选择");
        player.sendMessage(Main.PREFIX + "§7例如: §f/奖咪 确认 1");
        player.sendMessage(Main.PREFIX + "§6§m══════════════════════════");
        
        // 设置超时任务
        scheduleChoiceTimeout(player, 30); // 30秒超时
    }
    
    private void scheduleChoiceTimeout(Player player, int seconds) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.sendMessage(Main.PREFIX + "§c选择超时，模拟已取消。");
            }
        }, seconds * 20L);
    }
    
    private void handleNightChoice(Player player, String choice) {
        switch (choice) {
            case "1":
                simulateFullNightTransition(player);
                break;
            case "2":
                simulatePreResetOnly(player);
                break;
            case "3":
                simulateResetOnly(player);
                break;
            case "4":
                player.sendMessage(Main.PREFIX + "§a已取消模拟过夜流程。");
                break;
            default:
                player.sendMessage(Main.PREFIX + "§c无效的选择！请使用 1, 2, 3 或 4");
                showNightChoiceMenu(player);
                break;
        }
    }
    
    private void simulateFullNightTransition(Player player) {
        UUID uuid = player.getUniqueId();
        
        // 保存原始数据
        int originalHours = plugin.getConsecutiveHours(uuid);
        long originalUnrewarded = plugin.getTotalUnrewardedTime(uuid);
        String originalDate = plugin.getLastRewardDate(uuid);
        
        player.sendMessage(Main.PREFIX + "§6开始完整模拟过夜流程...");
        
        // 步骤1: 模拟23:58预重置
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            player.sendMessage(Main.PREFIX + "§e[23:58] 正在模拟预重置...");
            simulatePreResetForPlayer(player);
            
            // 步骤2: 模拟00:00正式重置
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.sendMessage(Main.PREFIX + "§e[00:00] 正在模拟正式重置...");
                
                // 保存重置前的状态
                plugin.savePlayerData(uuid, 0);
                
                // 执行重置
                plugin.setConsecutiveHours(uuid, 0);
                plugin.setLastRewardDate(uuid, LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1).toString());
                plugin.getUnrewardedTime().put(uuid, 0L);
                
                // 更新数据库
                try (PreparedStatement stmt = plugin.getConnection().prepareStatement(
                        "UPDATE player_time SET consecutive_hours = 0, unrewarded_millis = 0, last_reward_date = ? WHERE uuid = ?")) {
                    stmt.setString(1, plugin.getCurrentDate());
                    stmt.setString(2, uuid.toString());
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    player.sendMessage(Main.PREFIX + "§c更新数据库时出错！");
                }
                
                player.sendMessage(Main.PREFIX + "§a重置完成！");
                
                // 步骤3: 等待后询问是否恢复
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    player.sendMessage(Main.PREFIX + "§e[00:02] 模拟过夜流程完成！");
                    player.sendMessage(Main.PREFIX + "§6§m══════════════════════════");
                    player.sendMessage(Main.PREFIX + "§a模拟完成！当前状态：");
                    player.sendMessage("§7- 连续小时: §c0 §7(原来是 §e" + originalHours + "§7)");
                    player.sendMessage("§7- 未奖励时间: §c0ms §7(原来是 §e" + originalUnrewarded + "ms§7)");
                    player.sendMessage("§7- 最后奖励日期: §c" + plugin.getLastRewardDate(uuid) + 
                                     " §7(原来是 §e" + originalDate + "§7)");
                    player.sendMessage("");
                    player.sendMessage(Main.PREFIX + "§e是否要恢复原始数据？");
                    player.sendMessage(Main.PREFIX + "§a输入 '/奖咪 恢复' 恢复数据");
                    player.sendMessage(Main.PREFIX + "§c输入 '/奖咪 保持' 保持当前状态");
                    player.sendMessage(Main.PREFIX + "§6§m══════════════════════════");
                    
                }, 40L); // 2秒后
                
            }, 40L); // 2秒后
            
        }, 20L); // 1秒后
    }
    
    private void simulatePreResetOnly(Player player) {
        player.sendMessage(Main.PREFIX + "§6开始模拟预重置(23:58)...");
        simulatePreResetForPlayer(player);
    }
    
    private void simulateResetOnly(Player player) {
        UUID uuid = player.getUniqueId();
        
        // 保存原始数据
        int originalHours = plugin.getConsecutiveHours(uuid);
        String originalDate = plugin.getLastRewardDate(uuid);
        
        player.sendMessage(Main.PREFIX + "§6开始模拟正式重置(00:00)...");
        
        // 执行重置
        plugin.setConsecutiveHours(uuid, 0);
        plugin.setLastRewardDate(uuid, LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1).toString());
        
        // 更新数据库
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(
                "UPDATE player_time SET consecutive_hours = 0, last_reward_date = ? WHERE uuid = ?")) {
            stmt.setString(1, plugin.getCurrentDate());
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            player.sendMessage(Main.PREFIX + "§c更新数据库时出错！");
        }
        
        player.sendMessage(Main.PREFIX + "§a重置完成！");
        player.sendMessage(Main.PREFIX + "§7连续小时: " + originalHours + " → 0");
        player.sendMessage(Main.PREFIX + "§7最后奖励日期: " + originalDate + " → " + plugin.getLastRewardDate(uuid));
        
        // 询问是否恢复
        player.sendMessage(Main.PREFIX + "§e输入 '/奖咪 恢复' 可以恢复你的原始数据");
    }
    
    private void simulatePreResetForPlayer(Player player) {
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
                    
                    // 是否实际发放奖励？
                    player.sendMessage(Main.PREFIX + "§e是否要实际发放这些奖励？");
                    player.sendMessage(Main.PREFIX + "§a输入 '/奖咪 发放' 实际发放奖励");
                    player.sendMessage(Main.PREFIX + "§c输入 '/奖咪 跳过' 仅模拟不发放");
                }
            }
            
            // 清空未奖励时间（模拟）
            plugin.getUnrewardedTime().put(uuid, 0L);
            plugin.getSessionStartTimes().put(uuid, System.currentTimeMillis());
            
            player.sendMessage(Main.PREFIX + "§a预重置模拟完成！未奖励时间已清空");
        } else {
            player.sendMessage(Main.PREFIX + "§7没有需要处理的未奖励时间");
        }
    }
    
    private void cancelPreviousChoice(Player player) {
        // 这里可以取消之前设置的任何超时任务
        // 简化处理，直接返回
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
    
    private void restorePlayerData(Player player) {
        UUID uuid = player.getUniqueId();
        
        try (PreparedStatement stmt = plugin.getConnection().prepareStatement(
                "SELECT consecutive_hours, unrewarded_millis, last_reward_date FROM player_time WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                // 恢复数据库中的数据到内存
                plugin.setConsecutiveHours(uuid, rs.getInt("consecutive_hours"));
                plugin.getUnrewardedTime().put(uuid, rs.getLong("unrewarded_millis"));
                plugin.setLastRewardDate(uuid, rs.getString("last_reward_date"));
                
                // 重置会话开始时间（避免时间累积）
                plugin.getSessionStartTimes().put(uuid, System.currentTimeMillis());
                
                player.sendMessage(Main.PREFIX + "§a你的数据已从数据库恢复！");
                player.sendMessage(Main.PREFIX + "§7当前状态：");
                player.sendMessage("§7- 连续小时: " + plugin.getConsecutiveHours(uuid));
                player.sendMessage("§7- 最后奖励日期: " + plugin.getLastRewardDate(uuid));
            } else {
                player.sendMessage(Main.PREFIX + "§c找不到你的数据记录！");
            }
        } catch (SQLException e) {
            player.sendMessage(Main.PREFIX + "§c恢复数据时出错！");
            plugin.getLogger().warning("恢复玩家数据出错: " + e.getMessage());
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
            sender.sendMessage("§f/奖咪 恢复 §7- 恢复模拟前的数据");
            sender.sendMessage("§f/奖咪 保持 §7- 保持模拟后的数据");
            sender.sendMessage("§f/奖咪 确认 <编号> §7- 确认模拟选项");
            sender.sendMessage("§f/奖咪 发放 §7- 实际发放模拟奖励");
            sender.sendMessage("§f/奖咪 跳过 §7- 跳过奖励发放");
            sender.sendMessage("");
            sender.sendMessage("§e权限节点: lengrewards.admin");
        }
        sender.sendMessage("§6§l╚══════════════════════════╝");
    }
    
    private void giveActualReward(Player player) {
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
                    // 实际发放奖励
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), 
                        "money give " + player.getName() + " " + proportionalReward);
                    
                    player.sendMessage(Main.PREFIX + "§a已实际发放 §6" + proportionalReward + " §a硬币奖励！");
                    
                    // 清空未奖励时间
                    plugin.getUnrewardedTime().put(uuid, 0L);
                    plugin.getSessionStartTimes().put(uuid, System.currentTimeMillis());
                    
                    // 更新数据库
                    plugin.savePlayerData(uuid, 0);
                }
            }
        } else {
            player.sendMessage(Main.PREFIX + "§c没有可发放的奖励！");
        }
    }
}