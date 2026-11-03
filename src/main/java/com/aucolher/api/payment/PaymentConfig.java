package com.aucolher.api.payment;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Liga as propriedades do Mercado Pago, os @Scheduled e os @Async (e-mail). */
@Configuration
@EnableScheduling
@EnableAsync
@EnableConfigurationProperties(MercadoPagoProperties.class)
public class PaymentConfig {
}
