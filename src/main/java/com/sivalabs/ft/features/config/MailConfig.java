package com.sivalabs.ft.features.config;

import org.springframework.context.annotation.Configuration;


@Configuration
class MailConfig {
  @Be
  MailInterceptor mailInterceptor() {
    return new MailInterceptor();
  }
}
