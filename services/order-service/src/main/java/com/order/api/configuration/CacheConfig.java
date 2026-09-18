package com.order.api.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

import java.time.Duration;

/** Where a customer's cart lives before it becomes an order. */
@Configuration
public class CacheConfig {

    private final String keyPrefix;
    private final Duration ttl;

    public CacheConfig(
            @Value("${redis.cart.key-prefix}") String keyPrefix,
            @Value("${redis.cart.ttl}") Duration ttl) {
        this.keyPrefix = keyPrefix;
        this.ttl = ttl;
    }

    /**
     * The key layout and lifetime of a cart, in one place, so the code that reads a
     * cart cannot disagree with the code that wrote it about where it is.
     */
    @Bean
    public CartCacheProperties cartCacheProperties() {
        return new CartCacheProperties(keyPrefix, ttl);
    }

    /** The template the cart is read and written with. */
    @Bean
    public RedisTemplate<String, Object> cartRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisSerializer<String> keySerializer = StringRedisSerializer.UTF_8;
        RedisSerializer<Object> valueSerializer = GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(cartTypeValidator())
                .build();

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        // Nothing may fall back to the JDK serializer: a serializer left unset is a
        // payload no other tool can read.
        template.setEnableDefaultSerializer(false);

        return template;
    }

    /**
     * Allows a stored {@code @class} to name a cart class of this service, or the
     * collection, number and date types a cart line is built from. Anything else — the
     * class names a deserialization attack relies on — is rejected before it is
     * instantiated.
     */
    private PolymorphicTypeValidator cartTypeValidator() {
        return BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .allowIfSubType("com.order.api.model.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.lang.")
                .allowIfSubType("java.math.")
                .allowIfSubType("java.time.")
                .build();
    }

    /**
     * The cart's address in Redis and how long it survives without being touched.
     *
     * @param keyPrefix namespace the cart keys share, so a {@code KEYS cart:*} finds
     *                  them all and nothing else collides with them
     * @param ttl       how long an untouched cart is kept; refreshed on every write,
     *                  so it measures inactivity rather than age
     */
    public record CartCacheProperties(String keyPrefix, Duration ttl) {

        /** The single key holding everything {@code customerId} has put in their cart. */
        public String keyFor(Object customerId) {
            return keyPrefix + ":" + customerId;
        }
    }
}
