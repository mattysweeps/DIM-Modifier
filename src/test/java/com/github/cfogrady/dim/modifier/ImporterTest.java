package com.github.cfogrady.dim.modifier;

import com.github.cfogrady.dim.modifier.data.AppState;
import com.github.cfogrady.dim.modifier.data.card.CardData;
import com.github.cfogrady.dim.modifier.data.io.DimDirImporter;

import javafx.application.Platform;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;

public class ImporterTest {
    @Test
    public void testImport() throws Exception {
        Assumptions.assumeTrue(System.getenv("CI") == null, "Skipping test on CI environment");
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(() -> latch.countDown());
            latch.await();
        } catch (IllegalStateException e) {
            // Already initialized
        }

        System.out.println("Testing Import...");
        File importDir = new File("testdata/exported_dim");
        assertTrue(importDir.exists(), "Exported directory must exist to run this test");
        
        AppState appState = new AppState();
        DimDirImporter importer = new DimDirImporter(appState, new SpriteImageTranslator(appState, null));
        importer.importFromDir(importDir, null);
        
        CardData<?, ?, ?> cardData = appState.getCardData();
        assertNotNull(cardData, "CardData should not be null after import");
        assertEquals(14, cardData.getCharacters().size(), "There should be 14 characters");
        assertNotNull(cardData.getCardSprites().getLogo(), "Logo should exist");
        assertFalse(cardData.getCardSprites().getBackgrounds().isEmpty(), "Backgrounds should exist");
        
        System.out.println("Import completed successfully!");
    }
}
