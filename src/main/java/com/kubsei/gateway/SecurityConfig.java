package com.kubsei.gateway;

import com.kubsei.gateway.api.model.Problem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.util.matcher.OrServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

/** Edge check: rejects bad tokens before routing. Services validate the same token again. */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
                                                         ServerAuthenticationEntryPoint problemEntryPoint,
                                                         GatewayRoutesProperties routes) {
        ServerBearerTokenAuthenticationConverter defaultConverter = new ServerBearerTokenAuthenticationConverter();
        // permit-all filters work with no token, or with an expired one (e.g. refresh)
        ServerWebExchangeMatcher permitAll = permitAllMatcher(routes);

        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(Customizer.withDefaults())
                .exceptionHandling(exception -> exception.authenticationEntryPoint(problemEntryPoint))
                .authorizeExchange(auth -> auth
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .matchers(permitAll).permitAll()
                        .pathMatchers("/actuator/health/**").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenConverter(exchange -> permitAll.matches(exchange)
                                .flatMap(match -> match.isMatch() ? Mono.empty() : defaultConverter.convert(exchange)))
                        .authenticationEntryPoint(problemEntryPoint)
                        .jwt(Customizer.withDefaults()));

        return http.build();
    }

    private static ServerWebExchangeMatcher permitAllMatcher(GatewayRoutesProperties routes) {
        List<ServerWebExchangeMatcher> matchers = new ArrayList<>();
        for (GatewayRoutesProperties.Filter filter : routes.filter()) {
            if (!filter.permitAll()) continue;
            if (filter.methods().isEmpty()) {
                matchers.add(new PathPatternParserServerWebExchangeMatcher(filter.origin()));
            }
            filter.methods().forEach(method ->
                    matchers.add(new PathPatternParserServerWebExchangeMatcher(filter.origin(), method)));
        }
        return matchers.isEmpty() ? exchange -> ServerWebExchangeMatcher.MatchResult.notMatch()
                : new OrServerWebExchangeMatcher(matchers);
    }

    @Bean
    public ServerAuthenticationEntryPoint problemEntryPoint(JsonMapper jsonMapper) {
        return (exchange, ex) -> {
            var response = exchange.getResponse();
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
            byte[] body = jsonMapper.writeValueAsBytes(new Problem().title("Unauthorized").status(401)
                    .detail(ex.getMessage()).instance(exchange.getRequest().getPath().value()));
            return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
        };
    }

    /** CORS lives only here; services behind the gateway don't set CORS headers. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") List<String> origins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOrigins(origins);
        config.setAllowedHeaders(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
