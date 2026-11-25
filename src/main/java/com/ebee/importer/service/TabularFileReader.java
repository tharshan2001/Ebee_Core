package com.ebee.importer.service;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Reads the first sheet of an .xlsx, or a .csv, into rows keyed by normalised header (snake_case). */
public final class TabularFileReader {

    public static final int MAX_ROWS = 5000;

    private TabularFileReader() {
    }

    public record Parsed(List<String> headers, List<ImportRow> rows) {
    }

    public static Parsed read(String fileName, InputStream in) throws IOException {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".xlsx")) {
            return readXlsx(in);
        }
        if (name.endsWith(".csv") || name.endsWith(".txt") || name.isEmpty()) {
            return readCsv(in);
        }
        throw new IllegalArgumentException("Upload a .csv or .xlsx file (old .xls files aren't supported)");
    }

    static String normalizeHeader(String h) {
        if (h == null) {
            return "";
        }
        return h.replace("﻿", "").trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    }

    private static Parsed readCsv(InputStream in) throws IOException {
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true).setTrim(true).build();
        try (CSVParser parser = CSVParser.parse(new InputStreamReader(in, StandardCharsets.UTF_8), format)) {
            List<String> headers = parser.getHeaderNames().stream().map(TabularFileReader::normalizeHeader).toList();
            List<String> raw = parser.getHeaderNames();
            List<ImportRow> rows = new ArrayList<>();
            for (CSVRecord rec : parser) {
                Map<String, String> values = new LinkedHashMap<>();
                for (int i = 0; i < raw.size(); i++) {
                    values.put(headers.get(i), rec.isSet(raw.get(i)) ? rec.get(raw.get(i)) : null);
                }
                addRow(rows, (int) rec.getRecordNumber() + 1, values);
            }
            return new Parsed(headers, rows);
        }
    }

    private static Parsed readXlsx(InputStream in) throws IOException {
        try (Workbook wb = new XSSFWorkbook(in)) {
            Sheet sheet = wb.getSheetAt(0);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                return new Parsed(List.of(), List.of());
            }
            List<String> headers = new ArrayList<>();
            for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                headers.add(normalizeHeader(cellText(headerRow.getCell(c))));
            }
            List<ImportRow> rows = new ArrayList<>();
            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                Map<String, String> values = new LinkedHashMap<>();
                boolean any = false;
                for (int c = 0; c < headers.size(); c++) {
                    String v = cellText(row.getCell(c));
                    any |= v != null && !v.isBlank();
                    values.put(headers.get(c), v);
                }
                if (any) {
                    addRow(rows, r + 1, values);
                }
            }
            return new Parsed(headers, rows);
        }
    }

    private static void addRow(List<ImportRow> rows, int rowNumber, Map<String, String> values) {
        if (rows.size() >= MAX_ROWS) {
            throw new IllegalArgumentException("Too many rows: the limit is " + MAX_ROWS + " per file; split it up");
        }
        rows.add(new ImportRow(rowNumber, values));
    }

    /** Cell as text: numbers without float noise, dates as YYYY-MM-DD. */
    static String cellText(Cell cell) {
        if (cell == null) {
            return null;
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        return switch (type) {
            case STRING -> cell.getStringCellValue();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toLocalDate().toString()
                    : BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            default -> null;
        };
    }
}
