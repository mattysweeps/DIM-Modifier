package com.github.cfogrady.dim.modifier;

import com.github.cfogrady.dim.modifier.data.AppState;
import com.github.cfogrady.dim.modifier.data.card.CardData;
import com.github.cfogrady.dim.modifier.data.card.CardDataIO;
import com.github.cfogrady.dim.modifier.data.dim.DimCardDataReader;
import com.github.cfogrady.dim.modifier.data.dim.DimCardDataWriter;
import com.github.cfogrady.dim.modifier.data.bem.BemCardDataReader;
import com.github.cfogrady.dim.modifier.data.bem.BemCardDataWriter;
import com.github.cfogrady.vb.dim.card.DimReader;
import com.github.cfogrady.vb.dim.card.DimWriter;
import com.github.cfogrady.dim.modifier.data.io.DimDirExporter;

import javafx.application.Platform;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.util.concurrent.CountDownLatch;

public class ExporterTest {
    @Test
    public void testExport() throws Exception {
        Assumptions.assumeTrue(System.getenv("CI") == null, "Skipping test on CI environment");
        CountDownLatch latch = new CountDownLatch(1);
        Platform.startup(() -> latch.countDown());
        latch.await();

        System.out.println("Testing Export...");
        File inputFile = new File("testdata/dimcard-backup-20260814143100.bin");
        System.out.println("Exists: " + inputFile.exists());
        
        DimReader dimReader = new DimReader();
        DimWriter dimWriter = new DimWriter();
        CardDataIO cardDataIO = new CardDataIO(
                dimReader, dimWriter,
                new DimCardDataWriter(dimWriter), new DimCardDataReader(),
                new BemCardDataWriter(dimWriter), new BemCardDataReader()
        );
        
        CardData<?, ?, ?> cardData = cardDataIO.readFromStream(new FileInputStream(inputFile), false);
        AppState appState = new AppState();
        appState.setCardData(cardData);
        
        System.out.println("CardData loaded! Characters: " + cardData.getCharacters().size());
        
        DimDirExporter exporter = new DimDirExporter(appState, new SpriteImageTranslator(appState, null));
        File outDir = new File("testdata/exported_dim");
        outDir.mkdirs();
        exporter.exportToDir(outDir, null);
        
        System.out.println("Export completed successfully.");
    }
}
