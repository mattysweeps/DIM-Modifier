package com.github.cfogrady.dim.modifier.controllers;

import com.github.cfogrady.dim.modifier.data.AppState;
import com.github.cfogrady.dim.modifier.data.card.CardData;
import com.github.cfogrady.dim.modifier.data.card.CardDataIO;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.List;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class DimIOController {
    public static final String BULLET = "\u2022";
    public static final String ERROR_SEPARATOR = System.lineSeparator() + BULLET + " ";

    private final Stage stage;
    private final CardDataIO cardDataIO;
    private final AppState appState;

    public void openDim(Runnable onCompletion) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select DIM / BIN File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("BIN Files (*.bin)", "*.bin"),
                new FileChooser.ExtensionFilter("DIM Files (*.dim)", "*.dim"),
                new FileChooser.ExtensionFilter("All Files (*.*)", "*.*")
        );
        File lastDir = appState.getLastOpenedDirectory();
        if (lastDir != null && lastDir.exists()) {
            fileChooser.setInitialDirectory(lastDir);
        }
        File file = fileChooser.showOpenDialog(stage);
        if(file != null) {
            if (loadDimWithChecksumHandling(file)) {
                appState.setLastOpenedFilePath(file);
                onCompletion.run();
            }
        }
    }

    private boolean loadDimWithChecksumHandling(File file) {
        try(InputStream fileInputStream = new FileInputStream(file)) {
            CardData<?, ?, ?> cardData = cardDataIO.readFromStream(fileInputStream, true);
            appState.setCardData(cardData);
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
                try(InputStream retryStream = new FileInputStream(file)) {
                    CardData<?, ?, ?> cardData = cardDataIO.readFromStream(retryStream, false);
                    appState.setCardData(cardData);
                    return true;
                } catch (Exception retryEx) {
                    log.error("Failed to load file even with checksum bypassed: {}", file.getAbsolutePath(), retryEx);
                    return false;
                }
            }
            return false;
        } catch (FileNotFoundException e) {
            log.error("Couldn't find selected file.", e);
            return false;
        } catch (IOException e) {
            log.error("Error opening/closing file.", e);
            return false;
        }
    }

    public void saveDim() {
        if (appState.getLastOpenedFilePath() != null) {
            List<String> errors = appState.getCardData().checkForErrors();
            if(!errors.isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.NONE, "Cannot save. Errors in card data:" + ERROR_SEPARATOR + String.join(ERROR_SEPARATOR, errors));
                alert.getButtonTypes().add(ButtonType.OK);
                alert.show();
                return;
            }
            saveDimToFile(appState.getLastOpenedFilePath());
        } else {
            saveAsDim();
        }
    }

    public void saveAsDim() {
        List<String> errors = appState.getCardData().checkForErrors();
        if(!errors.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.NONE, "Cannot save. Errors in card data:" + ERROR_SEPARATOR + String.join(ERROR_SEPARATOR, errors));
            alert.getButtonTypes().add(ButtonType.OK);
            alert.show();
            return;
        }
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save DIM / BIN File As...");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("BIN Files (*.bin)", "*.bin"),
                new FileChooser.ExtensionFilter("DIM Files (*.dim)", "*.dim"),
                new FileChooser.ExtensionFilter("All Files (*.*)", "*.*")
        );
        File lastDir = appState.getLastOpenedDirectory();
        if (lastDir != null && lastDir.exists()) {
            fileChooser.setInitialDirectory(lastDir);
        }
        if (appState.getLastOpenedFilePath() != null) {
            fileChooser.setInitialFileName(appState.getLastOpenedFilePath().getName());
        } else {
            fileChooser.setInitialFileName("card.bin");
        }
        File file = fileChooser.showSaveDialog(stage);
        if(file != null) {
            appState.setLastOpenedFilePath(file);
            saveDimToFile(file);
        }
    }

    private void saveDimToFile(File file) {
        cardDataIO.writeToFile(appState.getCardData(), file);
    }

    public int calculateCardSize() {
        if (appState.getCardData() == null) return 0;
        return cardDataIO.calculateSize(appState.getCardData());
    }
}
