package com.order.api.configuration;

import com.order.api.listener.CartExpiryListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.util.StringUtils;

import java.util.Properties;

/**
 * Subscribes to Redis key expiry, which is how an abandoned cart line is noticed: a
 * TTL runs out inside Redis and nothing in this service is told otherwise.
 */
@Slf4j
@Configuration
public class CartExpiryConfig {

    static final String EXPIRED_TOPIC = "__keyevent@*__:expired";
    static final String CONFIG_PARAMETER = "notify-keyspace-events";

    private final String keyspaceEvents;

    /**
     * @param keyspaceEvents the {@code notify-keyspace-events} value to set when Redis
     *                       has none; empty leaves the server configuration alone
     */
    public CartExpiryConfig(@Value("${redis.cart.keyspace-events}") String keyspaceEvents) {
        this.keyspaceEvents = keyspaceEvents;
    }

    @Bean
    public RedisMessageListenerContainer cartExpiryListenerContainer(RedisConnectionFactory connectionFactory,
                                                                     CartExpiryListener listener) {
        enableKeyspaceEvents(connectionFactory);

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listener, new PatternTopic(EXPIRED_TOPIC));
        return container;
    }

    /**
     * Redis publishes no expiry events unless told to. A server that already has a
     * setting keeps it; one this service cannot reach or configure (a managed Redis
     * that forbids CONFIG, say) does not stop it from starting, it only means no cart
     * expiry emails until the setting is made there.
     */
    private void enableKeyspaceEvents(RedisConnectionFactory connectionFactory) {
        if (!StringUtils.hasText(keyspaceEvents)) {
            return;
        }

        try (RedisConnection connection = connectionFactory.getConnection()) {
            Properties config = connection.serverCommands().getConfig(CONFIG_PARAMETER);
            String current = (config == null) ? null : config.getProperty(CONFIG_PARAMETER);
            if (!StringUtils.hasText(current)) {
                connection.serverCommands().setConfig(CONFIG_PARAMETER, keyspaceEvents);
                log.info("Redis {} set to {}", CONFIG_PARAMETER, keyspaceEvents);
            } else if (!current.contains("x") && !current.contains("A")) {
                log.warn("Redis {} is {} and publishes no expiry events, cart expiry emails will not go out",
                        CONFIG_PARAMETER, current);
            }
        } catch (DataAccessException e) {
            log.warn("Unable to enable Redis {}, cart expiry emails need it set on the server", CONFIG_PARAMETER, e);
        }
    }
}
