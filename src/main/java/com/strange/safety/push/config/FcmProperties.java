package com.strange.safety.push.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.fcm")
public class FcmProperties {

    private boolean enabled;
    private String projectId;
    private String channelId = "safety-alerts";
    private long ttlMs = 300_000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public long getTtlMs() {
        return ttlMs;
    }

    public void setTtlMs(long ttlMs) {
        this.ttlMs = ttlMs;
    }
}
