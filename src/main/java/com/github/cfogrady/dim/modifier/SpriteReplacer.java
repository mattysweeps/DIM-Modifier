package com.github.cfogrady.dim.modifier;

import com.github.cfogrady.dim.modifier.data.AppState;
import com.github.cfogrady.dim.modifier.data.card.CardSprites;
import com.github.cfogrady.vb.dim.sprite.SpriteData;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.paint.Color;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;

@Slf4j
@RequiredArgsConstructor
public class SpriteReplacer {
    private final AppState appState;
    private final Stage stage;
    private final SpriteImageTranslator spriteImageTranslator;

    public SpriteData.Sprite replaceSprite(Integer expectedWidth, Integer expectedHeight, File file) {
        if(file == null) {
            return null;
        }
        SpriteData.Sprite newSprite = loadSpriteFromFile(file);
        boolean validReplacement = true;
        if(expectedWidth != null && expectedWidth != newSprite.getWidth()) {
            validReplacement = false;
        }
        if(expectedHeight != null && expectedHeight != newSprite.getHeight()) {
            validReplacement = false;
        }
        if(validReplacement) {
            return newSprite;
        }
        log.warn("Selected sprite doesn't match expected dimensions");
        return null;
    }

    public SpriteData.Sprite loadSpriteFromFileChooser() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select sprite replacement.");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image format", "*.png", "*.bmp"));
        File lastDir = appState.getLastOpenedDirectory();
        if (lastDir != null && lastDir.exists()) {
            fileChooser.setInitialDirectory(lastDir);
        }
        File file = fileChooser.showOpenDialog(stage);
        if(file != null) {
            appState.setLastOpenedFilePath(file.getParentFile());
            return loadSpriteFromFile(file);
        }
        return null;
    }

    public SpriteData.Sprite replaceSprite(SpriteData.Sprite sprite, boolean sameWidth, boolean sameHeight) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select sprite replacement. Should be " + sprite.getWidth() + " x " + sprite.getHeight());
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image format", "*.png", "*.bmp"));
        File lastDir2 = appState.getLastOpenedDirectory();
        if (lastDir2 != null && lastDir2.exists()) {
            fileChooser.setInitialDirectory(lastDir2);
        }
        File file = fileChooser.showOpenDialog(stage);
        if(file != null) {
            appState.setLastOpenedFilePath(file.getParentFile());
        }
        return replaceSprite(sameWidth ? sprite.getWidth() : null, sameHeight ? sprite.getHeight() : null, file);
    }

    public SpriteData.Sprite loadSpriteFromFile(File file) {
        return spriteImageTranslator.loadSprite(file);
    }

    public void handleSelectAndEdit(javafx.scene.input.MouseEvent event,
                                    java.util.function.Supplier<SpriteData.Sprite> currentSpriteSupplier,
                                    java.util.function.Supplier<SpriteData.Sprite> replaceSupplier,
                                    java.util.function.Consumer<SpriteData.Sprite> onNewSprite) {
        if (event.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
            javafx.scene.control.ContextMenu contextMenu = new javafx.scene.control.ContextMenu();
            javafx.scene.control.MenuItem replaceItem = new javafx.scene.control.MenuItem("Replace");
            replaceItem.setOnAction(e -> {
                SpriteData.Sprite newSprite = replaceSupplier.get();
                if (newSprite != null) {
                    onNewSprite.accept(newSprite);
                }
            });
            javafx.scene.control.MenuItem editItem = new javafx.scene.control.MenuItem("Edit");
            editItem.setOnAction(e -> {
                openSpriteEditor(currentSpriteSupplier.get(), onNewSprite);
            });
            contextMenu.getItems().addAll(replaceItem, editItem);
            contextMenu.show((javafx.scene.Node) event.getSource(), event.getScreenX(), event.getScreenY());
        } else if (event.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
            SpriteData.Sprite newSprite = replaceSupplier.get();
            if (newSprite != null) {
                onNewSprite.accept(newSprite);
            }
        }
    }

    private void openSpriteEditor(SpriteData.Sprite currentSprite, java.util.function.Consumer<SpriteData.Sprite> onNewSprite) {
        if (currentSprite == null) {
            return;
        }
        SpriteEditor editor = new SpriteEditor(currentSprite, spriteImageTranslator, this, onNewSprite);
        editor.initOwner(stage);
        editor.showAndWait();
    }

    public List<Color> getCharacterColors() {
        Set<Color> colors = new LinkedHashSet<>();
        if (appState.getCardData() != null && appState.getCardData().getCharacters() != null) {
            for (com.github.cfogrady.dim.modifier.data.card.Character<?, ?> character : appState.getCardData().getCharacters()) {
                if (character.getSprites() != null) {
                    for (SpriteData.Sprite sprite : character.getSprites()) {
                        if (sprite != null) {
                            collectColorsFromSprite(sprite, colors);
                        }
                    }
                }
            }
        }
        List<Color> sortedColors = new ArrayList<>(colors);
        sortedColors.sort((c1, c2) -> {
            boolean g1 = c1.getSaturation() < 0.08;
            boolean g2 = c2.getSaturation() < 0.08;
            if (g1 && g2) {
                return Double.compare(c1.getBrightness(), c2.getBrightness());
            }
            if (g1) return -1;
            if (g2) return 1;
            int hueCompare = Double.compare(c1.getHue(), c2.getHue());
            if (hueCompare != 0) return hueCompare;
            int satCompare = Double.compare(c1.getSaturation(), c2.getSaturation());
            if (satCompare != 0) return satCompare;
            return Double.compare(c1.getBrightness(), c2.getBrightness());
        });
        return sortedColors;
    }

    public List<Color> getSystemColors() {
        Set<Color> colors = new LinkedHashSet<>();
        if (appState.getCardData() != null && appState.getCardData().getCardSprites() != null) {
            CardSprites cs = appState.getCardData().getCardSprites();
            if (cs.getLogo() != null) collectColorsFromSprite(cs.getLogo(), colors);
            if (cs.getBackgrounds() != null) {
                for (SpriteData.Sprite sprite : cs.getBackgrounds()) {
                    if (sprite != null) collectColorsFromSprite(sprite, colors);
                }
            }
            if (cs.getEgg() != null) {
                for (SpriteData.Sprite sprite : cs.getEgg()) {
                    if (sprite != null) collectColorsFromSprite(sprite, colors);
                }
            }
            if (cs.getReady() != null) collectColorsFromSprite(cs.getReady(), colors);
            if (cs.getGo() != null) collectColorsFromSprite(cs.getGo(), colors);
            if (cs.getWin() != null) collectColorsFromSprite(cs.getWin(), colors);
            if (cs.getLose() != null) collectColorsFromSprite(cs.getLose(), colors);
            if (cs.getHits() != null) {
                for (SpriteData.Sprite sprite : cs.getHits()) {
                    if (sprite != null) collectColorsFromSprite(sprite, colors);
                }
            }
            if (cs.getTypes() != null) {
                for (SpriteData.Sprite sprite : cs.getTypes()) {
                    if (sprite != null) collectColorsFromSprite(sprite, colors);
                }
            }
            if (cs.getStages() != null) {
                for (SpriteData.Sprite sprite : cs.getStages()) {
                    if (sprite != null) collectColorsFromSprite(sprite, colors);
                }
            }
            if (cs.getSmallAttacks() != null) {
                for (SpriteData.Sprite sprite : cs.getSmallAttacks()) {
                    if (sprite != null) collectColorsFromSprite(sprite, colors);
                }
            }
            if (cs.getBigAttacks() != null) {
                for (SpriteData.Sprite sprite : cs.getBigAttacks()) {
                    if (sprite != null) collectColorsFromSprite(sprite, colors);
                }
            }
        }
        List<Color> sortedColors = new ArrayList<>(colors);
        sortedColors.sort((c1, c2) -> {
            boolean g1 = c1.getSaturation() < 0.08;
            boolean g2 = c2.getSaturation() < 0.08;
            if (g1 && g2) {
                return Double.compare(c1.getBrightness(), c2.getBrightness());
            }
            if (g1) return -1;
            if (g2) return 1;
            int hueCompare = Double.compare(c1.getHue(), c2.getHue());
            if (hueCompare != 0) return hueCompare;
            int satCompare = Double.compare(c1.getSaturation(), c2.getSaturation());
            if (satCompare != 0) return satCompare;
            return Double.compare(c1.getBrightness(), c2.getBrightness());
        });
        return sortedColors;
    }

    private void collectColorsFromSprite(SpriteData.Sprite sprite, Set<Color> colors) {
        javafx.scene.image.Image image = spriteImageTranslator.loadImageFromSprite(sprite);
        javafx.scene.image.PixelReader reader = image.getPixelReader();
        if (reader != null) {
            for (int y = 0; y < sprite.getHeight(); y++) {
                for (int x = 0; x < sprite.getWidth(); x++) {
                    Color color = reader.getColor(x, y);
                    if (color.getOpacity() > 0.0) {
                        int r = (int) Math.round(color.getRed() * 255.0);
                        int g = (int) Math.round(color.getGreen() * 255.0);
                        int b = (int) Math.round(color.getBlue() * 255.0);
                        colors.add(Color.rgb(r, g, b));
                    }
                }
            }
        }
    }
}
