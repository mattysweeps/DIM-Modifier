package com.github.cfogrady.dim.modifier;

import com.github.cfogrady.dim.modifier.data.AppState;
import com.github.cfogrady.dim.modifier.data.card.CardData;
import com.github.cfogrady.dim.modifier.controllers.LoadedViewController;
import com.github.cfogrady.dim.modifier.data.card.CardDataIO;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.Optional;

@RequiredArgsConstructor
@Slf4j
public class FirstLoadScene {
    private final AppState appState;
    private final Stage stage;
    private final CardDataIO cardDataIO;
    private final LoadedViewController loadedViewController;

    public void setupScene() {
        Button button = new Button();
        button.setText("Open DIM File");

        CheckBox bypassChecksumCheckBox = new CheckBox("Bypass Checksum Validation");

        button.setOnAction(event -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select DIM File");
            File file = fileChooser.showOpenDialog(stage);
            if(file != null) {
                if (loadCard(file, !bypassChecksumCheckBox.isSelected())) {
                    setupLoadedDataView();
                }
            }
        });

        Button importDirButton = new Button("Import from Dir");
        importDirButton.setOnAction(event -> {
            new com.github.cfogrady.dim.modifier.data.io.DimDirIOController(appState, stage).importFromDir(() -> {
                if (appState.getCardData() != null) {
                    setupLoadedDataView();
                }
            });
        });

        VBox root = new VBox(15, button, importDirButton, bypassChecksumCheckBox);
        root.setAlignment(Pos.CENTER);
        Scene scene = new Scene(root, 640, 480);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());

        stage.setScene(scene);
        stage.show();
    }

    private boolean loadCard(File file, boolean verifyChecksum) {
        try(FileInputStream fileInputStream = new FileInputStream(file)) {
            CardData<?, ?, ?> cardData = cardDataIO.readFromStream(fileInputStream, verifyChecksum);
            appState.setCardData(cardData);
            appState.setLastOpenedFilePath(file);
            return true;
        } catch (IllegalStateException e) {
            log.warn("Checksum mismatch or invalid DIM: {}", e.getMessage());
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Checksum Error");
            alert.setHeaderText("DIM Checksum Mismatch");
            alert.setContentText(e.getMessage() + "\n\nDo you want to bypass checksum validation and load this file anyway?");
            ButtonType loadAnyway = new ButtonType("Bypass & Load Anyway");
            ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
            alert.getButtonTypes().setAll(loadAnyway, cancel);
            Optional<ButtonType> result = alert.showAndWait();
            if(result.isPresent() && result.get() == loadAnyway) {
                try(FileInputStream retryStream = new FileInputStream(file)) {
                    CardData<?, ?, ?> cardData = cardDataIO.readFromStream(retryStream, false);
                    appState.setCardData(cardData);
                    return true;
                } catch (Exception retryEx) {
                    log.error("Failed to load file even with checksum bypassed: {}", file.getAbsolutePath(), retryEx);
                    return false;
                }
            }
            return false;
        } catch (IOException e) {
            log.error("Error loading file: {}", file.getAbsolutePath(), e);
            return false;
        }
    }

    private void setupLoadedDataView() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/LoadedView.fxml"));
            loader.setControllerFactory(p -> loadedViewController);
            Scene scene = new Scene(loader.load(), 1520, 720);
            scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
            loadedViewController.refreshAll();
            stage.setScene(scene);
            stage.centerOnScreen();
            stage.show();
        } catch (IOException e) {
            log.error("Unable to load layout for loaded data view!", e);
        }
    }
}
