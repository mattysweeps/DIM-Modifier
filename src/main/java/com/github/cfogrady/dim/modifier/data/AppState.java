package com.github.cfogrady.dim.modifier.data;


import com.github.cfogrady.dim.modifier.data.bem.BemCardData;
import com.github.cfogrady.dim.modifier.data.card.CardData;
import com.github.cfogrady.dim.modifier.data.card.Character;
import com.github.cfogrady.dim.modifier.data.firmware.FirmwareData;
import com.github.cfogrady.vb.dim.sprite.SpriteData;
import lombok.Data;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Data
public class AppState {
    public static final int SELECTION_SPRITE_IDX = 1;

    private FirmwareData firmwareData;
    private CardData<?, ?, ?> cardData;
    private File lastOpenedFilePath;
    private int selectedBackgroundIndex;

    private static final String LAST_OPENED_FILE_PATH = "LAST_OPENED_FILE_PATH";
    private static final java.util.prefs.Preferences PREFS = java.util.prefs.Preferences.userNodeForPackage(AppState.class);

    public File getLastOpenedFilePath() {
        if (lastOpenedFilePath == null) {
            String path = PREFS.get(LAST_OPENED_FILE_PATH, null);
            if (path != null) {
                lastOpenedFilePath = new File(path);
            }
        }
        return lastOpenedFilePath;
    }

    public void setLastOpenedFilePath(File file) {
        this.lastOpenedFilePath = file;
        if (file != null) {
            PREFS.put(LAST_OPENED_FILE_PATH, file.getAbsolutePath());
        } else {
            PREFS.remove(LAST_OPENED_FILE_PATH);
        }
    }

    public File getLastOpenedDirectory() {
        File file = getLastOpenedFilePath();
        if (file == null) {
            return null;
        }
        if (file.isDirectory()) {
            return file;
        }
        return file.getParentFile();
    }

    public SpriteData.Sprite getSelectedBackground() {
        return cardData.getCardSprites().getBackgrounds().get(selectedBackgroundIndex);
    }

    public List<SpriteData.Sprite> getIdleForCharacters() {
        List<SpriteData.Sprite> idleSprites = new ArrayList<>();
        for(Character<?, ?> character : getCardData().getCharacters()) {
            idleSprites.add(character.getSprites().get(SELECTION_SPRITE_IDX));
        }
        return idleSprites;
    }

    public Character<?, ?> getCharacter(int characterIndex) {
        return cardData.getCharacters().get(characterIndex);
    }

    public List<SpriteData.Sprite> getAttributes() {
        if(cardData instanceof BemCardData bemCardData) {
            return bemCardData.getCardSprites().getTypes();
        } else {
            return firmwareData.getTypes();
        }
    }

    public void setBackgroundSprite(SpriteData.Sprite sprite) {
        getCardData().getCardSprites().getBackgrounds().set(getSelectedBackgroundIndex(), sprite);
    }
}
