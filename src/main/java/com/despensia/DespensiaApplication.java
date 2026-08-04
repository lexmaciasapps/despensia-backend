package com.despensia;

import org.springframework.ai.autoconfigure.chat.client.ChatClientAutoConfiguration;
import org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = {ChatClientAutoConfiguration.class, OpenAiAutoConfiguration.class})
public class DespensiaApplication {

    public static void main(String[] args) {
        SpringApplication.run(DespensiaApplication.class, args);
    }
}
