package com.order.api.service.impl;

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
import com.order.api.service.CartService;
import com.order.api.util.AccountUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Keeps the cart in Redis, one key per product, until it is turned into an order.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final ProductRepository productRepository;
    private final RedisTemplate<String, Object> cartRedisTemplate;
    private final CartCacheProperties cartCacheProperties;

    /**
     * Writes one cart line. The quantity sent replaces whatever the line held, because
     * the caller is the one that works the total out. Every write re-arms the TTL.
     */
    @Override
    public Response doPush(CartPushReq req) {
        UUID userLogin = userLogin();
        Product product = productRepository.doGet(req.getProductId());
        String key = cartCacheProperties.keyFor(userLogin, product.getId());

        try {
            cartRedisTemplate.opsForValue().set(key, req.getQuantity(), cartCacheProperties.ttl());
        } catch (DataAccessException e) {
            log.error("Unable to write cart line {}", key, e);
            throw new ServiceException("Failed to put the product into the cart");
        }

        log.info("Cart line {} set to {}", key, req.getQuantity());
        return new Response(200, ResponseMsg.SUCCESS, new CartRes(userLogin, req.getProductId(), req.getQuantity()));
    }

    /**
     * Takes one product out of the signed-in customer's cart, and says so when the
     * cart was not holding it in the first place.
     */
    @Override
    public Response doRemove(Long productId) {
        UUID userLogin = userLogin();
        if (productId == null) {
            throw new BadRequestException("Product id cannot be null");
        }

        String key = cartCacheProperties.keyFor(userLogin, productId);
        Boolean removed;
        try {
            removed = cartRedisTemplate.delete(key);
        } catch (DataAccessException e) {
            log.error("Unable to remove cart line {}", key, e);
            throw new ServiceException("Failed to remove the product from the cart");
        }

        if (!Boolean.TRUE.equals(removed)) {
            throw new NotFoundException("Data Product with id " + productId + " not found in cart");
        }

        log.info("Cart line {} removed", key);
        return new Response(200, ResponseMsg.SUCCESS, new CartRes(userLogin, productId, 0));
    }

    /** The account behind the access token, which is whose cart is being worked on. */
    private UUID userLogin() {
        UUID userLogin = AccountUtil.getUserLogin();
        if (userLogin == null) {
            throw new ForbiddenException("You don't have permission to access this resource");
        }

        return userLogin;
    }
}
