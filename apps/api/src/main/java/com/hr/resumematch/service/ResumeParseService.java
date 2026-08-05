package com.hr.resumematch.service;

import com.hr.resumematch.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ResumeParseService {

    private final AppProperties props;

    public Path ensureUploadDir() throws IOException {
        Path dir = Path.of(props.getUploadDir()).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        return dir;
    }

    public StoredFile store(MultipartFile file) throws IOException {
        Path dir = ensureUploadDir();
        String original = file.getOriginalFilename() == null ? "resume.txt" : file.getOriginalFilename();
        String safe = original.replaceAll("[\\\\/]+", "_");
        String storedName = UUID.randomUUID() + "_" + safe;
        Path target = dir.resolve(storedName);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return new StoredFile(safe, target.toString());
    }

    public String extractText(Path path, String fileName) throws IOException {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            try (PDDocument doc = Loader.loadPDF(path.toFile())) {
                return new PDFTextStripper().getText(doc);
            }
        }
        if (lower.endsWith(".docx")) {
            try (InputStream in = Files.newInputStream(path); XWPFDocument doc = new XWPFDocument(in)) {
                return doc.getParagraphs().stream().map(XWPFParagraph::getText).collect(Collectors.joining("\n"));
            }
        }
        if (lower.endsWith(".doc")) {
            try (InputStream in = Files.newInputStream(path); HWPFDocument doc = new HWPFDocument(in)) {
                return new WordExtractor(doc).getText();
            }
        }
        // txt / md / 其他按文本读
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    public boolean isSupported(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".pdf") || lower.endsWith(".docx") || lower.endsWith(".doc")
                || lower.endsWith(".txt") || lower.endsWith(".md");
    }

    public String supportFormats() {
        return "PDF、DOCX、DOC、TXT、MD";
    }

    public record StoredFile(String originalName, String storedPath) {}
}
