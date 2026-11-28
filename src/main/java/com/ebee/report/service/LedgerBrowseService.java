package com.ebee.report.service;

import com.ebee.common.response.PageResponse;
import com.ebee.common.util.SearchPatterns;
import com.ebee.report.dto.LedgerPage;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Pages through the customer, supplier and agent ledgers. The running balance is worked out in the
 * database over each party's whole history, so it is right on any page and for any search.
 */
@Service
public class LedgerBrowseService {

    private enum Book {
        CUSTOMER("customer_ledger", "customer_id", "l.debit - l.credit",
                "p.customer_code", "CONCAT(p.first_name, ' ', p.last_name)",
                "LEFT JOIN customers p ON p.id = l.customer_id"),
        SUPPLIER("supplier_ledger", "supplier_id", "l.credit - l.debit",
                "CAST(NULL AS varchar)", "p.name",
                "LEFT JOIN suppliers p ON p.id = l.supplier_id"),
        AGENT("agent_ledger", "agent_id", "l.credit - l.debit",
                "p.agent_code", "CONCAT(u.first_name, ' ', u.last_name)",
                "LEFT JOIN agents p ON p.id = l.agent_id LEFT JOIN users u ON u.id = p.user_id");

        private final String table;
        private final String party;
        private final String signed;
        /** All entries with party code, name and running balance. Built from constants only. */
        private final String entries;

        Book(String table, String party, String signed, String code, String name, String joins) {
            this.table = table;
            this.party = party;
            this.signed = signed;
            this.entries = "SELECT l.id, l.created_at, l.transaction_type, l.description, l.debit, l.credit, "
                    + code + " AS code, " + name + " AS name, "
                    + "SUM(" + signed + ") OVER (PARTITION BY l." + party + " ORDER BY l.created_at, l.id) AS balance "
                    + "FROM " + table + " l " + joins;
        }
    }

    private static final String MATCHES =
            " WHERE LOWER(CONCAT(COALESCE(code, ''), ' ', COALESCE(name, ''), ' ', COALESCE(description, ''))) LIKE :q";

    private final NamedParameterJdbcTemplate jdbc;

    public LedgerBrowseService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public LedgerPage page(String bookName, String search, int page, int size) {
        Book book;
        try {
            book = Book.valueOf(bookName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Ledger must be customer, supplier or agent");
        }
        var params = new MapSqlParameterSource("q", SearchPatterns.contains(search))
                .addValue("size", size).addValue("offset", (long) page * size);
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM (" + book.entries + ") e" + MATCHES, params, Long.class);
        List<LedgerPage.Row> rows = jdbc.query("SELECT * FROM (" + book.entries + ") e" + MATCHES
                + " ORDER BY created_at DESC, id DESC LIMIT :size OFFSET :offset", params, (rs, i) -> new LedgerPage.Row(
                rs.getObject("id", UUID.class), rs.getTimestamp("created_at").toLocalDateTime(), rs.getString("code"),
                rs.getString("name"), rs.getString("transaction_type"), rs.getString("description"),
                rs.getBigDecimal("debit"), rs.getBigDecimal("credit"), rs.getBigDecimal("balance")));
        int pages = (int) ((total + size - 1) / size);
        return new LedgerPage(new PageResponse<>(rows, page, size, total, pages, page == 0, page + 1 >= pages),
                total(Book.CUSTOMER), total(Book.SUPPLIER), total(Book.AGENT));
    }

    private BigDecimal total(Book book) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(" + book.signed + "), 0) FROM " + book.table + " l",
                new MapSqlParameterSource(), BigDecimal.class);
    }
}
