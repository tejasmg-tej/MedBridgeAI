package com.medbridge.service;

import com.medbridge.exception.BadRequestException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class PdfTextExtractor {

    public String extract(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            if (text == null) {
                return "";
            }
            return text.replace('\u0000', ' ').replaceAll("[ \\t]+\\n", "\n").replaceAll("\\n{3,}", "\n\n").trim();
        } catch (InvalidPasswordException e) {
            throw new BadRequestException("Password-protected PDFs are not supported. Please upload an unprotected copy.");
        } catch (IOException e) {
            throw new BadRequestException("The PDF could not be read. The file may be corrupted.");
        }
    }
}
