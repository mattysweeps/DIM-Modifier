package com.github.cfogrady.dim.modifier.controllers;

import com.github.cfogrady.dim.modifier.SpriteImageTranslator;
import com.github.cfogrady.dim.modifier.data.AppState;
import com.github.cfogrady.dim.modifier.data.card.Character;
import com.github.cfogrady.dim.modifier.data.card.SpecificFusion;
import com.github.cfogrady.dim.modifier.data.card.TransformationEntry;
import com.github.cfogrady.vb.dim.sprite.SpriteData;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.CubicCurve;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.net.URL;
import java.util.*;

@RequiredArgsConstructor
@Slf4j
public class EvolutionTreeViewController implements Initializable {
    private static final double STAGE_X_SPACING = 240.0;
    private static final double NODE_Y_SPACING = 130.0;
    private static final double START_X = 50.0;
    private static final double START_Y = 50.0;
    private static final double NODE_WIDTH = 160.0;
    private static final double NODE_HEIGHT = 100.0;

    private static final Color NORMAL_LINE_COLOR = Color.web("#0066cc");
    private static final Color ATTRIBUTE_FUSION_LINE_COLOR = Color.web("#8800cc");
    private static final Color SPECIFIC_FUSION_LINE_COLOR = Color.web("#e67300");

    private final AppState appState;
    private final SpriteImageTranslator spriteImageTranslator;

