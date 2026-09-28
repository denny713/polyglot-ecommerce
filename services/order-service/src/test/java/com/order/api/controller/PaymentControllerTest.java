package com.order.api.controller;

import com.order.api.constant.ResponseMsg;
import com.order.api.enums.PaymentMethod;
import com.order.api.enums.SalesStatus;
import com.order.api.exception.BadRequestException;
import com.order.api.handler.ResponseHandler;
import com.order.api.model.dto.request.payment.PaymentReq;
import com.order.api.model.dto.response.Response;
import com.order.api.model.dto.response.payment.PaymentRes;
import com.order.api.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tests the HTTP layer of {@code /order/payment} in isolation. */
class PaymentControllerTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private PaymentService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(PaymentService.class);
        mvc = MockMvcBuilders.standaloneSetup(new PaymentController(service))
                .setControllerAdvice(new ResponseHandler())
                .build();
    }

    private static PaymentReq request(String amount) {
        PaymentReq req = new PaymentReq();
        req.setSalesOrderId(15L);
        req.setReference("TRX-001");
        req.setMethod(PaymentMethod.TRF);
        req.setBankName("BCA");
        req.setAccountNumber("1234567890");
        req.setAccountName("Budi");
        req.setAmount(amount == null ? null : new BigDecimal(amount));

        return req;
    }

    @Test
    void shouldPassAValidPaymentToTheService() throws Exception {
        PaymentRes res = new PaymentRes();
        res.setId(41L);
        res.setSalesOrderStatus(SalesStatus.PAID);
        when(service.doPayment(any(PaymentReq.class))).thenReturn(new Response(200, ResponseMsg.SUCCESS, res));

        mvc.perform(post("/order/payment").contentType(APPLICATION_JSON)
                        .content(JSON.writeValueAsString(request("100000.00"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(41));

        ArgumentCaptor<PaymentReq> sent = ArgumentCaptor.forClass(PaymentReq.class);
        verify(service).doPayment(sent.capture());
        assertEquals(15L, sent.getValue().getSalesOrderId());
        assertEquals(new BigDecimal("100000.00"), sent.getValue().getAmount());
    }

    @Test
    void shouldRejectAnAmountThatIsNotPositive() throws Exception {
        mvc.perform(post("/order/payment").contentType(APPLICATION_JSON)
                        .content(JSON.writeValueAsString(request("0"))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void shouldRejectAnAmountWithMoreThanTwoDecimals() throws Exception {
        mvc.perform(post("/order/payment").contentType(APPLICATION_JSON)
                        .content(JSON.writeValueAsString(request("100.001"))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void shouldRejectAPaymentWithoutAReference() throws Exception {
        PaymentReq req = request("100000.00");
        req.setReference(" ");

        mvc.perform(post("/order/payment").contentType(APPLICATION_JSON).content(JSON.writeValueAsString(req)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void shouldAnswerBadRequestWhenTheOrderIsAlreadyPaid() throws Exception {
        when(service.doPayment(any(PaymentReq.class)))
                .thenThrow(new BadRequestException("Only a pending order can be paid"));

        mvc.perform(post("/order/payment").contentType(APPLICATION_JSON)
                        .content(JSON.writeValueAsString(request("100000.00"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(ResponseMsg.BAD_REQUEST))
                .andExpect(jsonPath("$.data.error").value("Only a pending order can be paid"));
    }
}
