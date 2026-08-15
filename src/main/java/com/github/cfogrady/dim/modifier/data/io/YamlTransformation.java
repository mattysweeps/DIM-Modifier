package com.github.cfogrady.dim.modifier.data.io;

import lombok.Data;

@Data
public class YamlTransformation {
    private Integer toCharacterIndex;
    private int vitalRequirements;
    private int trophyRequirement;
    private int battleRequirement;
    private int winRatioRequirement;

    private int hoursUntilTransformation;
    private int minutesUntilTransformation;
    private int requiredCompletedAdventureLevel;
    private boolean isSecret;
}
