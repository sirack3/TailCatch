package com.koma.tailcatch;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class NicknameCommand implements CommandExecutor, TabCompleter {

    // 지원하는 색깔 목록 (한국어 이름 → ChatColor 매핑)
    private static final Map<String, ChatColor> COLOR_MAP = new java.util.LinkedHashMap<>();

    static {
        COLOR_MAP.put("빨강", ChatColor.RED);
        COLOR_MAP.put("진빨강", ChatColor.DARK_RED);
        COLOR_MAP.put("주황", ChatColor.GOLD);
        COLOR_MAP.put("노랑", ChatColor.YELLOW);
        COLOR_MAP.put("초록", ChatColor.GREEN);
        COLOR_MAP.put("진초록", ChatColor.DARK_GREEN);
        COLOR_MAP.put("하늘", ChatColor.AQUA);
        COLOR_MAP.put("진하늘", ChatColor.DARK_AQUA);
        COLOR_MAP.put("파랑", ChatColor.BLUE);
        COLOR_MAP.put("진파랑", ChatColor.DARK_BLUE);
        COLOR_MAP.put("보라", ChatColor.LIGHT_PURPLE);
        COLOR_MAP.put("진보라", ChatColor.DARK_PURPLE);
        COLOR_MAP.put("분홍", ChatColor.LIGHT_PURPLE);
        COLOR_MAP.put("흰색", ChatColor.WHITE);
        COLOR_MAP.put("회색", ChatColor.GRAY);
        COLOR_MAP.put("진회색", ChatColor.DARK_GRAY);
        COLOR_MAP.put("검정", ChatColor.BLACK);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "플레이어만 사용할 수 있는 명령어입니다.");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "사용법: /닉네임 <이름> <색깔>");
            return true;
        }

        String name = args[0];
        String colorName = args[1];

        ChatColor color = COLOR_MAP.get(colorName);
        if (color == null) {
            player.sendMessage(ChatColor.RED + "알 수 없는 색깔입니다. 탭을 눌러 색깔 목록을 확인하세요.");
            return true;
        }

        String coloredName = color + name;

        // NameTagManager를 통해 완전히 영어 이름 숨기고 커스텀 이름 적용
        NameTagManager.setCustomName(player, coloredName);

        player.sendMessage(org.bukkit.ChatColor.GREEN + "닉네임이 " + coloredName + org.bukkit.ChatColor.GREEN + " 으로 변경되었습니다!");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            // 첫 번째 인자: 이름 힌트
            suggestions.add("<이름>");
        } else if (args.length == 2) {
            // 두 번째 인자: 색깔 자동완성
            String input = args[1].toLowerCase();
            for (String colorName : COLOR_MAP.keySet()) {
                if (colorName.startsWith(input)) {
                    suggestions.add(colorName);
                }
            }
        }

        return suggestions;
    }

    public static ChatColor getChatColorFromDisplayName(String displayName) {
        if (displayName == null || displayName.isEmpty()) return ChatColor.WHITE;
        for (int i = 0; i < displayName.length() - 1; i++) {
            if (displayName.charAt(i) == ChatColor.COLOR_CHAR) {
                ChatColor c = ChatColor.getByChar(displayName.charAt(i + 1));
                if (c != null && c.isColor()) {
                    return c;
                }
            }
        }
        return ChatColor.WHITE;
    }

    public static org.bukkit.Color getColorFromDisplayName(String displayName) {
        if (displayName == null || displayName.isEmpty()) {
            return getRandomColor();
        }
        
        String lastColors = ChatColor.getLastColors(displayName);
        if (lastColors.isEmpty()) {
            return getRandomColor();
        }

        char colorChar = lastColors.charAt(lastColors.length() - 1);
        ChatColor color = ChatColor.getByChar(colorChar);
        if (color == null) {
            return getRandomColor();
        }
        
        switch (color) {
            case RED: return org.bukkit.Color.RED;
            case DARK_RED: return org.bukkit.Color.MAROON;
            case GOLD: return org.bukkit.Color.ORANGE;
            case YELLOW: return org.bukkit.Color.YELLOW;
            case GREEN: return org.bukkit.Color.LIME;
            case DARK_GREEN: return org.bukkit.Color.GREEN;
            case AQUA: return org.bukkit.Color.AQUA;
            case DARK_AQUA: return org.bukkit.Color.TEAL;
            case BLUE: return org.bukkit.Color.BLUE;
            case DARK_BLUE: return org.bukkit.Color.NAVY;
            case LIGHT_PURPLE: return org.bukkit.Color.FUCHSIA;
            case DARK_PURPLE: return org.bukkit.Color.PURPLE;
            case WHITE: return org.bukkit.Color.WHITE;
            case GRAY: return org.bukkit.Color.SILVER;
            case DARK_GRAY: return org.bukkit.Color.GRAY;
            case BLACK: return org.bukkit.Color.BLACK;
            default:
                return getRandomColor();
        }
    }

    private static org.bukkit.Color getRandomColor() {
        java.util.Random r = new java.util.Random();
        return org.bukkit.Color.fromRGB(r.nextInt(256), r.nextInt(256), r.nextInt(256));
    }
}
