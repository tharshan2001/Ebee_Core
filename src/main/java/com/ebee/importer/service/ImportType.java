package com.ebee.importer.service;

import java.util.List;

/** What can be imported, with its columns (required first) and a sample row for the template. */
public enum ImportType {

    PRODUCTS(List.of("name", "category", "price"),
            List.of("item_code", "cost_price", "brand", "description", "vat", "vat_rate", "tracks_expiry"),
            List.of("Coconut oil 1L", "Grocery", "1250.00", "ITEM-001", "900.00", "Kandy Gold", "Cold pressed", "1", "", "false")),

    OPENING_STOCK(List.of("item_code", "quantity", "unit_cost"),
            List.of("location_code", "lot_number", "expiry_date", "manufactured_date"),
            List.of("ITEM-001", "120", "900.00", "MAIN", "LOT-2026-11", "2027-11-30", "2026-11-01")),

    CUSTOMERS(List.of("phone", "first_name", "district"),
            List.of("phone_2", "last_name", "street", "city", "email", "vat_number", "credit_limit", "opening_balance",
                    "address_line2", "postal_code", "book_no"),
            List.of("0771234567", "Nimal", "JF", "0212345678", "Perera", "12 Main Street", "Jaffna", "nimal@example.com",
                    "", "50000", "12500.00", "", "40000", "")),

    SUPPLIERS(List.of("name", "address_line1", "city"),
            List.of("contact_person", "email", "phone", "district", "opening_balance"),
            List.of("Lanka Oils (Pvt) Ltd", "45 Factory Road", "Jaffna", "Sunil", "sales@lankaoils.lk", "0212345678",
                    "JF", "250000.00"));

    private final List<String> requiredColumns;
    private final List<String> optionalColumns;
    private final List<String> sampleRow;

    ImportType(List<String> requiredColumns, List<String> optionalColumns, List<String> sampleRow) {
        this.requiredColumns = requiredColumns;
        this.optionalColumns = optionalColumns;
        this.sampleRow = sampleRow;
    }

    public List<String> requiredColumns() { return requiredColumns; }
    public List<String> optionalColumns() { return optionalColumns; }

    public List<String> allColumns() {
        List<String> all = new java.util.ArrayList<>(requiredColumns);
        all.addAll(optionalColumns);
        return all;
    }

    /** A CSV template: header plus one example row. */
    public String templateCsv() {
        return String.join(",", allColumns()) + "\n"
                + String.join(",", sampleRow.stream().map(v -> v.contains(",") ? "\"" + v + "\"" : v).toList()) + "\n";
    }

    public static ImportType fromPath(String value) {
        return ImportType.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_'));
    }
}
