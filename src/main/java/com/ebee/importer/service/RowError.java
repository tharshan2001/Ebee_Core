package com.ebee.importer.service;

/** A problem with one cell or row; row is the spreadsheet row number (header = 1). */
public record RowError(int row, String column, String message) {
}
