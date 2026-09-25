package com.order.api.controller;

import com.order.api.constant.ResponseMsg;
import com.order.api.enums.SalesStatus;
import com.order.api.exception.BadRequestException;
import com.order.api.exception.ForbiddenException;
import com.order.api.exception.NotFoundException;
import com.order.api.handler.ResponseHandler;
import com.order.api.model.dto.request.checkout.CheckoutDetailReq;
import com.order.api.model.dto.request.checkout.CheckoutReq;
import com.order.api.model.dto.response.Response;
import com.order.api.model.dto.response.checkout.CheckoutDetailRes;
import com.order.api.model.dto.response.checkout.CheckoutRes;
import com.order.api.service.CheckoutService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tests the HTTP layer of {@code /order/checkout} in isolation. */
class CheckoutControllerTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private CheckoutService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(CheckoutService.class);
        mvc = MockMvcBuilders.standaloneSetup(new CheckoutController(service))
                .setControllerAdvice(new ResponseHandler())
                .build();
    }

    private static CheckoutDetailReq item(Long productId, Integer quantity) {
        CheckoutDetailReq item = new CheckoutDetailReq();
        item.setProductId(productId);
        item.setQuantity(quantity);

        return item;
    }

    private static CheckoutReq request(Boolean fromCart, CheckoutDetailReq... items) {
        CheckoutReq req = new CheckoutReq();
        req.setFromCart(fromCart);
        req.setItems(Arrays.asList(items));

        return req;
    }

    private static CheckoutRes order() {
        return order(SalesStatus.PENDING);
    }

    private static CheckoutRes order(SalesStatus status) {
        return new CheckoutRes(15L, "SO20260923001", new BigDecimal("100000.00"), status, null, null,
                List.of(new CheckoutDetailRes(31L, 7L, "Kopi Gayo 200g", 2,
                        new BigDecimal("50000.00"), new BigDecimal("100000.00"))));
    }

    // ------------------------------------------------------------------
    // the happy path
    // ------------------------------------------------------------------

    @Test
    void shouldPassAValidCartCheckoutToTheService() throws Exception {
        when(service.doCheckout(any(CheckoutReq.class)))
                .thenReturn(new Response(200, ResponseMsg.SUCCESS, order()));

        mvc.perform(post("/order/checkout").contentType(APPLICATION_JSON)
                        .content(JSON.writeValueAsString(request(true, item(7L, 2), item(9L, 1)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.status").value(ResponseMsg.SUCCESS))
                .andExpect(jsonPath("$.data.id").value(15))
                .andExpect(jsonPath("$.data.documentNumber").value("SO20260923001"))
                .andExpect(jsonPath("$.data.grandTotal").value(100000.00))
                .andExpect(jsonPath("$.data.items[0].productName").value("Kopi Gayo 200g"));

        ArgumentCaptor<CheckoutReq> req = ArgumentCaptor.forClass(CheckoutReq.class);
        verify(service).doCheckout(req.capture());
        assertEquals(true, req.getValue().getFromCart());
        assertEquals(2, req.getValue().getItems().size());
    }

    @Test
    void shouldPassABuyNowOfOneProductToTheService() throws Exception {
        when(service.doCheckout(any(CheckoutReq.class)))
                .thenReturn(new Response(200, ResponseMsg.SUCCESS, order()));

        mvc.perform(post("/order/checkout").contentType(APPLICATION_JSON)
                        .content(JSON.writeValueAsString(request(false, item(7L, 2)))))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // payloads the controller rejects before the service sees them
    // ------------------------------------------------------------------

    @Test
    void shouldRejectACheckoutWithoutFromCart() throws Exception {
        mvc.perform(post("/order/checkout").contentType(APPLICATION_JSON)
                        .content(JSON.writeValueAsString(request(null, item(7L, 2)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.status").value(ResponseMsg.BAD_REQUEST))
                .andExpect(jsonPath("$.data.error").value("fromCart cannot be null"));

        verifyNoInteractions(service);
    }

    @Test
    void shouldRejectACheckoutOfNothing() throws Exception {
        mvc.perform(post("/order/checkout").contentType(APPLICATION_JSON)
                        .content(JSON.writeValueAsString(request(true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.error").value("Items cannot be empty"));

        verifyNoInteractions(service);
    }

    @Test
    void shouldRejectABuyNowOfMoreThanOneProduct() throws Exception {
        mvc.perform(post("/order/checkout").contentType(APPLICATION_JSON)
                        .content(JSON.writeValueAsString(request(false, item(7L, 2), item(9L, 1)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.error").value("Buy now can only hold one product"));

        verifyNoInteractions(service);
    }

    @Test
    void shouldRejectALineWithoutAProduct() throws Exception {
        mvc.perform(post("/order/checkout").contentType(APPLICATION_JSON)
                        .content(JSON.writeValueAsString(request(true, item(null, 2)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.error").value("Product id cannot be null"));

        verifyNoInteractions(service);
    }

    @Test
    void shouldRejectALineOfNothing() throws Exception {
        mvc.perform(post("/order/checkout").contentType(APPLICATION_JSON)
                        .content(JSON.writeValueAsString(request(false, item(7L, 0)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.error").value("Quantity must be at least 1"));

        verifyNoInteractions(service);
    }

    // ------------------------------------------------------------------
    // refusals from the service keep the same envelope
    // ------------------------------------------------------------------

    @Test
    void shouldAnswerBadRequestWhenTheCartChanged() throws Exception {
        when(service.doCheckout(any(CheckoutReq.class)))
                .thenThrow(new BadRequestException("Cart has changed for product with id 7, please refresh"));

        mvc.perform(post("/order/checkout").contentType(APPLICATION_JSON)
                        .content(JSON.writeValueAsString(request(true, item(7L, 2)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.error").value("Cart has changed for product with id 7, please refresh"));
    }

    @Test
    void shouldAnswerNotFoundWhenTheCartDoesNotHoldTheProduct() throws Exception {
        when(service.doCheckout(any(CheckoutReq.class)))
                .thenThrow(new NotFoundException("Data Product with id 7 not found in cart"));

        mvc.perform(post("/order/checkout").contentType(APPLICATION_JSON)
                        .content(JSON.writeValueAsString(request(true, item(7L, 2)))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.status").value(ResponseMsg.NOT_FOUND));
    }

    // ------------------------------------------------------------------
    // cancelling an order
    // ------------------------------------------------------------------

    @Test
    void shouldPassTheOrderToCancelToTheService() throws Exception {
        when(service.doCancel(15L))
                .thenReturn(new Response(200, ResponseMsg.SUCCESS, order(SalesStatus.CANCELLED)));

        mvc.perform(put("/order/checkout/15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.status").value(ResponseMsg.SUCCESS))
                .andExpect(jsonPath("$.data.id").value(15))
                .andExpect(jsonPath("$.data.status").value(SalesStatus.CANCELLED.getLabel()));

        verify(service).doCancel(15L);
    }

    @Test
    void shouldAnswerBadRequestWhenTheOrderIsAlreadyCancelled() throws Exception {
        when(service.doCancel(anyLong()))
                .thenThrow(new BadRequestException("Sales order SO20260923001 is already cancelled"));

        mvc.perform(put("/order/checkout/15"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.status").value(ResponseMsg.BAD_REQUEST))
                .andExpect(jsonPath("$.data.error").value("Sales order SO20260923001 is already cancelled"));
    }

    @Test
    void shouldAnswerForbiddenWhenTheOrderBelongsToSomeoneElse() throws Exception {
        when(service.doCancel(anyLong()))
                .thenThrow(new ForbiddenException("You don't have permission to cancel this sales order"));

        mvc.perform(put("/order/checkout/15"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.status").value(ResponseMsg.FORBIDDEN))
                .andExpect(jsonPath("$.data.error").value("You don't have permission to cancel this sales order"));
    }

    @Test
    void shouldAnswerNotFoundWhenTheOrderDoesNotExist() throws Exception {
        when(service.doCancel(anyLong()))
                .thenThrow(new NotFoundException("Data Sales Order with id 99 not found"));

        mvc.perform(put("/order/checkout/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.status").value(ResponseMsg.NOT_FOUND));
    }
}
