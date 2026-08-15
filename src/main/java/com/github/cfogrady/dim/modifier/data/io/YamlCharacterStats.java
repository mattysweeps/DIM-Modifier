package com.github.cfogrady.dim.modifier.data.io;

import lombok.Data;

@Data
public class YamlCharacterStats {
    private int stage;
    private int attribute;
    private int activityType;
    private int smallAttack;
    private int bigAttack;
    private int bp;
    private int hp;
    private int ap;
    private Integer firstPoolBattleChance;
    private Integer secondPoolBattleChance;
    private Integer thirdPoolBattleChance;

    private Integer hoursUntilFusionCheck;
    private int stars;
    private boolean finishAdventureToUnlock;
}
