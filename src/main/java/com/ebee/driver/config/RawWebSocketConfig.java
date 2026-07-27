package com.ebee.driver.config;

import com.ebee.common.config.AppProperties;
import com.ebee.driver.websocket.DriverLocationWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

import java.util.List;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class RawWebSocketConfig implements WebSocketConfigurer {

    private final DriverLocationWebSocketHandler driverLocationHandler;
    private final AppProperties appProperties;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String origins = appProperties.getCors().getAllowedOrigins();
        List<String> allowedOrigins = List.of(origins.split(","))
                .stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        registry.addHandler(driverLocationHandler, "/raw-ws/driver")
                .addInterceptors(new HttpSessionHandshakeInterceptor())
                .setAllowedOrigins(allowedOrigins.toArray(new String[0]));
    }
}
