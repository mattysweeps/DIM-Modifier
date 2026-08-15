package com.github.cfogrady.dim.modifier.data.io;

import lombok.Data;

@Data
public class YamlAdventure {
    private int steps;
    private Integer bossCharacterIndex;
    private int bossDp;
    private int bossHp;
    private int bossAp;
    private int bossBp;

    private Integer smallAttackId;
    private Integer bigAttackId;
    private int walkingBackground;
    private int battleBackground;
    private boolean showBossIdentiy;
    private Integer giftCharacterIndex;
}
