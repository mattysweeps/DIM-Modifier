package com.github.cfogrady.dim.modifier.data.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.github.cfogrady.dim.modifier.SpriteImageTranslator;
import com.github.cfogrady.dim.modifier.data.AppState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class DimDirImporter {
    private final AppState appState;
    private final SpriteImageTranslator spriteImageTranslator;
    private final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());

    public List<String> validateImport(File sourceDir) {
        List<String> errors = new ArrayList<>();
        if (!new File(sourceDir, "meta.yaml").exists()) {
            errors.add("Missing meta.yaml at root directory.");
        }
        // TODO: add more validation
        return errors;
    }

    public void importFromDir(File sourceDir, Runnable onProgress) throws Exception {
        YamlMetaData metaData = objectMapper.readValue(new File(sourceDir, "meta.yaml"), YamlMetaData.class);
        
        com.github.cfogrady.dim.modifier.data.card.CardSprites.CardSpritesBuilder spritesBuilder = com.github.cfogrady.dim.modifier.data.card.CardSprites.builder();
        spritesBuilder.logo(spriteImageTranslator.loadSprite(new File(sourceDir, "logo.png")));
        
        // Backgrounds
        spritesBuilder.backgrounds(loadSpriteListFromDir(new File(sourceDir, "backgrounds")));
        
        // System
        File systemDir = new File(sourceDir, "system");
        spritesBuilder.types(loadSpriteListFromDir(new File(systemDir, "types")));
        spritesBuilder.smallAttacks(loadSpriteListFromDir(new File(systemDir, "small_attacks")));
        spritesBuilder.bigAttacks(loadSpriteListFromDir(new File(systemDir, "big_attacks")));

        // Egg
        File eggDir = new File(sourceDir, "egg");
        File eggSprites = new File(eggDir, "sprites.png");
        if (eggSprites.exists()) {
            spritesBuilder.egg(loadEggSprites(eggSprites));
        }

        com.github.cfogrady.dim.modifier.data.card.MetaData cardMetaData = com.github.cfogrady.dim.modifier.data.card.MetaData.builder()
            .id(metaData.getId())
            .revision(metaData.getRevision())
            .year(metaData.getYear())
            .month(metaData.getMonth())
            .day(metaData.getDay())
            .originalChecksum(metaData.getOriginalChecksum())
            .build();

        if (metaData.isBem()) {
            com.github.cfogrady.dim.modifier.data.bem.BemCardData cardData = com.github.cfogrady.dim.modifier.data.bem.BemCardData.builder()
                .metaData(cardMetaData)
                .cardSprites(spritesBuilder.build())
                .characters(new ArrayList<>())
                .adventures(new ArrayList<>())
                .uuidToCharacterSlot(new java.util.HashMap<>())
                .build();
            parseCharactersAndAdventures(sourceDir, cardData, true);
            appState.setCardData(cardData);
        } else {
            com.github.cfogrady.dim.modifier.data.dim.DimCardData cardData = com.github.cfogrady.dim.modifier.data.dim.DimCardData.builder()
                .metaData(cardMetaData)
                .cardSprites(spritesBuilder.build())
                .characters(new ArrayList<>())
                .adventures(new ArrayList<>())
                .uuidToCharacterSlot(new java.util.HashMap<>())
                .build();
            parseCharactersAndAdventures(sourceDir, cardData, false);
            appState.setCardData(cardData);
        }

        if (onProgress != null) {
            onProgress.run();
        }
    }

    private List<com.github.cfogrady.vb.dim.sprite.SpriteData.Sprite> loadSpriteListFromDir(File dir) throws Exception {
        List<com.github.cfogrady.vb.dim.sprite.SpriteData.Sprite> list = new ArrayList<>();
        if (!dir.exists() || !dir.isDirectory()) return list;
        
        File[] files = dir.listFiles((d, name) -> name.endsWith(".png"));
        if (files == null) return list;
        
        java.util.Arrays.sort(files, java.util.Comparator.comparing(File::getName));
        for (File file : files) {
            list.add(spriteImageTranslator.loadSprite(file));
        }
        return list;
    }

    private List<com.github.cfogrady.vb.dim.sprite.SpriteData.Sprite> loadEggSprites(File eggSpritesFile) throws Exception {
        List<com.github.cfogrady.vb.dim.sprite.SpriteData.Sprite> list = new ArrayList<>();
        if (!eggSpritesFile.exists()) return list;
        
        java.awt.image.BufferedImage sheet = javax.imageio.ImageIO.read(eggSpritesFile);
        int expectedWidth = 32;
        int expectedHeight = 40;
        int count = sheet.getWidth() / expectedWidth;
        
        for (int i = 0; i < count; i++) {
            java.awt.image.BufferedImage img = sheet.getSubimage(i * expectedWidth, 0, expectedWidth, expectedHeight);
            byte[] pixelData = convertToR5G6B5(img, expectedWidth, expectedHeight);
            list.add(com.github.cfogrady.vb.dim.sprite.SpriteData.Sprite.builder()
                .width(expectedWidth).height(expectedHeight).pixelData(pixelData).build());
        }
        return list;
    }

    private void parseCharactersAndAdventures(File sourceDir, com.github.cfogrady.dim.modifier.data.card.CardData<?, ?, ?> cardData, boolean isBem) throws Exception {
        File charsDir = new File(sourceDir, "characters");
        if (!charsDir.exists()) return;
        
        File[] charDirs = charsDir.listFiles(File::isDirectory);
        if (charDirs == null) return;
        java.util.Arrays.sort(charDirs, java.util.Comparator.comparing(File::getName));
        
        java.util.Map<Integer, java.util.UUID> indexToUuid = new java.util.HashMap<>();
        for (int i = 0; i < charDirs.length; i++) {
            indexToUuid.put(i, java.util.UUID.randomUUID());
        }
        
        List<com.github.cfogrady.dim.modifier.data.card.Character<?, ?>> characters = new ArrayList<>();
        
        for (int i = 0; i < charDirs.length; i++) {
            File charDir = charDirs[i];
            YamlCharacterStats stats = objectMapper.readValue(new File(charDir, "meta.yaml"), YamlCharacterStats.class);
            
            com.github.cfogrady.dim.modifier.data.card.Character character;
            if (isBem) {
                com.github.cfogrady.dim.modifier.data.bem.BemCharacter bemChar = com.github.cfogrady.dim.modifier.data.bem.BemCharacter.builder()
                    .id(indexToUuid.get(i))
                    .stage(stats.getStage())
                    .attribute(stats.getAttribute())
                    .activityType(stats.getActivityType())
                    .smallAttack(stats.getSmallAttack())
                    .bigAttack(stats.getBigAttack())
                    .bp(stats.getBp())
                    .hp(stats.getHp())
                    .ap(stats.getAp())
                    .firstPoolBattleChance(stats.getFirstPoolBattleChance())
                    .secondPoolBattleChance(stats.getSecondPoolBattleChance())
                    .thirdPoolBattleChance(stats.getThirdPoolBattleChance())
                    .sprites(new ArrayList<>())
                    .transformationEntries(new ArrayList<>())
                    .fusions(com.github.cfogrady.dim.modifier.data.card.Fusions.builder().build())
                    .specificFusions(new ArrayList<>())
                    .build();
                character = bemChar;
            } else {
                com.github.cfogrady.dim.modifier.data.dim.DimCharacter dimChar = com.github.cfogrady.dim.modifier.data.dim.DimCharacter.builder()
                    .id(indexToUuid.get(i))
                    .stage(stats.getStage())
                    .attribute(stats.getAttribute())
                    .activityType(stats.getActivityType())
                    .smallAttack(stats.getSmallAttack())
                    .bigAttack(stats.getBigAttack())
                    .bp(stats.getBp())
                    .hp(stats.getHp())
                    .ap(stats.getAp())
                    .firstPoolBattleChance(stats.getFirstPoolBattleChance())
                    .secondPoolBattleChance(stats.getSecondPoolBattleChance())
                    .hoursUntilFusionCheck(stats.getHoursUntilFusionCheck() != null ? stats.getHoursUntilFusionCheck() : 0)
                    .stars(stats.getStars())
                    .finishAdventureToUnlock(stats.isFinishAdventureToUnlock())
                    .sprites(new ArrayList<>())
                    .transformationEntries(new ArrayList<>())
                    .fusions(com.github.cfogrady.dim.modifier.data.card.Fusions.builder().build())
                    .specificFusions(new ArrayList<>())
                    .build();
                character = dimChar;
            }
            
            // Load Sprites
            com.github.cfogrady.vb.dim.sprite.SpriteData.Sprite nameSprite = spriteImageTranslator.loadSprite(new File(charDir, "name.png"));
            character.getSprites().add(nameSprite);
            
            // Pad dummy sprites so loadSpriteSheet can overwrite them
            int numSprites = character.getStage() < 2 ? 6 : 13;
            for (int s = 0; s < numSprites; s++) {
                character.getSprites().add(spriteImageTranslator.getBlankCharacterSprite());
            }
            
            File spritesFile = new File(charDir, "sprites.png");
            if (spritesFile.exists()) {
                spriteImageTranslator.loadSpriteSheet(character, spritesFile);
            }
            
            // Load Transformations
            File transFile = new File(charDir, "transformations.yaml");
            if (transFile.exists()) {
                java.util.List<YamlTransformation> ytList = objectMapper.readValue(transFile, objectMapper.getTypeFactory().constructCollectionType(List.class, YamlTransformation.class));
                for (YamlTransformation yt : ytList) {
                    java.util.UUID toCharacter = yt.getToCharacterIndex() != null ? indexToUuid.get(yt.getToCharacterIndex()) : null;
                    if (isBem) {
                        character.getTransformationEntries().add(com.github.cfogrady.dim.modifier.data.bem.BemTransformationEntry.builder()
                            .toCharacter(toCharacter)
                            .vitalRequirements(yt.getVitalRequirements())
                            .trophyRequirement(yt.getTrophyRequirement())
                            .battleRequirement(yt.getBattleRequirement())
                            .winRatioRequirement(yt.getWinRatioRequirement())
                            .minutesUntilTransformation(yt.getMinutesUntilTransformation())
                            .requiredCompletedAdventureLevel(yt.getRequiredCompletedAdventureLevel())
                            .isSecret(yt.isSecret())
                            .build());
                    } else {
                        character.getTransformationEntries().add(com.github.cfogrady.dim.modifier.data.dim.DimTransformationEntity.builder()
                            .toCharacter(toCharacter)
                            .vitalRequirements(yt.getVitalRequirements())
                            .trophyRequirement(yt.getTrophyRequirement())
                            .battleRequirement(yt.getBattleRequirement())
                            .winRatioRequirement(yt.getWinRatioRequirement())
                            .hoursUntilTransformation(yt.getHoursUntilTransformation())
                            .build());
                    }
                }
            }
            
            // Load Fusions
            File fusionsFile = new File(charDir, "fusions.yaml");
            if (fusionsFile.exists()) {
                YamlFusions yf = objectMapper.readValue(fusionsFile, YamlFusions.class);
                character.getFusions().setType1FusionResult(yf.getType1FusionResult() != null ? indexToUuid.get(yf.getType1FusionResult()) : null);
                character.getFusions().setType2FusionResult(yf.getType2FusionResult() != null ? indexToUuid.get(yf.getType2FusionResult()) : null);
                character.getFusions().setType3FusionResult(yf.getType3FusionResult() != null ? indexToUuid.get(yf.getType3FusionResult()) : null);
                character.getFusions().setType4FusionResult(yf.getType4FusionResult() != null ? indexToUuid.get(yf.getType4FusionResult()) : null);
            }
            
            // Load Specific Fusions
            File specificFusionsFile = new File(charDir, "specific_fusions.yaml");
            if (specificFusionsFile.exists()) {
                java.util.List<YamlSpecificFusion> ysfList = objectMapper.readValue(specificFusionsFile, objectMapper.getTypeFactory().constructCollectionType(List.class, YamlSpecificFusion.class));
                for (YamlSpecificFusion ysf : ysfList) {
                    character.getSpecificFusions().add(com.github.cfogrady.dim.modifier.data.card.SpecificFusion.builder()
                        .partnerDimId(ysf.getPartnerDimId())
                        .partnerDimSlotId(ysf.getPartnerDimSlotId())
                        .sameBemPartnerCharacter(ysf.getSameBemPartnerCharacterIndex() != null ? indexToUuid.get(ysf.getSameBemPartnerCharacterIndex()) : null)
                        .evolveToCharacterId(ysf.getEvolveToCharacterIndex() != null ? indexToUuid.get(ysf.getEvolveToCharacterIndex()) : null)
                        .build());
                }
            }
            
            characters.add(character);
            cardData.getUuidToCharacterSlot().put(character.getId(), i);
        }
        
        cardData.getCharacters().addAll((List) characters);
        
        // Parse Adventures
        File advsDir = new File(sourceDir, "adventures");
        if (!advsDir.exists()) return;
        
        File[] advDirs = advsDir.listFiles(File::isDirectory);
        if (advDirs == null) return;
        java.util.Arrays.sort(advDirs, java.util.Comparator.comparing(File::getName));
        
        for (int i = 0; i < advDirs.length; i++) {
            File aDir = advDirs[i];
            YamlAdventure ya = objectMapper.readValue(new File(aDir, "meta.yaml"), YamlAdventure.class);
            
            if (isBem) {
                com.github.cfogrady.dim.modifier.data.bem.BemAdventure ba = com.github.cfogrady.dim.modifier.data.bem.BemAdventure.builder()
                    .steps(ya.getSteps())
                    .bossId(ya.getBossCharacterIndex() != null ? indexToUuid.get(ya.getBossCharacterIndex()) : null)
                    .bossBp(ya.getBossBp())
                    .bossHp(ya.getBossHp())
                    .bossAp(ya.getBossAp())
                    .smallAttackId(ya.getSmallAttackId())
                    .bigAttackId(ya.getBigAttackId())
                    .walkingBackground(ya.getWalkingBackground())
                    .battleBackground(ya.getBattleBackground())
                    .showBossIdentiy(ya.isShowBossIdentiy())
                    .giftCharacter(ya.getGiftCharacterIndex() != null ? indexToUuid.get(ya.getGiftCharacterIndex()) : null)
                    .build();
                ((List<com.github.cfogrady.dim.modifier.data.card.Adventure>)cardData.getAdventures()).add(ba);
            } else {
                com.github.cfogrady.dim.modifier.data.card.Adventure da = com.github.cfogrady.dim.modifier.data.card.Adventure.builder()
                    .steps(ya.getSteps())
                    .bossId(ya.getBossCharacterIndex() != null ? indexToUuid.get(ya.getBossCharacterIndex()) : null)
                    .bossBp(ya.getBossBp())
                    .bossHp(ya.getBossHp())
                    .bossAp(ya.getBossAp())
                    .build();
                ((List<com.github.cfogrady.dim.modifier.data.card.Adventure>)cardData.getAdventures()).add(da);
            }
        }
    }

    private byte[] convertToR5G6B5(java.awt.image.BufferedImage img, int width, int height) {
        javafx.scene.image.Image fxImage = javafx.embed.swing.SwingFXUtils.toFXImage(img, null);
        javafx.scene.image.PixelReader pixelReader = fxImage.getPixelReader();
        byte[] bytes = new byte[width*height*2];
        for(int y = 0; y < height; y++) {
            for(int x = 0; x < width; x++) {
                javafx.scene.paint.Color color = pixelReader.getColor(x, y);
                int red, green, blue;
                if(color.getOpacity() == 0.0) {
                    red = 0; blue = 0; green = 63;
                } else {
                    red = (int) Math.floor(color.getRed() * 31.0);
                    green = (int) Math.floor(color.getGreen() * 63.0);
                    blue = (int) Math.floor(color.getBlue() * 31.0);
                }
                byte byte0 = (byte) (((red & 0xFF) << 3) | ((green & 0xFF) >> 3));
                byte byte1 = (byte) (((green & 0xFF) << 5) | (blue & 0xFF));
                int index = (y * width + x) * 2;
                bytes[index] = byte1;
                bytes[index + 1] = byte0;
            }
        }
        return bytes;
    }
}
