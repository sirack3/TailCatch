package com.koma.tailcatch;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.CommandSender;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.Sound;

import java.util.ArrayList;
import java.util.List;

public class TailCatchCommand implements CommandExecutor, TabCompleter {
    private final GameManager gameManager;

    public TailCatchCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player p) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "사용법: /꼬리잡기 <시작|종료|테스트시작|테스트종료|월드보더>");
            return true;
        }

        if (!sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "오피 권한이 필요합니다.");
            return true;
        }

        if (args[0].equalsIgnoreCase("시작")) {
            gameManager.startGame();
        } else if (args[0].equalsIgnoreCase("종료")) {
            gameManager.stopGame();
        } else if (args[0].equalsIgnoreCase("월드보더")) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.RED + "사용법: /꼬리잡기 월드보더 <크기>");
                return true;
            }
            try {
                double size = Double.parseDouble(args[1]);
                if (size < 10) {
                    sender.sendMessage(ChatColor.RED + "최소 10블록 이상의 크기를 설정해야 합니다.");
                    return true;
                }
                gameManager.setWorldBorderSize(size);
                sender.sendMessage(ChatColor.GREEN + "월드보더 크기가 " + size + "(으)로 설정되었습니다. 게임 시작 시 적용됩니다.");
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "올바른 숫자를 입력해주세요.");
            }
        } else if (args[0].equalsIgnoreCase("테스트시작")) {
            gameManager.startTestGame();
        } else if (args[0].equalsIgnoreCase("테스트종료")) {
            gameManager.stopGame();
        } else {
            sender.sendMessage(ChatColor.RED + "알 수 없는 명령어입니다.");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String arg = args[0].toLowerCase();
            if ("시작".startsWith(arg)) completions.add("시작");
            if ("종료".startsWith(arg)) completions.add("종료");
            if ("테스트시작".startsWith(arg)) completions.add("테스트시작");
            if ("테스트종료".startsWith(arg)) completions.add("테스트종료");
            if ("월드보더".startsWith(arg)) completions.add("월드보더");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("월드보더")) {
            String arg = args[1].toLowerCase();
            if ("100".startsWith(arg)) completions.add("100");
            if ("500".startsWith(arg)) completions.add("500");
            if ("1000".startsWith(arg)) completions.add("1000");
            if ("2000".startsWith(arg)) completions.add("2000");
        }
        return completions;
    }
}
