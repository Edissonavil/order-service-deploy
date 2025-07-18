// src/main/java/com/aec/ordsrv/config/PaypalProperties.java
package com.aec.ordsrv.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Getter;
import lombok.Setter;

@Configuration
@ConfigurationProperties(prefix = "paypal")
@Getter @Setter
public class PaypalProperties {
    private String clientId;
    private String clientSecret;
}
