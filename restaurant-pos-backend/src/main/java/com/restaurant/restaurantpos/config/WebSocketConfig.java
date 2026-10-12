package com.restaurant.restaurantpos.config;

import com.restaurant.restaurantpos.security.StompAuthChannelInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final List<String> allowedOrigins;
    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    @org.springframework.beans.factory.annotation.Autowired
    public WebSocketConfig(
            StompAuthChannelInterceptor stompAuthChannelInterceptor,
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        this.stompAuthChannelInterceptor = stompAuthChannelInterceptor;
        this.allowedOrigins = allowedOrigins != null ? allowedOrigins : List.of();
    }

    public WebSocketConfig(StompAuthChannelInterceptor stompAuthChannelInterceptor) {
        this(stompAuthChannelInterceptor, List.of("http://localhost:5173", "http://localhost:5174"));
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] originsArray = (allowedOrigins != null ? allowedOrigins : List.<String>of()).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);

        if (originsArray.length == 0) {
            throw new IllegalStateException("WebSocket allowed origins ('app.cors.allowed-origins' / CORS_ALLOWED_ORIGINS) must be configured and cannot be empty.");
        }
        for (String origin : originsArray) {
            if ("*".equals(origin) || origin.contains("*")) {
                throw new IllegalStateException("WebSocket wildcard origins ('*') are strictly prohibited.");
            }
        }

        registry.addEndpoint("/ws")
                .setAllowedOrigins(originsArray)
                .withSockJS();

        registry.addEndpoint("/ws")
                .setAllowedOrigins(originsArray);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }
}
