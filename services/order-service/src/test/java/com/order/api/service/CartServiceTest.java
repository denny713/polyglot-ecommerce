package com.order.api.service;

import com.order.api.configuration.CacheConfig.CartCacheProperties;
import com.order.api.constant.ResponseMsg;
import com.order.api.exception.BadRequestException;
import com.order.api.exception.ForbiddenException;
import com.order.api.exception.NotFoundException;
import com.order.api.exception.ServiceException;
import com.order.api.model.dto.request.cart.CartPushReq;
import com.order.api.model.dto.response.Response;
import com.order.api.model.dto.response.cart.CartRes;
import com.order.api.model.entity.Product;
import com.order.api.repository.ProductRepository;
import com.order.api.service.impl.CartServiceImpl;
import com.order.api.util.AccountUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Unit tests for what a customer can do with their cart. */
class CartServiceTest {

    private static final UUID USER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final Long PRODUCT_ID = 7L;
    private static final Duration TTL = Duration.ofDays(7);
    private static final String KEY = "cart:" + USER + ":" + PRODUCT_ID;

    private ProductRepository productRepository;
    private RedisTemplate<String, Object> cartRedisTemplate;
    private ValueOperations<String, Object> valueOps;

    private CartServiceImpl service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        cartRedisTemplate = mock(RedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(cartRedisTemplate.opsForValue()).thenReturn(valueOps);

        service = new CartServiceImpl(productRepository, cartRedisTemplate, new CartCacheProperties("cart", TTL));

        AccountUtil.setUserLogin(USER);
    }

    @AfterEach
    void tearDown() {
        AccountUtil.clearUserLogin();
    }

    // ------------------------------------------------------------------
    // fixtures
    // ------------------------------------------------------------------

    private static Product product(Long id) {
        Product product = new Product();
        product.setId(id);
        product.setName("Kopi Gayo 200g");

        return product;
    }

    private static CartPushReq request(Long productId, Integer quantity) {
        CartPushReq req = new CartPushReq();
        req.setProductId(productId);
        req.setQuantity(quantity);

        return req;
    }

    // ------------------------------------------------------------------
    // push: the happy path
    // ------------------------------------------------------------------

    @Test
    void shouldPushTheProductAndAnswerWithTheLine() {
        when(productRepository.doGet(PRODUCT_ID)).thenReturn(product(PRODUCT_ID));

        Response response = service.doPush(request(PRODUCT_ID, 3));

        assertEquals(200, response.getCode());
        assertEquals(ResponseMsg.SUCCESS, response.getStatus());

        CartRes cart = assertInstanceOf(CartRes.class, response.getData());
        assertEquals(USER, cart.getUserId());
        assertEquals(PRODUCT_ID, cart.getProductId());
        assertEquals(3, cart.getQuantity());
    }

    @Test
    void shouldSetTheLineRatherThanAddToIt() {
        when(productRepository.doGet(PRODUCT_ID)).thenReturn(product(PRODUCT_ID));

        service.doPush(request(PRODUCT_ID, 3));

        // The quantity the caller worked out is the quantity stored, addressed by
        // user and product, and the TTL is re-armed on every write.
        verify(valueOps).set(KEY, 3, TTL);
    }

    @Test
    void shouldPushWhateverProductTheRequestNames() {
        when(productRepository.doGet(anyLong())).thenReturn(product(99L));

        service.doPush(request(99L, 1));

        verify(valueOps).set("cart:" + USER + ":99", 1, TTL);
    }

    // ------------------------------------------------------------------
    // push: what the cart refuses
    // ------------------------------------------------------------------

    @Test
    void shouldRefuseAPushWithNoSignedInUser() {
        AccountUtil.clearUserLogin();

        assertThrows(ForbiddenException.class, () -> service.doPush(request(PRODUCT_ID, 3)));

        // Nothing is looked up or written for a caller the cart cannot name.
        verifyNoInteractions(productRepository, valueOps);
    }

    @Test
    void shouldNotWriteACartLineForAProductThatDoesNotExist() {
        when(productRepository.doGet(PRODUCT_ID)).thenThrow(new NotFoundException("Product not found"));

        assertThrows(NotFoundException.class, () -> service.doPush(request(PRODUCT_ID, 3)));

        verifyNoInteractions(valueOps);
    }

    @Test
    void shouldReportAFailedWriteAsAServiceError() {
        when(productRepository.doGet(PRODUCT_ID)).thenReturn(product(PRODUCT_ID));
        doThrow(new RedisConnectionFailureException("redis is down"))
                .when(valueOps).set(anyString(), any(), any(Duration.class));

        ServiceException exc = assertThrows(ServiceException.class, () -> service.doPush(request(PRODUCT_ID, 3)));

        assertEquals("Failed to put the product into the cart", exc.getMessage());
    }

    // ------------------------------------------------------------------
    // remove
    // ------------------------------------------------------------------

    @Test
    void shouldRemoveTheLineAndAnswerWithAnEmptyOne() {
        when(cartRedisTemplate.delete(KEY)).thenReturn(true);

        Response response = service.doRemove(PRODUCT_ID);

        assertEquals(200, response.getCode());
        assertEquals(ResponseMsg.SUCCESS, response.getStatus());

        CartRes cart = assertInstanceOf(CartRes.class, response.getData());
        assertEquals(USER, cart.getUserId());
        assertEquals(PRODUCT_ID, cart.getProductId());
        // Nothing of that product is left in the cart.
        assertEquals(0, cart.getQuantity());
    }

    @Test
    void shouldRemoveOnlyTheSignedInCustomersLine() {
        when(cartRedisTemplate.delete(KEY)).thenReturn(true);

        service.doRemove(PRODUCT_ID);

        // Another customer's line for the same product lives under its own key.
        verify(cartRedisTemplate).delete(KEY);
    }

    @Test
    void shouldReportALineTheCartNeverHeld() {
        when(cartRedisTemplate.delete(KEY)).thenReturn(false);

        NotFoundException exc = assertThrows(NotFoundException.class, () -> service.doRemove(PRODUCT_ID));

        assertEquals("Data Product with id 7 not found in cart", exc.getMessage());
    }

    @Test
    void shouldTreatNoAnswerFromRedisAsNothingRemoved() {
        when(cartRedisTemplate.delete(KEY)).thenReturn(null);

        // A null reply says nothing was deleted, so it must not read as a success.
        assertThrows(NotFoundException.class, () -> service.doRemove(PRODUCT_ID));
    }

    @Test
    void shouldRefuseARemovalWithoutAProduct() {
        BadRequestException exc = assertThrows(BadRequestException.class, () -> service.doRemove(null));

        assertEquals("Product id cannot be null", exc.getMessage());
        verify(cartRedisTemplate, never()).delete(anyString());
    }

    @Test
    void shouldRefuseARemovalWithNoSignedInUser() {
        AccountUtil.clearUserLogin();

        assertThrows(ForbiddenException.class, () -> service.doRemove(PRODUCT_ID));

        verify(cartRedisTemplate, never()).delete(anyString());
    }

    @Test
    void shouldReportAFailedRemovalAsAServiceError() {
        when(cartRedisTemplate.delete(KEY)).thenThrow(new RedisConnectionFailureException("redis is down"));

        ServiceException exc = assertThrows(ServiceException.class, () -> service.doRemove(PRODUCT_ID));

        assertEquals("Failed to remove the product from the cart", exc.getMessage());
    }
}
