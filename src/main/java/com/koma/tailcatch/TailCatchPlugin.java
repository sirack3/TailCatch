package com.koma.tailcatch;

import org.bukkit.plugin.java.JavaPlugin;

public class TailCatchPlugin extends JavaPlugin {
    private GameManager gameManager;

    @Override
    public void onEnable() {
        gameManager = new GameManager(this);

        // ProtocolLib 기반 네임태그 매니저 초기화
        NameTagManager.init(this);
        
        TailCatchCommand commandHandler = new TailCatchCommand(gameManager);
        getCommand("꼬리잡기").setExecutor(commandHandler);
        getCommand("꼬리잡기").setTabCompleter(commandHandler);
        
        NicknameCommand nicknameHandler = new NicknameCommand();
        getCommand("닉네임").setExecutor(nicknameHandler);
        getCommand("닉네임").setTabCompleter(nicknameHandler);
        
        getServer().getPluginManager().registerEvents(new GameListener(gameManager), this);
        
        getLogger().info("TailCatch 꼬리잡기 플러그인이 활성화 되었습니다!");
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            gameManager.stopGame();
        }
        NameTagManager.shutdown();
        getLogger().info("TailCatch 꼬리잡기 플러그인이 비활성화 되었습니다!");
    }
}
