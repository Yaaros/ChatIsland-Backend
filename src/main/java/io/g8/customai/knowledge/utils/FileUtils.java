package io.g8.customai.knowledge.utils;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Iterator;

@Component
public class FileUtils {

    public String extractTextFromFile(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();
        String contentType = file.getContentType();
        InputStream inputStream = file.getInputStream();
        byte[] inputs = file.getBytes();
        // Extract text based on file type
        if (fileName != null) {
            if (fileName.endsWith(".pdf")) {
                return extractTextFromPdf(inputs);
            } else if (fileName.endsWith(".docx")) {
                return extractTextFromDocx(inputStream);
            } else if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) {
                return extractTextFromExcel(inputStream, fileName);
            } else if (fileName.endsWith(".txt") || contentType != null && contentType.contains("text/plain")) {
                return extractTextFromTxt(inputStream);
            } else if (contentType != null && contentType.contains("text/markdown") || fileName.endsWith(".md")) {
                return extractTextFromTxt(inputStream);
            } else {
                throw new IOException("不支持的文件类型: " + fileName);
            }
        } else {
            throw new IOException("无效的文件名");
        }
    }

    private String extractTextFromPdf(byte[] inputs) throws IOException {
        try (PDDocument document = Loader.loadPDF(inputs)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private String extractTextFromDocx(InputStream inputStream) throws IOException {
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            XWPFWordExtractor extractor = new XWPFWordExtractor(document);
            return extractor.getText();
        }
    }

    private String extractTextFromExcel(InputStream inputStream, String fileName) throws IOException {
        Workbook workbook;
        if (fileName.endsWith(".xlsx")) {
            workbook = new XSSFWorkbook(inputStream);
        } else {
            workbook = new HSSFWorkbook(inputStream);
        }

        StringBuilder text = new StringBuilder();
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            Sheet sheet = workbook.getSheetAt(i);
            text.append("Sheet: ").append(sheet.getSheetName()).append("\n");

            Iterator<Row> rowIterator = sheet.iterator();
            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                Iterator<Cell> cellIterator = row.cellIterator();

                while (cellIterator.hasNext()) {
                    Cell cell = cellIterator.next();
                    switch (cell.getCellType()) {
                        case STRING:
                            text.append(cell.getStringCellValue()).append("\t");
                            break;
                        case NUMERIC:
                            text.append(cell.getNumericCellValue()).append("\t");
                            break;
                        case BOOLEAN:
                            text.append(cell.getBooleanCellValue()).append("\t");
                            break;
                        default:
                            text.append("\t");
                    }
                }
                text.append("\n");
            }
        }

        workbook.close();
        return text.toString();
    }

    private String extractTextFromTxt(InputStream inputStream) throws IOException {
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line).append("\n");
            }
        }
        return text.toString();
    }
}