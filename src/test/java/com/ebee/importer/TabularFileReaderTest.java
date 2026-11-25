package com.ebee.importer;

import com.ebee.importer.service.TabularFileReader;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TabularFileReaderTest {

    private static ByteArrayInputStream csv(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void csv_normalisesHeaders_andHandlesBomQuotesAndBlankLines() throws Exception {
        var p = TabularFileReader.read("products.csv", csv("﻿Item Code,Name,Price (LKR)\nA-1,\"Oil, 1L\",1250\n\nA-2,Rice,300\n"));

        assertThat(p.headers()).containsExactly("item_code", "name", "price_lkr");
        assertThat(p.rows()).hasSize(2);
        assertThat(p.rows().get(0).text("name")).isEqualTo("Oil, 1L");
        assertThat(p.rows().get(0).rowNumber()).isEqualTo(2);
    }

    @Test
    void xlsx_readsNumbersWithoutFloatNoise_andDatesAsIso() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (var wb = new XSSFWorkbook()) {
            var sheet = wb.createSheet();
            var h = sheet.createRow(0);
            h.createCell(0).setCellValue("item_code");
            h.createCell(1).setCellValue("quantity");
            h.createCell(2).setCellValue("expiry_date");
            var r = sheet.createRow(1);
            r.createCell(0).setCellValue("SKU-1");
            r.createCell(1).setCellValue(120.0);
            var dateCell = r.createCell(2);
            dateCell.setCellValue(LocalDate.of(2027, 3, 31));
            var style = wb.createCellStyle();
            style.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));
            dateCell.setCellStyle(style);
            sheet.createRow(2); // empty row is skipped
            wb.write(out);
        }

        var p = TabularFileReader.read("stock.xlsx", new ByteArrayInputStream(out.toByteArray()));

        assertThat(p.rows()).hasSize(1);
        assertThat(p.rows().get(0).text("quantity")).isEqualTo("120");
        assertThat(p.rows().get(0).text("expiry_date")).isEqualTo("2027-03-31");
    }

    @Test
    void oldExcelFormat_isRejected() {
        assertThatThrownBy(() -> TabularFileReader.read("legacy.xls", csv("x")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(".xlsx");
    }

    @Test
    void moreThanTheRowLimit_isRejected() {
        StringBuilder sb = new StringBuilder("item_code\n");
        for (int i = 0; i <= TabularFileReader.MAX_ROWS; i++) {
            sb.append("X").append(i).append('\n');
        }
        assertThatThrownBy(() -> TabularFileReader.read("big.csv", csv(sb.toString())))
                .hasMessageContaining("Too many rows");
    }
}