    @FXML
    private AnchorPane treeAnchorPane;
    @FXML
    private Button refreshButton;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (refreshButton != null) {
            refreshButton.setOnAction(e -> refreshAll());
        }
    }

    public void clearState() {
        if (treeAnchorPane != null) {
            treeAnchorPane.getChildren().clear();
        }
    }

    public void refreshAll() {
        if (treeAnchorPane == null || appState.getCardData() == null) return;
        treeAnchorPane.getChildren().clear();

        List<Character<?, ?>> characters = (List<Character<?, ?>>) (List<?>) appState.getCardData().getCharacters();
        if (characters == null || characters.isEmpty()) return;

        Map<UUID, Character<?, ?>> characterMap = new HashMap<>();
        Map<UUID, Integer> indexMap = new HashMap<>();
        Map<Integer, List<Character<?, ?>>> stageMap = new TreeMap<>();

        for (int i = 0; i < characters.size(); i++) {
            Character<?, ?> c = characters.get(i);
            characterMap.put(c.getId(), c);
            indexMap.put(c.getId(), i);
            stageMap.computeIfAbsent(c.getStage(), k -> new ArrayList<>()).add(c);
        }

        Map<UUID, NodePosition> posMap = new HashMap<>();

        double maxX = 800.0;
        double maxY = 600.0;

        int stageColIdx = 0;
        for (Map.Entry<Integer, List<Character<?, ?>>> entry : stageMap.entrySet()) {
            int stageNum = entry.getKey();
            List<Character<?, ?>> charList = entry.getValue();

            // Add Stage header label
            double colX = START_X + stageColIdx * STAGE_X_SPACING;
            Label stageHeader = new Label("Stage " + (stageNum + 1));
            stageHeader.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
            AnchorPane.setLeftAnchor(stageHeader, colX);
            AnchorPane.setTopAnchor(stageHeader, 15.0);
            treeAnchorPane.getChildren().add(stageHeader);

            for (int r = 0; r < charList.size(); r++) {
                Character<?, ?> c = charList.get(r);
                int charIdx = indexMap.get(c.getId());
                double nodeX = colX;
                double nodeY = START_Y + r * NODE_Y_SPACING;

                posMap.put(c.getId(), new NodePosition(nodeX, nodeY, charIdx));

                maxX = Math.max(maxX, nodeX + NODE_WIDTH + 100);
                maxY = Math.max(maxY, nodeY + NODE_HEIGHT + 100);
            }
            stageColIdx++;
        }

        // Draw connection lines FIRST so they stay behind node cards
        for (Character<?, ?> c : characters) {
            NodePosition srcPos = posMap.get(c.getId());
            if (srcPos == null) continue;

            double srcRightX = srcPos.x + NODE_WIDTH;
            double srcRightY = srcPos.y + NODE_HEIGHT / 2.0;

            // 1. Regular Transformations
            if (c.getTransformationEntries() != null) {
                for (Object rawEntry : c.getTransformationEntries()) {
                    TransformationEntry entry = (TransformationEntry) rawEntry;
                    if (entry.getToCharacter() != null && posMap.containsKey(entry.getToCharacter())) {
                        NodePosition dstPos = posMap.get(entry.getToCharacter());
                        double dstLeftX = dstPos.x;
                        double dstLeftY = dstPos.y + NODE_HEIGHT / 2.0;

                        String tooltipText = String.format("Transform Requirements:\nVitals: %d\nWin%%: %d%%\nBattles: %d\nTrophies: %d",
                                entry.getVitalRequirements(), entry.getWinRatioRequirement(), entry.getBattleRequirement(), entry.getTrophyRequirement());
                        drawConnectionLine(srcRightX, srcRightY, dstLeftX, dstLeftY, NORMAL_LINE_COLOR, false, tooltipText);
                    }
                }
            }

            // 2. Attribute Fusions
            if (c.getFusions() != null) {
                UUID[] fusionTargets = new UUID[] {
                        c.getFusions().getType1FusionResult(),
                        c.getFusions().getType2FusionResult(),
                        c.getFusions().getType3FusionResult(),
                        c.getFusions().getType4FusionResult()
                };
                for (int typeIdx = 0; typeIdx < fusionTargets.length; typeIdx++) {
                    UUID targetId = fusionTargets[typeIdx];
                    if (targetId != null && posMap.containsKey(targetId)) {
                        NodePosition dstPos = posMap.get(targetId);
                        double dstLeftX = dstPos.x;
                        double dstLeftY = dstPos.y + NODE_HEIGHT / 2.0;
                        drawConnectionLine(srcRightX, srcRightY, dstLeftX, dstLeftY, ATTRIBUTE_FUSION_LINE_COLOR, true, "Attribute Fusion Type " + (typeIdx + 1));
                    }
                }
            }

            // 3. Specific Fusions
            if (c.getSpecificFusions() != null) {
                for (SpecificFusion fusion : c.getSpecificFusions()) {
                    if (fusion.getEvolveToCharacterId() != null && posMap.containsKey(fusion.getEvolveToCharacterId())) {
                        NodePosition dstPos = posMap.get(fusion.getEvolveToCharacterId());
                        double dstLeftX = dstPos.x;
                        double dstLeftY = dstPos.y + NODE_HEIGHT / 2.0;
                        drawConnectionLine(srcRightX, srcRightY, dstLeftX, dstLeftY, SPECIFIC_FUSION_LINE_COLOR, true, "Specific Fusion");
                    }
                }
            }
        }

        // Draw Node Cards AFTER lines
        for (Character<?, ?> c : characters) {
            NodePosition pos = posMap.get(c.getId());
            if (pos == null) continue;

            VBox nodeCard = createCharacterNodeCard(c, pos.index);
            AnchorPane.setLeftAnchor(nodeCard, pos.x);
            AnchorPane.setTopAnchor(nodeCard, pos.y);
            treeAnchorPane.getChildren().add(nodeCard);
        }

        treeAnchorPane.setPrefWidth(maxX);
        treeAnchorPane.setPrefHeight(maxY);
    }

    private VBox createCharacterNodeCard(Character<?, ?> c, int charIdx) {
        VBox card = new VBox(4);
        card.setPrefWidth(NODE_WIDTH);
        card.setPrefHeight(NODE_HEIGHT);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(6));
        card.setStyle("-fx-background-color: -fx-background; -fx-border-color: -fx-box-border; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.15), 5, 0, 0, 1);");

        Label titleLabel = new Label(String.format("#%d (Stage %d)", charIdx, c.getStage() + 1));
        titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 11px;");

        HBox spriteBox = new HBox(6);
        spriteBox.setAlignment(Pos.CENTER);

        // Character Idle Sprite
        if (c.getSprites() != null && c.getSprites().size() > 1) {
            SpriteData.Sprite idleSprite = c.getSprites().get(1);
            Image img = spriteImageTranslator.loadImageFromSprite(idleSprite);
            ImageView imgView = new ImageView(img);
            imgView.setFitWidth(36);
            imgView.setFitHeight(36);
            imgView.setPreserveRatio(true);
            spriteBox.getChildren().add(imgView);
        }

        // Character Name Box Sprite (Index 0 if present)
        if (c.getSprites() != null && !c.getSprites().isEmpty()) {
            SpriteData.Sprite nameSprite = c.getSprites().get(0);
            if (nameSprite != null) {
                Image nameImg = spriteImageTranslator.loadImageFromSprite(nameSprite);
                ImageView nameView = new ImageView(nameImg);
                nameView.setFitWidth(80);
                nameView.setFitHeight(15);
                nameView.setPreserveRatio(true);
                StackPane nameHolder = new StackPane(nameView);
                nameHolder.setStyle("-fx-background-color: black; -fx-padding: 2;");
                spriteBox.getChildren().add(nameHolder);
            }
        }

        card.getChildren().addAll(titleLabel, spriteBox);

        // Hover animation effect matching app theme
        card.setOnMouseEntered(e -> card.setStyle("-fx-background-color: -fx-control-inner-background; -fx-border-color: -fx-accent; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-effect: dropshadow(three-pass-box, rgba(0,102,204,0.3), 8, 0, 0, 1);"));
        card.setOnMouseExited(e -> card.setStyle("-fx-background-color: -fx-background; -fx-border-color: -fx-box-border; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.15), 5, 0, 0, 1);"));

        return card;
    }

    private void drawConnectionLine(double startX, double startY, double endX, double endY, Color color, boolean isDashed, String tooltipText) {
        double controlX1 = startX + (endX - startX) / 2.0;
        double controlY1 = startY;
        double controlX2 = startX + (endX - startX) / 2.0;
        double controlY2 = endY;

        CubicCurve curve = new CubicCurve(startX, startY, controlX1, controlY1, controlX2, controlY2, endX, endY);
        curve.setStroke(color);
        curve.setStrokeWidth(2.5);
        curve.setFill(null);

        if (isDashed) {
            curve.getStrokeDashArray().addAll(6.0, 4.0);
        }

        if (tooltipText != null && !tooltipText.isEmpty()) {
            Tooltip.install(curve, new Tooltip(tooltipText));
        }

        treeAnchorPane.getChildren().add(curve);
    }

    private record NodePosition(double x, double y, int index) {}
}
