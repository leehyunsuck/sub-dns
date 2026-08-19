package top.nulldns.subdns.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import top.nulldns.subdns.config.finalconfig.PubSubChannel;
import top.nulldns.subdns.service.infra.EventSubscriberService;

@Configuration
public class RedisPubSubConfig {
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory redisConnectionFactory,
            MessageListenerAdapter listenerAdapter
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.addMessageListener(listenerAdapter, new ChannelTopic(PubSubChannel.REFRESH.name()));
        return container;
    }

    @Bean
    public MessageListenerAdapter listenerAdapter(EventSubscriberService subscriber) {
        return new MessageListenerAdapter(subscriber, "onMessage");
    }


}
