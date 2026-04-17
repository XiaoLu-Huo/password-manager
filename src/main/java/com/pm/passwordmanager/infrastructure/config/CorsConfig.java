package com.pm.passwordmanager.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * CORS 配置。
 * 允许 Electron Desktop App 和 Chrome Extension 的跨域请求。
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // Electron dev server
        config.addAllowedOrigin("http://localhost:5173");
        // Electron production (file:// protocol uses null origin)
        config.addAllowedOrigin("null");
        // Chrome Extension origins (chrome-extension:// sends Origin header)
        config.addAllowedOriginPattern("chrome-extension://*");

        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return new CorsFilter(source);
    }
}
