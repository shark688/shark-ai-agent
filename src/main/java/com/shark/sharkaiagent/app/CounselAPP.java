package com.shark.sharkaiagent.app;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CounselAPP {

    private final ChatClient chatClient;


    public static final String SYSTEM_PROMPT = "你是一个专业的心理咨询师，名为“心伴AI”，专长于提供情感支持和心理指导。你不是医生或治疗师，不能诊断或开药；如果用户有严重问题（如自杀意念），立即建议寻求专业帮助，如拨打心理热线（例如中国大陆：12320；日本：0570-064-556）。\n" +
            "在对话中：\n" +
            "\n" +
            "始终以共情、温暖、非判断性的语气回应，模拟真实咨询场景。\n" +
            "先倾听用户描述，复述关键点以示理解（如“我听到你感到很焦虑，因为……”）。\n" +
            "多问引导性开放问题（如“能告诉我更多关于这个情况吗？”“这让你感觉如何？”），逐步深入了解用户背景、情绪触发和影响。\n" +
            "基于用户分享，提供实用建议，如认知行为技巧（挑战负面想法）、放松练习（深呼吸指导）或习惯调整，帮助解决心理问题。\n" +
            "保持对话自然流畅，鼓励用户表达更多细节，确保建议个性化且全面。\n" +
            "如果对话卡住，主动引导下一个话题，但尊重用户隐私。\n" +
            "回复简洁、有针对性，避免长篇大论；以积极结尾，邀请继续分享。";

    public CounselAPP(ChatModel dashscopeChatModel)
    {
        ChatMemoryRepository repository = new InMemoryChatMemoryRepository();
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(10)
                .build();
        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
    }

    /**
     * AI 对话
     * @param message
     * @param chatId
     * @return
     */
    public String doChat(String message, String chatId)
    {
        ChatResponse chatResponse = chatClient.prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .chatResponse();
        String result = chatResponse.getResult().getOutput().getText();
        log.info("content: {}",result);
        return result;
    }

}
