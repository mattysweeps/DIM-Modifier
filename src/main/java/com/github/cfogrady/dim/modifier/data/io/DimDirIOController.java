package com.github.cfogrady.dim.modifier.data.io;

import com.github.cfogrady.dim.modifier.data.AppState;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.io.File;

@Slf4j
@RequiredArgsConstructor
public class DimDirIOController {
    private final AppState appState;
    private final Stage stage;

    public void exportToDir() {
        javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
        chooser.setTitle("Select Directory to Export DIM");
        File lastDir = appState.getLastOpenedDirectory();
        if (lastDir != null && lastDir.exists()) {
            chooser.setInitialDirectory(lastDir);
        }
        File selectedDir = chooser.showDialog(stage);
        if (selectedDir != null) {
            if (selectedDir.exists() && selectedDir.list() != null && selectedDir.list().length > 0) {
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
                alert.setTitle("Directory Not Empty");
                alert.setHeaderText("The selected directory is not empty.");
                alert.setContentText("Files may be overwritten. Do you want to proceed?");
                java.util.Optional<javafx.scene.control.ButtonType> result = alert.showAndWait();
                if (result.isEmpty() || result.get() != javafx.scene.control.ButtonType.OK) {
                    return;
                }
            }
            
            appState.setLastOpenedFilePath(selectedDir);
            
            DimDirExporter exporter = new DimDirExporter(appState, new com.github.cfogrady.dim.modifier.SpriteImageTranslator(appState, stage));
            
            javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<Void>() {
                @Override
                protected Void call() throws Exception {
                    exporter.exportToDir(selectedDir, () -> updateProgress(1, 1));
                    return null;
                }
            };
            
            task.setOnSucceeded(e -> {
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
                alert.setTitle("Export Complete");
                alert.setHeaderText(null);
                alert.setContentText("Export to directory completed successfully.");
                alert.show();
            });
            
            task.setOnFailed(e -> {
                log.error("Failed to export to dir", task.getException());
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
                alert.setTitle("Export Failed");
                alert.setHeaderText("An error occurred during export.");
                alert.setContentText(task.getException().getMessage());
                alert.show();
            });

            javafx.scene.control.Alert progressAlert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
            progressAlert.setTitle("Exporting");
            progressAlert.setHeaderText("Exporting to directory...");
            javafx.scene.control.ProgressBar progressBar = new javafx.scene.control.ProgressBar();
            progressBar.progressProperty().bind(task.progressProperty());
            progressAlert.getDialogPane().setContent(progressBar);
            progressAlert.show();
            
            task.runningProperty().addListener((obs, wasRunning, isRunning) -> {
                if (!isRunning) {
                    progressAlert.close();
                }
            });

            new Thread(task).start();
        }
    }

    public void importFromDir() {
        importFromDir(null);
    }

    public void importFromDir(Runnable onSuccess) {
        javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
        chooser.setTitle("Select Directory to Import DIM");
        File lastDir = appState.getLastOpenedDirectory();
        if (lastDir != null && lastDir.exists()) {
            chooser.setInitialDirectory(lastDir);
        }
        File selectedDir = chooser.showDialog(stage);
        if (selectedDir != null) {
            appState.setLastOpenedFilePath(selectedDir);
            
            DimDirImporter importer = new DimDirImporter(appState, new com.github.cfogrady.dim.modifier.SpriteImageTranslator(appState, stage));
            java.util.List<String> errors = importer.validateImport(selectedDir);
            
            if (!errors.isEmpty()) {
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.WARNING);
                alert.setTitle("Validation Errors");
                alert.setHeaderText("Errors found during import validation.");
                
                javafx.scene.control.TextArea textArea = new javafx.scene.control.TextArea(String.join("\n", errors));
                textArea.setEditable(false);
                textArea.setWrapText(true);
                alert.getDialogPane().setContent(textArea);
                
                javafx.scene.control.ButtonType importAnyway = new javafx.scene.control.ButtonType("Import Anyway");
                javafx.scene.control.ButtonType cancel = new javafx.scene.control.ButtonType("Cancel", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
                alert.getButtonTypes().setAll(importAnyway, cancel);
                
                java.util.Optional<javafx.scene.control.ButtonType> result = alert.showAndWait();
                if (result.isEmpty() || result.get() != importAnyway) {
                    return;
                }
            }

            javafx.concurrent.Task<Void> task = new javafx.concurrent.Task<Void>() {
                @Override
                protected Void call() throws Exception {
                    importer.importFromDir(selectedDir, () -> updateProgress(1, 1));
                    return null;
                }
            };
            
            task.setOnSucceeded(e -> {
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
                alert.setTitle("Import Complete");
                alert.setHeaderText(null);
                alert.setContentText("Import from directory completed successfully. Please reload the view or interact to see changes.");
                alert.show();
                if (onSuccess != null) {
                    onSuccess.run();
                }
            });
            
            task.setOnFailed(e -> {
                log.error("Failed to import from dir", task.getException());
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
                alert.setTitle("Import Failed");
                alert.setHeaderText("An error occurred during import.");
                alert.setContentText(task.getException().getMessage());
                alert.show();
            });

            javafx.scene.control.Alert progressAlert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
            progressAlert.setTitle("Importing");
            progressAlert.setHeaderText("Importing from directory...");
            javafx.scene.control.ProgressBar progressBar = new javafx.scene.control.ProgressBar();
            progressBar.progressProperty().bind(task.progressProperty());
            progressAlert.getDialogPane().setContent(progressBar);
            progressAlert.show();
            
            task.runningProperty().addListener((obs, wasRunning, isRunning) -> {
                if (!isRunning) {
                    progressAlert.close();
                }
            });

            new Thread(task).start();
        }
    }
}
