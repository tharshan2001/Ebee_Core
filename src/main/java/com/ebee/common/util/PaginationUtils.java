package com.ebee.common.util;

/**
 * Centralised guards for the {@code page} and {@code size} parameters
 * accepted by paginated controller endpoints. Without these, a caller
 * can request arbitrarily large page sizes (DoS) or negative page
 * indices, which Spring Data silently turns into a query for the
 * first page.
 */
public final class PaginationUtils {

    private PaginationUtils() {}

    /** Maximum page size accepted by any paginated endpoint. */
    public static final int MAX_PAGE_SIZE = 200;

    /** Default page size when the caller omits {@code size}. */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * Clamp a request-supplied page index to a non-negative integer.
     * Negative or NaN inputs collapse to 0.
     */
    public static int safePage(int page) {
        if (page < 0) {
            return 0;
        }
        return page;
    }

    /**
     * Clamp a request-supplied page size to the range
     * [1, {@link #MAX_PAGE_SIZE}]. Inputs that are zero, negative, or
     * too large collapse to {@link #MAX_PAGE_SIZE} so a request for
     * "everything" still gets a bounded result.
     */
    public static int safeSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
