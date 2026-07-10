package com.strange.safety.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // 프로젝트의 모든 API 엔드포인트에 대해
                .allowedOrigins("http://localhost:5173",
                        "http://safety-web-dashboard-bucket.s3-website.ap-northeast-2.amazonaws.com") // 로컬과 S3 프론트엔드 주소
                                                                                                      // 허용
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // 허용할 요청 방식
                .allowedHeaders("*")
                .allowCredentials(true); // 인증 정보(쿠키 등) 포함 허용
    }
}