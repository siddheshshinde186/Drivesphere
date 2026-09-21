package com.drivesphere.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        // Resolve path to frontend folder
        String userDir = System.getProperty("user.dir");
        File frontendDir = new File(userDir, "frontend");
        if (!frontendDir.exists()) {
            frontendDir = new File(userDir, "../frontend");
        }
        String frontendUri = "file:///" + frontendDir.getAbsolutePath().replace("\\", "/") + "/";

        registry.addResourceHandler("/**")
                .addResourceLocations(
                        frontendUri,
                        "file:../frontend/",
                        "file:./frontend/",
                        "classpath:/static/",
                        "classpath:/public/"
                );
    }

    @Override
    public void addViewControllers(@NonNull ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
    }
}
