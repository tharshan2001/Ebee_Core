package com.ebee.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiVersionFilter extends OncePerRequestFilter {

    private static final String NEW_PREFIX = "/api/v1/";
    private static final String LEGACY_PREFIX = "/api/";
    private static final int NEW_PREFIX_LENGTH = NEW_PREFIX.length() - 1;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !path.startsWith(NEW_PREFIX) && !path.startsWith(LEGACY_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String path = request.getServletPath();

        if (path.startsWith(NEW_PREFIX)) {
            String rewritten = LEGACY_PREFIX + path.substring(NEW_PREFIX_LENGTH);
            chain.doFilter(new RewrittenPathWrapper(request, rewritten), response);
        } else if (path.equals("/api/v1")) {
            response.sendRedirect("/api/v1/");
        } else {
            chain.doFilter(request, response);
        }
    }

    private static class RewrittenPathWrapper extends HttpServletRequestWrapper {

        private final String rewrittenServletPath;

        RewrittenPathWrapper(HttpServletRequest request, String rewrittenServletPath) {
            super(request);
            this.rewrittenServletPath = rewrittenServletPath;
        }

        @Override
        public String getServletPath() {
            return rewrittenServletPath;
        }

        @Override
        public String getRequestURI() {
            return getContextPath() + rewrittenServletPath;
        }
    }
}
