package com.viomanager.ai.config;

import com.viomanager.ai.client.GlmClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * GLM 客户端装配口（接口清单 §7：装配口径同 RocketMQ）。
 * key 未配置 = 零装配零 Bean：@Bean 直接返回 null，系统照常跑；
 * 消费侧一律 ObjectProvider<GlmClient> 注入（getIfAvailable 判空），
 * 强注入会因 NullBean 炸上下文——参考 ApprovalMqProducer 对 RocketMQTemplate 的同款处理。
 */
@Configuration
public class AiClientConfig {

    /**
     * key 空 → 返回 null（零装配）；key 非空 → 建客户端。
     * 客户端持有连接与密钥，做成不可变单例；改配置重建部署即可（AI08/09 管理界面首版不实做，拍板见清单 §11）。
     */
    @Bean
    public GlmClient glmClient(AiProperties properties) {
        if (!properties.isEnabled()) {
            return null;
        }
        return new GlmClient(properties);
    }
}
