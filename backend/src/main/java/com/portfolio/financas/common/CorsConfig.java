package com.portfolio.financas.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Origem do frontend e lida de APP_CORS_ORIGIN (default localhost:5173, a
 * porta padrao do Vite em dev) -- sem isso, o frontend publicado na Vercel
 * teria toda chamada bloqueada pelo browser mesmo com o backend saudavel
 * (funciona via curl/Postman, falha so na UI real; ja visto no
 * portfolio-motor-aprovacao).
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String allowedOrigin;

    public CorsConfig(@Value("${app.cors.origin}") String allowedOrigin) {
        this.allowedOrigin = allowedOrigin;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigin)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
