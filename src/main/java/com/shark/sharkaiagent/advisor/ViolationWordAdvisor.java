package com.shark.sharkaiagent.advisor;

import org.ahocorasick.trie.Trie;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 违禁词校验Advisor
 * 在AI对话请求处理前进行敏感词检查
 * 使用Aho-Corasick算法实现高性能多关键词匹配
 *
 */
public class ViolationWordAdvisor implements BaseAdvisor {

    private final ViolationWordChecker violationWordChecker;

    // 使用ThreadLocal存储当前请求的违禁词检查结果，避免并发问题
    private static final ThreadLocal<AtomicBoolean> violationDetected = ThreadLocal.withInitial(() -> new AtomicBoolean(false));
    private static final ThreadLocal<String> detectedViolationWord = new ThreadLocal<>();

    /**
     * 构造函数，初始化违禁词检查器
     * @param violationWords 违禁词列表
     */
    public ViolationWordAdvisor(List<String> violationWords) {
        this.violationWordChecker = new ViolationWordChecker(violationWords);
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        // 重置检查状态
        violationDetected.get().set(false);
        detectedViolationWord.remove();

        // 提取用户输入消息
        String userInput = chatClientRequest.prompt().getUserMessage().getText();

        if (StringUtils.hasText(userInput)) {
            // 进行违禁词校验
            String violationWord = violationWordChecker.check(userInput);
            if (violationWord != null) {
                // 发现违禁词，标记状态并保存违禁词
                violationDetected.get().set(true);
                detectedViolationWord.set(violationWord);
            }
        }
        return chatClientRequest;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        // 检查是否检测到违禁词
        if (violationDetected.get().get()) {
            // 创建包含固定消息的AssistantMessage
            AssistantMessage assistantMessage = new AssistantMessage(
                    "抱歉，当前用户信息中包含敏感内容，请检查后重新提问"
            );

            // 创建Generation对象
            Generation generation = new Generation(assistantMessage);

            // 创建ChatResponse
            ChatResponse chatResponse = new ChatResponse(List.of(generation),chatClientResponse.chatResponse().getMetadata());

            // 清理ThreadLocal
            violationDetected.remove();
            detectedViolationWord.remove();

            // 创建新的context，包含原有的context和添加conversationId
            Map<String, Object> context = chatClientResponse.context();

            // 返回修改后的ChatClientResponse
            return ChatClientResponse.builder().chatResponse(chatResponse).context(context).build();
        }

        // 清理ThreadLocal
        violationDetected.remove();
        detectedViolationWord.remove();

        return chatClientResponse;
    }

    @Override
    public int getOrder() {
        return 0;
    }
}

/**
 * 违禁词检查器
 * 基于Aho-Corasick自动机实现高性能敏感词匹配
 */
class ViolationWordChecker {
    private final Trie trie;

    public ViolationWordChecker(List<String> violationWords) {
        // 构建AC自动机，忽略大小写
        this.trie = Trie.builder()
                .addKeywords(violationWords)
                .ignoreCase()
                .build();
    }

    /**
     * 检查文本是否包含违禁词
     * @param text 待检查文本
     * @return 返回匹配到的违禁词，无违禁词则返回null
     */
    public String check(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }

        // 执行匹配检查
        return trie.parseText(text).stream()
                .findFirst()
                .map(match -> match.getKeyword())
                .orElse(null);
    }
}