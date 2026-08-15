package com.github.cfogrady.dim.modifier.data.io;

import lombok.Data;

@Data
public class YamlSpecificFusion {
    private int partnerDimId;
    private Integer partnerDimSlotId;
    private Integer sameBemPartnerCharacterIndex;
    private Integer evolveToCharacterIndex;
}
