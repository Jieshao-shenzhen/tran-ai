package com.gdcp.lab.gateway.filter;

import com.gdcp.lab.common.constant.AuthConst;
import com.gdcp.lab.common.jwt.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {
    private final JwtUtil jwtUtil;

    public AuthGlobalFilter(@Value("${lab.jwt.secret}") String secret) {
        this.jwtUtil = new JwtUtil(secret);
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
            return chain.filter(mutated);
        } catch (Exception e) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    @Override
    public int getOrder() { return -100; }
}
