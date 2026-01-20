package com.axel.pennywise.util;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

@Slf4j
public class RequestIdFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String requestId = Optional.ofNullable(request.getHeader(HEADER))
                .filter(v -> !v.isBlank())
                .orElse(UUID.randomUUID().toString());

        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);

        log.info("Incoming request: method={}, path={}, requestId={}, remoteAddr={}",
                request.getMethod(), request.getRequestURI(), requestId, request.getRemoteAddr());

        long startTime = System.currentTimeMillis();

        try {
            filterChain.doFilter(request, response);
            long duration = System.currentTimeMillis() - startTime;
            log.info("Request completed: method={}, path={}, requestId={}, status={}, durationMs={}",
                    request.getMethod(), request.getRequestURI(), requestId, response.getStatus(), duration);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Request failed: method={}, path={}, requestId={}, durationMs={}",
                    request.getMethod(), request.getRequestURI(), requestId, duration, e);
            throw e;
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
