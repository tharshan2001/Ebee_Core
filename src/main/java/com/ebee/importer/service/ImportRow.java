package com.ebee.importer.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One data row. Getters record a {@link RowError} and return null when a value is missing or
 * malformed, so a whole file can be checked in one pass.
 */
public final class ImportRow {

    private final int rowNumber;
    private final Map<String, String> values;

    public ImportRow(int rowNumber, Map<String, String> values) {
        this.rowNumber = rowNumber;
        this.values = values;
    }

    public int rowNumber() {
        return rowNumber;
    }

    /** The cells as read from the file, keyed by column. */
    public Map<String, String> values() {
        return values;
    }

    public String text(String column) {
        String v = values.get(column);
        return v == null || v.isBlank() ? null : v.trim();
    }

    public String requiredText(String column, List<RowError> errors) {
        String v = text(column);
        if (v == null) {
            errors.add(new RowError(rowNumber, column, "is required"));
        }
        return v;
    }

    /** Records an error when the value is longer than the database column. */
    public void maxLength(String column, int max, List<RowError> errors) {
        String v = text(column);
        if (v != null && v.length() > max) {
            errors.add(new RowError(rowNumber, column, "must be " + max + " characters or fewer"));
        }
    }

    /** Records an error when the number is too large for the database column. */
    public void maxValue(String column, BigDecimal value, BigDecimal max, List<RowError> errors) {
        if (value != null && value.abs().compareTo(max) > 0) {
            errors.add(new RowError(rowNumber, column, "is too large (limit " + max.toPlainString() + ")"));
        }
    }

    public BigDecimal decimal(String column, boolean required, boolean allowNegative, List<RowError> errors) {
        String v = text(column);
        if (v == null) {
            if (required) {
                errors.add(new RowError(rowNumber, column, "is required"));
            }
            return null;
        }
        try {
            BigDecimal d = new BigDecimal(v.replace(",", ""));
            if (!allowNegative && d.signum() < 0) {
                errors.add(new RowError(rowNumber, column, "must not be negative"));
                return null;
            }
            return d;
        } catch (NumberFormatException e) {
            errors.add(new RowError(rowNumber, column, "'" + v + "' is not a number"));
            return null;
        }
    }

    public Integer positiveInt(String column, List<RowError> errors) {
        String v = requiredText(column, errors);
        if (v == null) {
            return null;
        }
        try {
            BigDecimal d = new BigDecimal(v.replace(",", ""));
            if (d.signum() <= 0 || d.stripTrailingZeros().scale() > 0) {
                errors.add(new RowError(rowNumber, column, "must be a whole number above 0"));
                return null;
            }
            return d.intValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            errors.add(new RowError(rowNumber, column, "'" + v + "' is not a whole number"));
            return null;
        }
    }

    public LocalDate date(String column, List<RowError> errors) {
        String v = text(column);
        if (v == null) {
            return null;
        }
        try {
            return LocalDate.parse(v.length() > 10 ? v.substring(0, 10) : v);
        } catch (DateTimeParseException e) {
            errors.add(new RowError(rowNumber, column, "'" + v + "' is not a date (use YYYY-MM-DD)"));
            return null;
        }
    }

    public Boolean bool(String column, List<RowError> errors) {
        String v = text(column);
        if (v == null) {
            return null;
        }
        return switch (v.toLowerCase(Locale.ROOT)) {
            case "true", "yes", "y", "1" -> true;
            case "false", "no", "n", "0" -> false;
            default -> {
                errors.add(new RowError(rowNumber, column, "'" + v + "' must be 1 or 0 (yes or no also work)"));
                yield null;
            }
        };
    }
}
