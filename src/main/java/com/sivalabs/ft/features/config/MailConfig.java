package com.sivalabs.ft.features.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
class MailConfig {
  @Bean
  MailInterceptor mailInterceptor() {
    return new MailInterceptor();
  }
}
