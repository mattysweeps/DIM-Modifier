package com.github.cfogrady.dim.modifier.controllers;

import com.github.cfogrady.dim.modifier.data.AppState;
import com.github.cfogrady.dim.modifier.data.bem.BemCardData;
import com.github.cfogrady.dim.modifier.data.card.MetaData;
import com.github.cfogrady.dim.modifier.data.dim.DimCardData;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.AnchorPane;
import javafx.scene.text.Text;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.net.URL;
import java.util.ResourceBundle;

@Slf4j
@RequiredArgsConstructor
public class LoadedViewController implements Initializable {
    private final AppState appState;
    private final Node charactersSubView;
    private final CharacterViewController characterViewController;
    private final BattlesViewController battlesViewController;
    private final Node battlesSubView;
    private final EvolutionTreeViewController evolutionTreeViewController;
    private final Node evolutionTreeSubView;
    private final EggViewController eggViewController;
    private final Node eggSubView;
    private final BemSystemViewController bemSystemViewController;
    private final Node bemSystemSubView;
    private final DimSystemViewController dimSystemViewController;
    private final Node dimSystemSubView;
    private final DimIOController dimIOController;

    @FXML
    private Button openButton;
    @FXML
    private Button saveButton;
    @FXML
    private Button saveAsButton;
    @FXML
    private Text dimIdText;
    @FXML
    private Text revisionIdText;
    @FXML
    private Text factoryDateText;
    @FXML
    private Text checksumText;
    @FXML
    private Text bytesUsedText;
    @FXML
    private Button charactersButton;
    @FXML
    private Button evolutionTreeButton;
    @FXML
    private Button battlesButton;
    @FXML
    private Button eggButton;
    @FXML
    private Button systemButton;
    @FXML
    private AnchorPane subView;

    private SubViewSelection subViewSelection = SubViewSelection.CHARACTERS;

    private enum SubViewSelection {
        CHARACTERS,
        EVOLUTION_TREE,
        BATTLES,
        EGG,
        SYSTEM;
    }


    @Override
    public void initialize(URL location, ResourceBundle resources) {
        charactersButton.setOnAction(e -> {
            subViewSelection = SubViewSelection.CHARACTERS;
            refreshButtons();
            refreshSubview();
        });
        evolutionTreeButton.setOnAction(e -> {
            subViewSelection = SubViewSelection.EVOLUTION_TREE;
            refreshButtons();
            refreshSubview();
        });
        battlesButton.setOnAction(e -> {
            subViewSelection = SubViewSelection.BATTLES;
            refreshButtons();
            refreshSubview();
        });
        eggButton.setOnAction(e -> {
            subViewSelection = SubViewSelection.EGG;
            refreshButtons();
            refreshSubview();
        });
        systemButton.setOnAction(e -> {
            subViewSelection = SubViewSelection.SYSTEM;
            refreshButtons();
            refreshSubview();
        });
        openButton.setOnAction(this::openButton);
        saveButton.setOnAction(e -> dimIOController.saveDim());
        saveAsButton.setOnAction(e -> dimIOController.saveAsDim());
    }

    private void openButton(ActionEvent event) {
        dimIOController.openDim(this::clearState);
    }

    private void clearState() {
        appState.setSelectedBackgroundIndex(0);
        characterViewController.clearState();
        evolutionTreeViewController.clearState();
        eggViewController.clearState();
        bemSystemViewController.clearState();
        dimSystemViewController.clearState();
        refreshAll();
    }

    public void refreshAll() {
        MetaData metaData = appState.getCardData().getMetaData();
        dimIdText.setText("DIM ID: " + metaData.getId());
        revisionIdText.setText("Revision: " + metaData.getRevision());
        factoryDateText.setText("Factory Date: " + metaData.getYear() + "/" + metaData.getMonth() + "/" + metaData.getDay());
        checksumText.setText("Checksum At Load: " + Integer.toHexString(metaData.getOriginalChecksum()));
        updateBytesUsedText();
        refreshButtons();
        refreshSubview();
    }

    public void updateBytesUsedText() {
        if (bytesUsedText == null || appState.getCardData() == null) return;
        try {
            int bytesUsed = dimIOController.calculateCardSize();
            double pages = bytesUsed / 4096.0;
            int pageCount = (int) Math.ceil(pages);
            bytesUsedText.setText(String.format("Bytes Used: %,d (%d / 4096 B blocks | %.2f)", bytesUsed, pageCount, pages));
        } catch (Exception e) {
            log.error("Failed to calculate bytes used", e);
            bytesUsedText.setText("Bytes Used: N/A");
        }
    }

    public void refreshButtons() {
        charactersButton.setDisable(subViewSelection == SubViewSelection.CHARACTERS);
        evolutionTreeButton.setDisable(subViewSelection == SubViewSelection.EVOLUTION_TREE);
        battlesButton.setDisable(subViewSelection == SubViewSelection.BATTLES);
        eggButton.setDisable(subViewSelection == SubViewSelection.EGG);
        systemButton.setDisable(subViewSelection == SubViewSelection.SYSTEM);
    }

    public void refreshSubview() {
        Node subViewNode = getSubview();
        subView.getChildren().clear();
        subView.getChildren().add(subViewNode);
    }

    private Node getSubview() {
        switch (subViewSelection) {
            case CHARACTERS -> {
                characterViewController.refreshAll();
                return charactersSubView;
            }
            case EVOLUTION_TREE -> {
                evolutionTreeViewController.refreshAll();
                return evolutionTreeSubView;
            }
            case BATTLES -> {
                battlesViewController.refreshAll();
                return battlesSubView;
            }
            case EGG -> {
                eggViewController.refreshAll();
                return eggSubView;
            }
            case SYSTEM -> {
                if(appState.getCardData() instanceof BemCardData) {
                    bemSystemViewController.refreshAll();
                    return bemSystemSubView;
                } else if(appState.getCardData() instanceof DimCardData) {
                    dimSystemViewController.refreshAll();
                    return dimSystemSubView;
                } else {
                    throw new IllegalArgumentException("Cannot load system view for unknown card type " + appState.getCardData().getClass().getName());
                }
            }
            default -> {
                return null;
            }
        }
    }
}
