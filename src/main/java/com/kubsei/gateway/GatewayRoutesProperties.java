package com.kubsei.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpMethod;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Routing table of the gateway: security.gateway.filter[n] + security.gateway.service.&lt;name&gt;.uri.
 * Adding an application = adding its service uri and its filters, no code.
 */
@ConfigurationProperties(prefix = "security.gateway")
public record GatewayRoutesProperties(List<Filter> filter, Map<String, Service> service) {

    public GatewayRoutesProperties {
        filter = filter == null ? List.of() : filter;
        service = service == null ? Map.of() : service;
        for (Filter f : filter) {
            if (!service.containsKey(f.service())) {
                throw new IllegalStateException("security.gateway filter '" + f.id()
                        + "' points to undeclared service '" + f.service() + "'");
            }
        }
    }

    /**
     * @param origin      public path. "*" = one segment, trailing "/**" = any rest (appended to destination)
     * @param destination path in the service. "*" or ${inmask} take the origin's "*" segments, in order
     * @param methods     empty = any method
     * @param permitAll   true = reachable without a token (any bearer sent is ignored here)
     * @param timeout     response timeout for this route, overrides the httpclient default
     */
    public record Filter(String id, String origin, String destination, String service,
                         List<HttpMethod> methods, boolean permitAll, Duration timeout) {

        public Filter {
            methods = methods == null ? List.of() : methods;
            destination = destination == null ? origin : destination;
        }
    }

    public record Service(URI uri) {
    }
}
