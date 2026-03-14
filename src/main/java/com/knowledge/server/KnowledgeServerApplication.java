package com.knowledge.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.knowledge.server.config.KnowledgeServerProperties;

@SpringBootApplication
@EnableConfigurationProperties(KnowledgeServerProperties.class)
public class KnowledgeServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowledgeServerApplication.class, args);
    }
}
