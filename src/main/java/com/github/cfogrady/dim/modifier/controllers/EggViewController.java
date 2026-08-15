package com.github.cfogrady.dim.modifier.controllers;

import com.github.cfogrady.dim.modifier.SpriteImageTranslator;
import com.github.cfogrady.dim.modifier.SpriteReplacer;
import com.github.cfogrady.dim.modifier.data.AppState;
import com.github.cfogrady.dim.modifier.data.card.CardSprites;
import com.github.cfogrady.vb.dim.sprite.SpriteData;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.StackPane;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

@RequiredArgsConstructor
@Slf4j
public class EggViewController implements Initializable {
    private static final SpriteData.SpriteDimensions EXPECTED_DIMENSIONS = SpriteData.SpriteDimensions.builder().width(32).height(40).build();

    private final AppState appState;
    private final SpriteImageTranslator spriteImageTranslator;
    private final SpriteReplacer spriteReplacer;

    @FXML
    private ImageView eggSpritesView;
    @FXML
    private StackPane eggSpriteContainer;
    @FXML
    private Button prevEggButton;
    @FXML
    private Button nextEggButton;
    @FXML
    private Label eggIndexLabel;

    private int eggSelection = 0;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initializeEggSprites();
    }

    public void clearState() {
        eggSelection = 0;
    }

    public void refreshAll() {
        refreshEggSprite();
    }

    private void initializeEggSprites() {
        nextEggButton.setOnAction(e -> {
            List<SpriteData.Sprite> eggs = getEggList();
            if(!eggs.isEmpty()) {
                eggSelection = (eggSelection + 1) % eggs.size();
                refreshEggSprite();
            }
        });
        prevEggButton.setOnAction(e -> {
            List<SpriteData.Sprite> eggs = getEggList();
            if(!eggs.isEmpty()) {
                eggSelection = eggSelection - 1;
                if(eggSelection < 0) {
                    eggSelection = eggs.size() - 1;
                }
                refreshEggSprite();
            }
        });
        eggSpriteContainer.setOnMouseClicked(e -> {
            SpriteData.Sprite newSprite = spriteReplacer.loadSpriteFromFileChooser();
            if(newSprite != null) {
                replaceSprite(newSprite);
            }
        });
        eggSpriteContainer.setOnDragDropped(e -> {
            if(e.getDragboard().hasFiles()) {
                List<File> files = e.getDragboard().getFiles();
                File file = files.get(0);
                SpriteData.Sprite newSprite = spriteReplacer.loadSpriteFromFile(file);
                if(newSprite != null) {
                    replaceSprite(newSprite);
                }
            }
        });
        eggSpriteContainer.setOnDragOver(e -> {
            if (e.getDragboard().hasImage() || e.getDragboard().hasFiles()) {
                e.acceptTransferModes(TransferMode.ANY);
                e.consume();
            }
        });
    }

    private void replaceSprite(SpriteData.Sprite newSprite) {
        SpriteData.SpriteDimensions proposedDimensions = newSprite.getSpriteDimensions();
        if(proposedDimensions.equals(EXPECTED_DIMENSIONS)) {
            setEggSprite(newSprite);
            refreshEggSprite();
        } else {
            Alert alert = new Alert(Alert.AlertType.NONE, CardSprites.getDimensionsText(proposedDimensions, List.of(EXPECTED_DIMENSIONS)));
            alert.getButtonTypes().add(ButtonType.OK);
            alert.show();
        }
    }

    private List<SpriteData.Sprite> getEggList() {
        return appState.getCardData().getCardSprites().getEgg();
    }

    private SpriteData.Sprite getEggSprite() {
        List<SpriteData.Sprite> eggs = getEggList();
        if(eggSelection >= eggs.size()) {
            eggSelection = 0;
        }
        return eggs.get(eggSelection);
    }

    private void setEggSprite(SpriteData.Sprite newSprite) {
        getEggList().set(eggSelection, newSprite);
    }

    private void refreshEggSprite() {
        List<SpriteData.Sprite> eggs = getEggList();
        if(eggs == null || eggs.isEmpty()) return;
        if(eggSelection >= eggs.size()) {
            eggSelection = 0;
        }
        eggIndexLabel.setText(String.format("Egg %d of %d", eggSelection + 1, eggs.size()));
        prevEggButton.setDisable(eggs.size() <= 1);
        nextEggButton.setDisable(eggs.size() <= 1);
        eggSpritesView.setImage(spriteImageTranslator.loadImageFromSprite(getEggSprite()));
    }
}
