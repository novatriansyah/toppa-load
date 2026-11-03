package com.toppaload.shared;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class OwaspHeadersAndRateLimitConfig {

    @Bean
    public FilterRegistrationBean<RateLimitingFilter> rateLimitingFilter() {
        FilterRegistrationBean<RateLimitingFilter> reg = new FilterRegistrationBean<>(new RateLimitingFilter());
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return reg;
    }

    public static class RateLimitingFilter extends OncePerRequestFilter {

        private static final int MAX_MUTATIONS_PER_MINUTE = 120;
        private final ConcurrentHashMap<String, WindowCounter> clientCounters = new ConcurrentHashMap<>();

        private record WindowCounter(long windowMinute, AtomicInteger count) {}

        @Override
        protected void doFilterInternal(
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain filterChain
        ) throws ServletException, IOException {
            String method = request.getMethod().toUpperCase();
            String path = request.getRequestURI();

            // Only apply rate limiting to mutating operations on API endpoints; GET / polling is NEVER blocked (NAT-friendly!)
            boolean isMutation = "POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method) || "PATCH".equals(method);
            boolean isApiEndpoint = path.startsWith("/api/v1/") || path.startsWith("/oauth2/");

            if (isMutation && isApiEndpoint) {
                String clientIp = resolveClientIp(request);
                long currentMinute = System.currentTimeMillis() / 60000;

                WindowCounter counter = clientCounters.compute(clientIp, (key, existing) -> {
                    if (existing == null || existing.windowMinute != currentMinute) {
                        return new WindowCounter(currentMinute, new AtomicInteger(1));
                    }
                    existing.count.incrementAndGet();
                    return existing;
                });

                if (counter.count.get() > MAX_MUTATIONS_PER_MINUTE) {
                    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                    response.setHeader("Retry-After", "60");
                    response.setContentType("application/problem+json");
                    response.getWriter().write("""
                        {"type":"about:blank","title":"Terlalu Banyak Permintaan","status":429,"detail":"Batas kecepatan mutasi terlampaui. Coba lagi dalam 1 menit."}
                    """);
                    return;
                }
            }

            filterChain.doFilter(request, response);
        }

        private String resolveClientIp(HttpServletRequest request) {
            String xfHeader = request.getHeader("X-Forwarded-For");
            if (xfHeader != null && !xfHeader.isBlank()) {
                return xfHeader.split(",")[0].trim();
            }
            return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
        }
    }
}

