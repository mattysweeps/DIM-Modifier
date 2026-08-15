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
import javafx.scene.image.ImageView;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.StackPane;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.Consumer;

@RequiredArgsConstructor
@Slf4j
public class DimSystemViewController implements Initializable {
    private final AppState appState;
    private final SpriteImageTranslator spriteImageTranslator;
    private final SpriteReplacer spriteReplacer;

    @FXML
    ImageView backgroundsView;
    @FXML
    StackPane backgroundSpriteContainer;
    @FXML
    ImageView iconSpriteView;
    @FXML
    StackPane iconSpriteContainer;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initializeBackground();
        initializeLogoSprites();
    }

    public void clearState() {
    }

    public void refreshAll() {
        refreshBackground();
        refreshLogoSprite();
    }

    private void initializeBackground() {
        backgroundSpriteContainer.setOnMouseClicked(e -> {
            spriteReplacer.handleSelectAndEdit(e,
                appState::getSelectedBackground,
                spriteReplacer::loadSpriteFromFileChooser,
                newSprite -> replaceSprite(newSprite, SpriteData.SpriteDimensions.builder().width(80).height(160).build(), appState::setBackgroundSprite, this::refreshBackground)
            );
        });
        backgroundSpriteContainer.setOnDragDropped( e-> {
            if(e.getDragboard().hasFiles()) {
                List<File> files = e.getDragboard().getFiles();
                File file = files.get(0);
                SpriteData.Sprite newSprite = spriteReplacer.loadSpriteFromFile(file);
                replaceSprite(newSprite, SpriteData.SpriteDimensions.builder().width(80).height(160).build(), appState::setBackgroundSprite, this::refreshBackground);
            }
        });
        backgroundSpriteContainer.setOnDragOver(e -> {
            if (e.getDragboard().hasImage()) {
                e.acceptTransferModes(TransferMode.ANY);
                log.info("Drag Over Image");
                e.consume();
            } else if(e.getDragboard().hasFiles()) {
                if (e.getDragboard().getFiles().size() > 1) {
                    log.info("Can only load 1 file at a time");
                } else {
                    e.acceptTransferModes(TransferMode.ANY);
                    e.consume();
                }
            }
        });
    }

    private void refreshBackground() {
        backgroundsView.setImage(spriteImageTranslator.loadImageFromSprite(appState.getSelectedBackground()));
    }

    private void replaceSprite(SpriteData.Sprite newSprite, SpriteData.SpriteDimensions expectedDimensions, Consumer<SpriteData.Sprite> setter, Runnable refresher) {
        SpriteData.SpriteDimensions proposedDimensions = newSprite.getSpriteDimensions();
        if(proposedDimensions.equals(expectedDimensions)) {
            setter.accept(newSprite);
            refresher.run();
        } else {
            Alert alert = new Alert(Alert.AlertType.NONE, CardSprites.getDimensionsText(proposedDimensions, List.of(expectedDimensions)));
            alert.getButtonTypes().add(ButtonType.OK);
            alert.show();
        }
    }

    private void initializeLogoSprites() {
        iconSpriteContainer.setOnMouseClicked(e -> {
            spriteReplacer.handleSelectAndEdit(e,
                appState.getCardData().getCardSprites()::getLogo,
                spriteReplacer::loadSpriteFromFileChooser,
                newSprite -> replaceSprite(newSprite, SpriteData.SpriteDimensions.builder().width(42).height(42).build(), appState.getCardData().getCardSprites()::setLogo, this::refreshLogoSprite)
            );
        });
        iconSpriteContainer.setOnDragDropped( e-> {
            if(e.getDragboard().hasFiles()) {
                List<File> files = e.getDragboard().getFiles();
                File file = files.get(0);
                SpriteData.Sprite newSprite = spriteReplacer.loadSpriteFromFile(file);
                replaceSprite(newSprite, SpriteData.SpriteDimensions.builder().width(42).height(42).build(), appState.getCardData().getCardSprites()::setLogo, this::refreshLogoSprite);
            }
        });
        iconSpriteContainer.setOnDragOver(e -> {
            if (e.getDragboard().hasImage()) {
                e.acceptTransferModes(TransferMode.ANY);
                log.info("Drag Over Image");
                e.consume();
            } else if(e.getDragboard().hasFiles()) {
                if (e.getDragboard().getFiles().size() > 1) {
                    log.info("Can only load 1 file at a time");
                } else {
                    e.acceptTransferModes(TransferMode.ANY);
                    e.consume();
                }
            }
        });
    }

    private void refreshLogoSprite() {
        iconSpriteView.setImage(spriteImageTranslator.loadImageFromSprite(appState.getCardData().getCardSprites().getLogo()));
    }
}
