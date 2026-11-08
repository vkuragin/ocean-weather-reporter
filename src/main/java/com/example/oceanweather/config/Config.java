package com.example.oceanweather.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({AgentProperties.class, OpenMeteoProperties.class})
public class Config {

  @Bean
  public ChatClient openAiChatClient(ChatClient.Builder builder) {
    return builder.build();
  }

  @Bean
  public RestClient openMeteoRestClient(
      RestClient.Builder builder, OpenMeteoProperties properties) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(properties.connectTimeoutMs());
    requestFactory.setReadTimeout(properties.readTimeoutMs());
    return builder.requestFactory(requestFactory).build();
  }

  @Bean
  public TaskExecutor weatherTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(8);
    executor.setQueueCapacity(100);
    executor.setThreadNamePrefix("weather-task-");
    executor.initialize();
    return executor;
  }
}
