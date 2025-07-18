package com.aec.ordsrv.config;

import com.paypal.core.PayPalEnvironment;
import com.paypal.core.PayPalHttpClient;
import lombok.RequiredArgsConstructor;      //  ✅   ahora sí lo resuelve
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class PaypalConfig {

    private final PaypalProperties props;   // inyectado por Spring

    @Bean
    public PayPalHttpClient payPalClient() {
        PayPalEnvironment env = new PayPalEnvironment.Sandbox(
                props.getClientId(),
                props.getClientSecret()
        );
        return new PayPalHttpClient(env);
    }
}

