package com.despensia.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Manually creates Spring AI ChatModel and ChatClient beans for LM Studio (OpenAI-compatible API).
 * Excludes spring-ai-openai-spring-boot-starter auto-config which fails silently with fake keys.
 */
@Configuration
public class SpringAiConfig {

    private static final Logger log = LoggerFactory.getLogger(SpringAiConfig.class);

    @Value("${spring.ai.openai.api-key:sk-fake}")
    String apiKey;

    @Value("${spring.ai.openai.base-url:http://localhost:1234/v1/}")
    String baseUrl;

    /**
     * Create ChatModel for LM Studio since spring-ai auto-config fails silently.
     */
    /**
     * Primary ChatModel for LM Studio — overrides any auto-config bean.
     */
    @Bean("chatModel")
    @Primary
    public ChatModel chatModel() {
        log.info("Creating Spring AI ChatClient for LM Studio at {}", baseUrl);
        
        String safeKey = (apiKey == null || apiKey.isBlank()) ? "sk-fake" : apiKey;

        OpenAiApi openAiApi = new OpenAiApi(baseUrl, safeKey);
        org.springframework.ai.openai.OpenAiChatOptions options = 
                org.springframework.ai.openai.OpenAiChatOptions.builder()
                        .model("qwen/qwen3.6-35b-a3b")
                        .build();
        
        return new OpenAiChatModel(openAiApi, options);
    }

    /**
     * Wrap ChatModel in a ChatClient for use by LmStudioReceiptParser and other consumers.
     */
    @Bean("chatClient")
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
