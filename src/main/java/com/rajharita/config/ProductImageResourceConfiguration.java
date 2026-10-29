package com.rajharita.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class ProductImageResourceConfiguration implements WebMvcConfigurer {

    private final String productImageLocation;

    public ProductImageResourceConfiguration(@Value("${app.upload.dir:uploads}") String uploadDirectory) {
        String location = Path.of(uploadDirectory).toAbsolutePath().normalize()
                .resolve("products").toUri().toString();
        this.productImageLocation = location.endsWith("/") ? location : location + "/";
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/products/**")
                .addResourceLocations(productImageLocation);
    }
}
