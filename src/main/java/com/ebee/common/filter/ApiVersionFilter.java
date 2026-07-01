package com.ebee.common.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.io.IOException;

@Configuration
public class ApiVersionFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiVersionFilter.class);

    private static final String NEW_PREFIX = "/api/v1/";
    private static final String LEGACY_PREFIX = "/api/";
    private static final int NEW_PREFIX_LEN = NEW_PREFIX.length() - 1;

    @Bean
    public FilterRegistrationBean<Filter> versionFilter() {
        FilterRegistrationBean<Filter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new Filter() {
            @Override
            public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
                    throws IOException, ServletException {

                HttpServletRequest request = (HttpServletRequest) servletRequest;
                HttpServletResponse response = (HttpServletResponse) servletResponse;
                String path = request.getServletPath();

                if (path.startsWith(NEW_PREFIX)) {
                    String rewritten = LEGACY_PREFIX + path.substring(NEW_PREFIX_LEN);
                    log.debug("Rewriting {} → {}", path, rewritten);
                    chain.doFilter(new RewrittenPathWrapper(request, rewritten), response);
                } else if (path.equals("/api/v1")) {
                    response.sendRedirect("/api/v1/");
                } else {
                    chain.doFilter(request, response);
                }
            }

            @Override
            public void init(FilterConfig filterConfig) {
            }

            @Override
            public void destroy() {
            }
        });
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        bean.addUrlPatterns("/*");
        return bean;
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
