package com.ebee.common.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ApiVersionFilter implements Filter {

    private static final String NEW_PREFIX = "/api/v1/";
    private static final String LEGACY_PREFIX = "/api/";
    private static final int NEW_PREFIX_LEN = NEW_PREFIX.length() - 1;

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        String path = request.getServletPath();

        if (path.startsWith(NEW_PREFIX)) {
            String rewritten = LEGACY_PREFIX + path.substring(NEW_PREFIX_LEN);
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
