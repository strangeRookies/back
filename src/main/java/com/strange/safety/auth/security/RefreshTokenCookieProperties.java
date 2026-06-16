package com.strange.safety.auth.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.refresh-token-cookie")
public class RefreshTokenCookieProperties {

    private String name = "REFRESH_TOKEN";
    private boolean secure = false;
    private String sameSite = "Lax";
    private String path = "/api/auth";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isSecure() {
        return secure;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    public String getSameSite() {
        return sameSite;
    }

    public void setSameSite(String sameSite) {
        this.sameSite = sameSite;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
