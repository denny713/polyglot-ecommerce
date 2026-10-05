package com.order.api.listener;

import com.order.api.configuration.CacheConfig.CartCacheProperties;
import com.order.api.producer.NotificationProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * Hears Redis expire a key and, when it was a cart line, tells the notification
 * service whose line it was and which product it held. Keys removed on purpose — at
 * checkout or by the customer — are deleted rather than expired, so they never get
 * here.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CartExpiryListener implements MessageListener {

    /**
     * Every instance of this service hears every expiry, so the first one to claim it
     * is the only one that publishes. The claim outlives the pub/sub fan-out by far
     * and a cart line is not re-created and expired again inside it.
     */
    static final String CLAIM_PREFIX = "notification:cart-expired:";
    static final Duration CLAIM_TTL = Duration.ofMinutes(5);

    private final RedisTemplate<String, Object> cartRedisTemplate;
    private final CartCacheProperties cartCacheProperties;
    private final NotificationProducer notificationProducer;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String key = new String(message.getBody(), StandardCharsets.UTF_8);
        String prefix = cartCacheProperties.keyFor("");
        if (!key.startsWith(prefix)) {
            return;
        }

        String[] parts = key.substring(prefix.length()).split(":");
        if (parts.length != 2) {
            return;
        }

        UUID userId;
        Long productId;
        try {
            userId = UUID.fromString(parts[0]);
            productId = Long.valueOf(parts[1]);
        } catch (IllegalArgumentException e) {
            log.warn("Ignoring expired key {} that looks like a cart line but is not one", key);
            return;
        }

        if (claim(key)) {
            notificationProducer.doCartExpired(userId, productId);
        }
    }

    private boolean claim(String key) {
        try {
            return !Boolean.FALSE.equals(cartRedisTemplate.opsForValue().setIfAbsent(CLAIM_PREFIX + key, 1, CLAIM_TTL));
        } catch (DataAccessException e) {
            // A second email is better than none.
            log.warn("Unable to claim expired cart line {}, publishing anyway", key, e);
            return true;
        }
    }
}
