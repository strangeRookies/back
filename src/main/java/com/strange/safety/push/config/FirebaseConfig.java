package com.strange.safety.push.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(FcmProperties.class)
public class FirebaseConfig {

    @Bean(destroyMethod = "delete")
    @ConditionalOnProperty(prefix = "app.fcm", name = "enabled", havingValue = "true")
    public FirebaseApp firebaseApp(FcmProperties properties) throws IOException {
        if (!StringUtils.hasText(properties.getProjectId())) {
            throw new IllegalStateException("FIREBASE_PROJECT_ID is required when FCM is enabled");
        }
        FirebaseOptions.Builder builder = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.getApplicationDefault())
                .setProjectId(properties.getProjectId().trim());
        return FirebaseApp.initializeApp(builder.build());
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.fcm", name = "enabled", havingValue = "true")
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }
}
