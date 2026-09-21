package com.order.api.configuration;

import com.order.api.configuration.CacheConfig.CartCacheProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** Tests where a cart lives in Redis and how it is written. */
class CacheConfigTest {

    private static final String PREFIX = "cart";
    private static final Duration TTL = Duration.ofDays(7);
    private static final UUID USER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private final CacheConfig config = new CacheConfig(PREFIX, TTL);

    @Test
    void shouldCarryTheConfiguredPrefixAndTtl() {
        CartCacheProperties properties = config.cartCacheProperties();

        assertEquals(PREFIX, properties.keyPrefix());
        assertEquals(TTL, properties.ttl());
    }

    @Test
    void shouldNamespaceACustomersCart() {
        assertEquals("cart:" + USER, config.cartCacheProperties().keyFor(USER));
    }

    @Test
    void shouldAddressOneLineByUserAndProduct() {
        // The whole cart is then reachable with cart:<user>:*, one key per product.
        assertEquals("cart:" + USER + ":7", config.cartCacheProperties().keyFor(USER, 7L));
    }

    @Test
    void shouldBuildACartTemplateOnTheGivenConnection() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);

        RedisTemplate<String, Object> template = config.cartRedisTemplate(connectionFactory);

        assertSame(connectionFactory, template.getConnectionFactory());
        assertInstanceOf(StringRedisSerializer.class, template.getKeySerializer());
        assertInstanceOf(StringRedisSerializer.class, template.getHashKeySerializer());
        assertNotNull(template.getValueSerializer());
        assertNotNull(template.getHashValueSerializer());
        // Nothing may quietly fall back to the JDK serializer.
        assertFalse(template.isEnableDefaultSerializer());
    }

    @Test
    void shouldWriteCartValuesAsJson() {
        RedisSerializer<?> serializer = config.cartRedisTemplate(mock(RedisConnectionFactory.class))
                .getValueSerializer();

        @SuppressWarnings("unchecked")
        byte[] written = ((RedisSerializer<Object>) serializer).serialize(Map.of("quantity", 3));

        assertNotNull(written);
        assertTrue(new String(written).contains("quantity"), "a cart must be readable by any other tool");
    }
}
