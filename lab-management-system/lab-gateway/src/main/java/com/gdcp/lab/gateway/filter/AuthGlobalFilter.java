package com.gdcp.lab.gateway.filter;

import com.gdcp.lab.common.constant.AuthConst;
import com.gdcp.lab.common.jwt.JwtUtil;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {
    private static final Logger log = LoggerFactory.getLogger(AuthGlobalFilter.class);
    private final JwtUtil jwtUtil;
    private final WebClient webClient;

    public AuthGlobalFilter(WebClient.Builder webClientBuilder,
                            @Value("${lab.jwt.secret}") String secret) {
        this.jwtUtil = new JwtUtil(secret);
        this.webClient = webClientBuilder.build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (path.startsWith("/api/v1/auth/login")) {
            return chain.filter(exchange);
        }
        String header = exchange.getRequest().getHeaders().getFirst(AuthConst.AUTH_HEADER);
        if (header == null || !header.startsWith(AuthConst.TOKEN_PREFIX)) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
        try {
            Claims claims = jwtUtil.parse(header.substring(AuthConst.TOKEN_PREFIX.length()));
            ServerWebExchange mutated = exchange.mutate()
                    .request(r -> r.header("X-User-Id", claims.get("userId").toString())
                            .header("X-User-Role", claims.get("role").toString()))
                    .build();
            String method = exchange.getRequest().getMethod().name();
            if (!"GET".equals(method)) {
                recordLog(claims.get("userId").toString(), resolveClientIp(exchange.getRequest()),
                        method.trim() + " " + path);
            }
            return chain.filter(mutated);
        } catch (Exception e) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    private void recordLog(String userId, String ip, String action) {
        try {
            Map<String, Object> body = Map.of(
                    "userId", Long.valueOf(userId),
                    "ip", ip,
                    "action", action);
            webClient.post()
                    .uri("http://lab-user-service/api/v1/internal/oplog")
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .toBodilessEntity()
                    .subscribe(r -> {
                    }, e -> log.warn("记录操作日志失败: {}", e.getMessage()));
        } catch (Exception e) {
            log.warn("记录操作日志失败: {}", e.getMessage());
        }
    }

    private String resolveClientIp(ServerHttpRequest request) {
        String xff = request.getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }
        return "unknown";
    }

    @Override
    public int getOrder() { return -100; }
}