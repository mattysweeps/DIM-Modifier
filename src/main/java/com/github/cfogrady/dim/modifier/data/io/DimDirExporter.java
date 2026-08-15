package com.github.cfogrady.dim.modifier.data.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.github.cfogrady.dim.modifier.SpriteImageTranslator;
import com.github.cfogrady.dim.modifier.data.AppState;
import com.github.cfogrady.dim.modifier.data.card.CardData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;

@Slf4j
@RequiredArgsConstructor
public class DimDirExporter {
    private final AppState appState;
    private final SpriteImageTranslator spriteImageTranslator;
    private final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());

    public void exportToDir(File targetDir, Runnable onProgress) throws Exception {
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }
        
        CardData<?, ?, ?> cardData = appState.getCardData();
        
        // Write metadata
        YamlMetaData metaData = new YamlMetaData();
        metaData.setBem(cardData instanceof com.github.cfogrady.dim.modifier.data.bem.BemCardData);
        metaData.setId(cardData.getMetaData().getId());
        metaData.setRevision(cardData.getMetaData().getRevision());
        metaData.setYear(cardData.getMetaData().getYear());
        metaData.setMonth(cardData.getMetaData().getMonth());
        metaData.setDay(cardData.getMetaData().getDay());
        metaData.setOriginalChecksum(cardData.getMetaData().getOriginalChecksum());
        
        objectMapper.writeValue(new File(targetDir, "meta.yaml"), metaData);

        // Logo
        exportSprite(cardData.getCardSprites().getLogo(), new File(targetDir, "logo.png"));

        // Backgrounds
        exportSpriteListAsDir(cardData.getCardSprites().getBackgrounds(), new File(targetDir, "backgrounds"));

        // System
        File systemDir = new File(targetDir, "system");
        systemDir.mkdirs();
        exportSpriteListAsDir(cardData.getCardSprites().getTypes(), new File(systemDir, "types"));
        exportSpriteListAsDir(cardData.getCardSprites().getSmallAttacks(), new File(systemDir, "small_attacks"));
        exportSpriteListAsDir(cardData.getCardSprites().getBigAttacks(), new File(systemDir, "big_attacks"));

        // Egg
        File eggDir = new File(targetDir, "egg");
        eggDir.mkdirs();
        if (cardData.getCardSprites().getEgg() != null && !cardData.getCardSprites().getEgg().isEmpty()) {
            exportSpritesAsSheet(cardData.getCardSprites().getEgg(), new File(eggDir, "sprites.png"));
        }

        // Characters
        File charsDir = new File(targetDir, "characters");
        charsDir.mkdirs();
        for (int i = 0; i < cardData.getCharacters().size(); i++) {
            com.github.cfogrady.dim.modifier.data.card.Character<?, ?> character = cardData.getCharacters().get(i);
            File charDir = new File(charsDir, String.format("%02d", i));
            charDir.mkdirs();

            YamlCharacterStats stats = new YamlCharacterStats();
            stats.setStage(character.getStage());
            stats.setAttribute(character.getAttribute());
            stats.setActivityType(character.getActivityType());
            stats.setSmallAttack(character.getSmallAttack());
            stats.setBigAttack(character.getBigAttack());
            stats.setBp(character.getBp());
            stats.setHp(character.getHp());
            stats.setAp(character.getAp());
            stats.setFirstPoolBattleChance(character.getFirstPoolBattleChance());
            stats.setSecondPoolBattleChance(character.getSecondPoolBattleChance());
            
            if (character instanceof com.github.cfogrady.dim.modifier.data.dim.DimCharacter dimChar) {
                stats.setHoursUntilFusionCheck(dimChar.getHoursUntilFusionCheck());
                stats.setStars(dimChar.getStars());
                stats.setFinishAdventureToUnlock(dimChar.isFinishAdventureToUnlock());
            } else if (character instanceof com.github.cfogrady.dim.modifier.data.bem.BemCharacter bemChar) {
                stats.setThirdPoolBattleChance(bemChar.getThirdPoolBattleChance());
            }

            objectMapper.writeValue(new File(charDir, "meta.yaml"), stats);

            // Transformations
            if (!character.getTransformationEntries().isEmpty()) {
                java.util.List<YamlTransformation> transformations = new java.util.ArrayList<>();
                for (com.github.cfogrady.dim.modifier.data.card.TransformationEntry entry : character.getTransformationEntries()) {
                    YamlTransformation yt = new YamlTransformation();
                    if (entry.getToCharacter() != null) {
                        yt.setToCharacterIndex(cardData.getUuidToCharacterSlot().get(entry.getToCharacter()));
                    }
                    yt.setVitalRequirements(entry.getVitalRequirements());
                    yt.setTrophyRequirement(entry.getTrophyRequirement());
                    yt.setBattleRequirement(entry.getBattleRequirement());
                    yt.setWinRatioRequirement(entry.getWinRatioRequirement());
                    
                    if (entry instanceof com.github.cfogrady.dim.modifier.data.dim.DimTransformationEntity dimEntry) {
                        yt.setHoursUntilTransformation(dimEntry.getHoursUntilTransformation());
                    } else if (entry instanceof com.github.cfogrady.dim.modifier.data.bem.BemTransformationEntry bemEntry) {
                        yt.setMinutesUntilTransformation(bemEntry.getMinutesUntilTransformation());
                        yt.setRequiredCompletedAdventureLevel(bemEntry.getRequiredCompletedAdventureLevel());
                        yt.setSecret(bemEntry.isSecret());
                    }
                    transformations.add(yt);
                }
                objectMapper.writeValue(new File(charDir, "transformations.yaml"), transformations);
            }

            // Fusions
            if (character.getFusions() != null) {
                YamlFusions yf = new YamlFusions();
                if (character.getFusions().getType1FusionResult() != null) {
                    yf.setType1FusionResult(cardData.getUuidToCharacterSlot().get(character.getFusions().getType1FusionResult()));
                }
                if (character.getFusions().getType2FusionResult() != null) {
                    yf.setType2FusionResult(cardData.getUuidToCharacterSlot().get(character.getFusions().getType2FusionResult()));
                }
                if (character.getFusions().getType3FusionResult() != null) {
                    yf.setType3FusionResult(cardData.getUuidToCharacterSlot().get(character.getFusions().getType3FusionResult()));
                }
                if (character.getFusions().getType4FusionResult() != null) {
                    yf.setType4FusionResult(cardData.getUuidToCharacterSlot().get(character.getFusions().getType4FusionResult()));
                }
                objectMapper.writeValue(new File(charDir, "fusions.yaml"), yf);
            }

            // Specific Fusions
            if (character.getSpecificFusions() != null && !character.getSpecificFusions().isEmpty()) {
                java.util.List<YamlSpecificFusion> specificFusions = new java.util.ArrayList<>();
                for (com.github.cfogrady.dim.modifier.data.card.SpecificFusion sf : character.getSpecificFusions()) {
                    YamlSpecificFusion ysf = new YamlSpecificFusion();
                    ysf.setPartnerDimId(sf.getPartnerDimId());
                    ysf.setPartnerDimSlotId(sf.getPartnerDimSlotId());
                    if (sf.getSameBemPartnerCharacter() != null) {
                        ysf.setSameBemPartnerCharacterIndex(cardData.getUuidToCharacterSlot().get(sf.getSameBemPartnerCharacter()));
                    }
                    if (sf.getEvolveToCharacterId() != null) {
                        ysf.setEvolveToCharacterIndex(cardData.getUuidToCharacterSlot().get(sf.getEvolveToCharacterId()));
                    }
                    specificFusions.add(ysf);
                }
                objectMapper.writeValue(new File(charDir, "specific_fusions.yaml"), specificFusions);
            }

            if (character.getSprites().size() == 14) {
                exportSprite(character.getSprites().get(0), new File(charDir, "name.png"));
                spriteImageTranslator.exportCharacterSpriteSheet(new File(charDir, "sprites.png"), character.getSprites().subList(1, 14));
            } else {
                exportSprite(character.getSprites().get(0), new File(charDir, "name.png"));
                spriteImageTranslator.exportBabySpriteSheet(new File(charDir, "sprites.png"), character);
            }
        }

        // Adventures
        File advDir = new File(targetDir, "adventures");
        advDir.mkdirs();
        for (int i = 0; i < cardData.getAdventures().size(); i++) {
            com.github.cfogrady.dim.modifier.data.card.Adventure adventure = cardData.getAdventures().get(i);
            File aDir = new File(advDir, String.format("%02d", i));
            aDir.mkdirs();

            YamlAdventure ya = new YamlAdventure();
            ya.setSteps(adventure.getSteps());
            ya.setBossHp(adventure.getBossHp());
            ya.setBossAp(adventure.getBossAp());
            ya.setBossBp(adventure.getBossBp());

            if (adventure.getBossId() != null) {
                ya.setBossCharacterIndex(cardData.getUuidToCharacterSlot().get(adventure.getBossId()));
            }

            if (adventure instanceof com.github.cfogrady.dim.modifier.data.bem.BemAdventure bemAdv) {
                ya.setSmallAttackId(bemAdv.getSmallAttackId());
                ya.setBigAttackId(bemAdv.getBigAttackId());
                ya.setWalkingBackground(bemAdv.getWalkingBackground());
                ya.setBattleBackground(bemAdv.getBattleBackground());
                ya.setShowBossIdentiy(bemAdv.isShowBossIdentiy());
                if (bemAdv.getGiftCharacter() != null) {
                    ya.setGiftCharacterIndex(cardData.getUuidToCharacterSlot().get(bemAdv.getGiftCharacter()));
                }
            }

            objectMapper.writeValue(new File(aDir, "meta.yaml"), ya);
        }

        if (onProgress != null) {
            onProgress.run();
        }
    }

    private void exportSprite(com.github.cfogrady.vb.dim.sprite.SpriteData.Sprite sprite, File file) throws Exception {
        if (sprite == null) return;
        javax.imageio.ImageIO.write(SpriteImageTranslator.createBufferedImage(sprite), "PNG", file);
    }

    private void exportSpriteListAsDir(java.util.List<com.github.cfogrady.vb.dim.sprite.SpriteData.Sprite> sprites, File dir) throws Exception {
        if (sprites == null || sprites.isEmpty()) return;
        dir.mkdirs();
        for (int i = 0; i < sprites.size(); i++) {
            exportSprite(sprites.get(i), new File(dir, String.format("%02d.png", i)));
        }
    }

    private void exportSpritesAsSheet(java.util.List<com.github.cfogrady.vb.dim.sprite.SpriteData.Sprite> sprites, File file) throws Exception {
        if (sprites == null || sprites.isEmpty()) return;
        int totalWidth = sprites.stream().mapToInt(com.github.cfogrady.vb.dim.sprite.SpriteData.Sprite::getWidth).sum();
        int maxHeight = sprites.stream().mapToInt(com.github.cfogrady.vb.dim.sprite.SpriteData.Sprite::getHeight).max().orElse(0);
        java.awt.image.BufferedImage sheet = new java.awt.image.BufferedImage(totalWidth, maxHeight, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics g = sheet.getGraphics();
        int x = 0;
        for (com.github.cfogrady.vb.dim.sprite.SpriteData.Sprite sprite : sprites) {
            g.drawImage(SpriteImageTranslator.createBufferedImage(sprite), x, 0, null);
            x += sprite.getWidth();
        }
        javax.imageio.ImageIO.write(sheet, "PNG", file);
    }
}
