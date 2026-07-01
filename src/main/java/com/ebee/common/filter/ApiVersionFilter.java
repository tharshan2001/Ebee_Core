package com.ebee.common.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class ApiVersionFilter {

    private static final int SPRING_SECURITY_FILTER_ORDER = -100;

    @Bean
    public FilterRegistrationBean<Filter> apiVersionFilterRegistration() {
        FilterRegistrationBean<Filter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new Filter() {
            @Override
            public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
                    throws IOException, ServletException {
                HttpServletRequest request = (HttpServletRequest) servletRequest;
                HttpServletResponse response = (HttpServletResponse) servletResponse;
                String path = request.getServletPath();

                if (path.startsWith("/api/v1/")) {
                    String rewritten = "/api" + path.substring(7);
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
        bean.setOrder(SPRING_SECURITY_FILTER_ORDER - 100);
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
