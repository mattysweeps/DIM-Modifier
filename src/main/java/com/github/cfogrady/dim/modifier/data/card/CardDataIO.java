package com.github.cfogrady.dim.modifier.data.card;

import com.github.cfogrady.dim.modifier.data.bem.BemCardData;
import com.github.cfogrady.dim.modifier.data.bem.BemCardDataReader;
import com.github.cfogrady.dim.modifier.data.bem.BemCardDataWriter;
import com.github.cfogrady.dim.modifier.data.dim.DimCardData;
import com.github.cfogrady.dim.modifier.data.dim.DimCardDataReader;
import com.github.cfogrady.dim.modifier.data.dim.DimCardDataWriter;
import com.github.cfogrady.vb.dim.card.BemCard;
import com.github.cfogrady.vb.dim.card.Card;
import com.github.cfogrady.vb.dim.card.DimCard;
import com.github.cfogrady.vb.dim.card.DimReader;
import lombok.RequiredArgsConstructor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

@RequiredArgsConstructor
public class CardDataIO {
    private final DimReader dimReader;
    private final com.github.cfogrady.vb.dim.card.DimWriter dimWriter;
    private final DimCardDataWriter dimCardDataWriter;
    private final DimCardDataReader dimCardDataReader;
    private final BemCardDataWriter bemCardDataWriter;
    private final BemCardDataReader bemCardDataReader;

    public CardData<?, ?, ?> readFromStream(InputStream inputStream) throws IOException {
        return readFromStream(inputStream, true);
    }

    public CardData<?, ?, ?> readFromStream(InputStream inputStream, boolean verifyChecksum) throws IOException {
        Card card = dimReader.readCard(inputStream, verifyChecksum);
        if(verifyChecksum && card.getChecksum() != card.getCalculatedCheckSum()) {
            throw new IllegalStateException("Checksum mismatch! Calculated: " + Integer.toHexString(card.getCalculatedCheckSum()) + " Received: " + Integer.toHexString(card.getChecksum()));
        }
        if(card instanceof BemCard bemCard) {
            return bemCardDataReader.fromCard(bemCard);
        } else if(card instanceof DimCard dimCard) {
            return dimCardDataReader.fromCard(dimCard);
        } else {
            throw new IllegalArgumentException("Unknown card type: " + card.getClass().getName());
        }
    }

    public void writeToFile(CardData<?, ?, ?> cardData, File file) {
        if(cardData instanceof BemCardData bemCardData) {
            bemCardDataWriter.write(file, bemCardData);
        } else if (cardData instanceof DimCardData dimCardData) {
            dimCardDataWriter.write(file, dimCardData);
        } else {
            throw new IllegalArgumentException("Unknown CardData type: " + cardData.getClass().getName());
        }
    }

    public int calculateSize(CardData<?, ?, ?> cardData) {
        if (cardData == null) return 0;
        try {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            if (cardData instanceof BemCardData bemCardData) {
                BemCard bemCard = bemCardDataWriter.mergeBack(bemCardData);
                dimWriter.writeCard(bemCard, baos);
            } else if (cardData instanceof DimCardData dimCardData) {
                DimCard dimCard = dimCardDataWriter.mergeBack(dimCardData);
                dimWriter.writeCard(dimCard, baos);
            }
            return baos.size();
        } catch (Exception e) {
            return 0;
        }
    }
}
