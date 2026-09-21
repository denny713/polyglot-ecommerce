package com.order.api.controller;

import com.order.api.constant.ResponseMsg;
import com.order.api.exception.BadRequestException;
import com.order.api.exception.ForbiddenException;
import com.order.api.exception.NotFoundException;
import com.order.api.handler.ResponseHandler;
import com.order.api.model.dto.request.cart.CartPushReq;
import com.order.api.model.dto.response.Response;
import com.order.api.model.dto.response.cart.CartRes;
import com.order.api.service.CartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tests the HTTP layer of {@code /order/cart} in isolation. */
class CartControllerTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final UUID USER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private CartService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(CartService.class);
        mvc = MockMvcBuilders.standaloneSetup(new CartController(service))
                .setControllerAdvice(new ResponseHandler())
                .build();
    }

    private static CartPushReq validRequest() {
        CartPushReq req = new CartPushReq();
        req.setProductId(7L);
        req.setQuantity(3);

        return req;
    }

    // ------------------------------------------------------------------
    // the happy path
    // ------------------------------------------------------------------

    @Test
    void shouldPassAValidPushToTheService() throws Exception {
        when(service.doPush(any(CartPushReq.class)))
                .thenReturn(new Response(200, ResponseMsg.SUCCESS, new CartRes(USER, 7L, 5)));

        mvc.perform(post("/order/cart").contentType(APPLICATION_JSON)
                        .content(JSON.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.status").value(ResponseMsg.SUCCESS))
                .andExpect(jsonPath("$.data.userId").value(USER.toString()))
                .andExpect(jsonPath("$.data.productId").value(7))
                .andExpect(jsonPath("$.data.quantity").value(5));

        ArgumentCaptor<CartPushReq> req = ArgumentCaptor.forClass(CartPushReq.class);
        verify(service).doPush(req.capture());
        assertEquals(7L, req.getValue().getProductId());
        assertEquals(3, req.getValue().getQuantity());
    }

    // ------------------------------------------------------------------
    // payloads the controller rejects before the service sees them
    // ------------------------------------------------------------------

    @Test
    void shouldRejectAPushWithoutAProduct() throws Exception {
        CartPushReq req = validRequest();
        req.setProductId(null);

        mvc.perform(post("/order/cart").contentType(APPLICATION_JSON)
                        .content(JSON.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.status").value(ResponseMsg.BAD_REQUEST))
                .andExpect(jsonPath("$.data.error").value("Product id cannot be null"));

        verifyNoInteractions(service);
    }

    @Test
    void shouldRejectAPushWithoutAQuantity() throws Exception {
        CartPushReq req = validRequest();
        req.setQuantity(null);

        mvc.perform(post("/order/cart").contentType(APPLICATION_JSON)
                        .content(JSON.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.error").value("Quantity cannot be null"));

        verifyNoInteractions(service);
    }

    @Test
    void shouldRejectAPushOfNothing() throws Exception {
        CartPushReq req = validRequest();
        req.setQuantity(0);

        // Adding zero of something is not a cart line, and a negative would silently
        // take products back out.
        mvc.perform(post("/order/cart").contentType(APPLICATION_JSON)
                        .content(JSON.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.error").value("Quantity must be at least 1"));

        verifyNoInteractions(service);
    }

    // ------------------------------------------------------------------
    // the service's own refusals come back in the same envelope
    // ------------------------------------------------------------------

    @Test
    void shouldAnswerNotFoundWhenTheProductIsUnknown() throws Exception {
        when(service.doPush(any(CartPushReq.class))).thenThrow(new NotFoundException("Product not found"));

        mvc.perform(post("/order/cart").contentType(APPLICATION_JSON)
                        .content(JSON.writeValueAsString(validRequest())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.status").value(ResponseMsg.NOT_FOUND))
                .andExpect(jsonPath("$.data.error").value("Product not found"));
    }

    @Test
    void shouldAnswerForbiddenWhenThereIsNoSignedInUserOnAPush() throws Exception {
        when(service.doPush(any(CartPushReq.class))).thenThrow(new ForbiddenException("No permission"));

        mvc.perform(post("/order/cart").contentType(APPLICATION_JSON)
                        .content(JSON.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.status").value(ResponseMsg.FORBIDDEN));
    }

    // ------------------------------------------------------------------
    // taking a product back out, on the same path
    // ------------------------------------------------------------------

    @Test
    void shouldPassTheProductToRemoveToTheService() throws Exception {
        when(service.doRemove(7L))
                .thenReturn(new Response(200, ResponseMsg.SUCCESS, new CartRes(USER, 7L, 0)));

        mvc.perform(delete("/order/cart").param("productId", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.status").value(ResponseMsg.SUCCESS))
                .andExpect(jsonPath("$.data.productId").value(7))
                .andExpect(jsonPath("$.data.quantity").value(0));

        verify(service).doRemove(7L);
    }

    @Test
    void shouldHandARemovalWithoutAProductToTheService() throws Exception {
        when(service.doRemove(null)).thenThrow(new BadRequestException("Product id cannot be null"));

        // The parameter is not declared required, so the refusal comes back in the
        // same envelope as every other one rather than as a bare container error.
        mvc.perform(delete("/order/cart"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.status").value(ResponseMsg.BAD_REQUEST))
                .andExpect(jsonPath("$.data.error").value("Product id cannot be null"));
    }

    @Test
    void shouldAnswerNotFoundWhenTheCartNeverHeldTheProduct() throws Exception {
        when(service.doRemove(7L)).thenThrow(new NotFoundException("Data Product with id 7 not found in cart"));

        mvc.perform(delete("/order/cart").param("productId", "7"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.data.error").value("Data Product with id 7 not found in cart"));
    }

    @Test
    void shouldAnswerForbiddenWhenThereIsNoSignedInUserOnARemoval() throws Exception {
        when(service.doRemove(7L)).thenThrow(new ForbiddenException("No permission"));

        mvc.perform(delete("/order/cart").param("productId", "7"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(ResponseMsg.FORBIDDEN));
    }
}
