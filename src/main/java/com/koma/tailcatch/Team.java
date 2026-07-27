package com.koma.tailcatch;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Color;

public class Team {
    private final UUID masterId;
    private final Set<UUID> slaves;
    private Team targetTeam;
    private String teamName = "";
    private Color teamColor;

    public Team(UUID masterId) {
        this.masterId = masterId;
        this.slaves = new HashSet<>();
    }

    public UUID getMasterId() {
        return masterId;
    }

    public Set<UUID> getSlaves() {
        return slaves;
    }

    public void addSlave(UUID slaveId) {
        slaves.add(slaveId);
    }

    public void addSlaves(Set<UUID> newSlaves) {
        slaves.addAll(newSlaves);
    }

    public Team getTargetTeam() {
        return targetTeam;
    }

    public void setTargetTeam(Team targetTeam) {
        this.targetTeam = targetTeam;
    }

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public Color getTeamColor() {
        return teamColor;
    }

    public void setTeamColor(Color teamColor) {
        this.teamColor = teamColor;
    }
}
