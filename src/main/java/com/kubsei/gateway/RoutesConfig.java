package com.kubsei.gateway;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.support.RouteMetadataUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Configuration
public class RoutesConfig {

    /** One route per security.gateway.filter, in declaration order: put specific paths before catch-alls. */
    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder, GatewayRoutesProperties properties) {
        RouteLocatorBuilder.Builder routes = builder.routes();
        List<GatewayRoutesProperties.Filter> filters = properties.filter();

        for (int i = 0; i < filters.size(); i++) {
            GatewayRoutesProperties.Filter filter = filters.get(i);
            int order = i;
            String[] rewrite = rewrite(filter.origin(), filter.destination());
            Map<String, Object> metadata = filter.timeout() == null ? Map.of()
                    : Map.of(RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR, filter.timeout().toMillis());

            routes.route(filter.id(), route -> {
                var predicate = route.order(order).path(filter.origin());
                if (!filter.methods().isEmpty()) {
                    predicate = predicate.and().method(filter.methods().toArray(HttpMethod[]::new));
                }
                return predicate
                        .filters(f -> f.rewritePath(rewrite[0], rewrite[1]))
                        .metadata(metadata)
                        .uri(properties.service().get(filter.service()).uri());
            });
        }
        return routes.build();
    }

    // Origin pattern -> {regex, replacement} for RewritePath.
    //   /orders/*/pdf  -> /api/orders/*/pdf : each "*" segment is carried over, in order
    //   /products/**   -> /api/products     : whatever follows /products is appended
    static String[] rewrite(String origin, String destination) {
        boolean rest = origin.endsWith("/**");
        String base = rest ? origin.substring(0, origin.length() - 3) : origin;
        if (base.contains("**")) {
            throw new IllegalStateException("'**' is only allowed at the end of an origin: " + origin);
        }

        StringBuilder regex = new StringBuilder("^");
        String[] literals = base.split("\\*", -1);
        for (int i = 0; i < literals.length; i++) {
            if (i > 0) {
                regex.append("(?<w").append(i).append(">[^/]+)");
            }
            if (!literals[i].isEmpty()) {
                regex.append(Pattern.quote(literals[i]));
            }
        }
        if (rest) {
            regex.append("(?<rest>/.*)?");
        }
        regex.append('$');

        int wildcards = literals.length - 1;
        String[] targets = destination.replace("${inmask}", "*").split("\\*", -1);
        if (targets.length - 1 > wildcards) {
            throw new IllegalStateException("Destination '" + destination + "' uses more '*' than origin '" + origin + "'");
        }
        StringBuilder replacement = new StringBuilder(targets[0]);
        for (int i = 1; i < targets.length; i++) {
            replacement.append("${w").append(i).append('}').append(targets[i]);
        }
        if (rest) {
            replacement.append("${rest}");
        }
        return new String[]{regex.toString(), replacement.toString()};
    }
}
