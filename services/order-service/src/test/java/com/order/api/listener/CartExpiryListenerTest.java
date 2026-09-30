package com.order.api.listener;

import com.order.api.configuration.CacheConfig.CartCacheProperties;
import com.order.api.producer.NotificationProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Unit tests for noticing a cart line that expired. */
class CartExpiryListenerTest {

    private static final UUID USER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String KEY = "cart:" + USER + ":7";

    private ValueOperations<String, Object> valueOps;
    private NotificationProducer producer;
    private CartExpiryListener listener;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(true);
        producer = mock(NotificationProducer.class);

        listener = new CartExpiryListener(redisTemplate, new CartCacheProperties("cart", Duration.ofDays(7)), producer);
    }

    private void expire(String key) {
        listener.onMessage(new DefaultMessage("__keyevent@0__:expired".getBytes(StandardCharsets.UTF_8),
                key.getBytes(StandardCharsets.UTF_8)), "__keyevent@*__:expired".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void shouldPublishTheUserAndProductOfAnExpiredCartLine() {
        expire(KEY);

        verify(valueOps).setIfAbsent(eq("notification:cart-expired:" + KEY), eq(1), eq(Duration.ofMinutes(5)));
        verify(producer).doCartExpired(USER, 7L);
    }

    @Test
    void shouldLeaveItToTheInstanceThatClaimedItFirst() {
        when(valueOps.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(false);

        expire(KEY);

        verify(producer, never()).doCartExpired(any(), any());
    }

    @Test
    void shouldPublishWhenRedisDoesNotAnswerTheClaim() {
        when(valueOps.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(null);

        expire(KEY);

        verify(producer).doCartExpired(USER, 7L);
    }

    @Test
    void shouldPublishAnywayWhenTheClaimCannotBeMade() {
        when(valueOps.setIfAbsent(anyString(), any(), any(Duration.class)))
                .thenThrow(new QueryTimeoutException("redis is slow"));

        expire(KEY);

        verify(producer).doCartExpired(USER, 7L);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "session:abc",                                   // not a cart key at all
            "notification:cart-expired:cart:x:1",            // this listener's own claim
            "cart:11111111-2222-3333-4444-555555555555",     // the namespace, not a line
            "cart:11111111-2222-3333-4444-555555555555:7:9", // one part too many
            "cart:not-a-uuid:7",
            "cart:11111111-2222-3333-4444-555555555555:abc",
    })
    void shouldIgnoreAKeyThatIsNotACartLine(String key) {
        expire(key);

        verifyNoInteractions(valueOps, producer);
    }
}
