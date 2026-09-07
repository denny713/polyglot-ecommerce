package com.inventory.api.controller;

import com.inventory.api.constant.ResponseMsg;
import com.inventory.api.exception.BadRequestException;
import com.inventory.api.exception.NotFoundException;
import com.inventory.api.handler.ResponseHandler;
import com.inventory.api.model.dto.request.po.PODetailSubmitReq;
import com.inventory.api.model.dto.request.po.POSearchReq;
import com.inventory.api.model.dto.request.po.POSubmitReq;
import com.inventory.api.model.dto.response.PagingResponse;
import com.inventory.api.model.dto.response.Response;
import com.inventory.api.service.PurchaseOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests the HTTP layer of {@code /po} in isolation.
 * <p>
 * Standalone setup rather than {@code @SpringBootTest}: the controller only
 * routes and delegates, so a mocked service is enough and no database, Keycloak
 * or application context is needed. {@link ResponseHandler} is registered
 * because mapping an exception to a status code is part of what the HTTP layer
 * promises, and would otherwise go untested.
 * <p>
 * {@code TokenFilter} is deliberately absent — it is a filter, so it sits
 * outside this slice and is covered by its own test.
 */
class PurchaseOrderControllerTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private PurchaseOrderService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(PurchaseOrderService.class);
        mvc = MockMvcBuilders.standaloneSetup(new PurchaseOrderController(service))
                .setControllerAdvice(new ResponseHandler())
                .build();
    }

    private static POSubmitReq validRequest() {
        PODetailSubmitReq detail = new PODetailSubmitReq();
        detail.setProductId(7L);
        detail.setQuantity(3);

        POSubmitReq req = new POSubmitReq();
        req.setSupplierId(1L);
        req.setDetails(List.of(detail));

        return req;
    }

    // ------------------------------------------------------------------
    // create and update share one service method
    // ------------------------------------------------------------------

    @Test
    void shouldSubmitACreateWithoutAnId() throws Exception {
        when(service.doSubmit(isNull(), any(POSubmitReq.class)))
                .thenReturn(new Response(201, ResponseMsg.SUCCESS, "created"));

        mvc.perform(post("/po").contentType(APPLICATION_JSON)
                        .content(JSON.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.status").value(ResponseMsg.SUCCESS));

        // A null id is how the service is told this is a new document.
        ArgumentCaptor<Long> id = ArgumentCaptor.forClass(Long.class);
        verify(service).doSubmit(id.capture(), any(POSubmitReq.class));
        assertNull(id.getValue());
    }

    @Test
    void shouldSubmitAnUpdateWithThePathId() throws Exception {
        when(service.doSubmit(eq(9L), any(POSubmitReq.class)))
                .thenReturn(new Response(200, ResponseMsg.SUCCESS, "updated"));

        mvc.perform(put("/po/9").contentType(APPLICATION_JSON)
                        .content(JSON.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("updated"));

        // Mixing a raw value with a matcher is illegal in Mockito, so the id is
        // wrapped in eq(); the body itself is asserted by the captor test below.
        verify(service).doSubmit(eq(9L), any(POSubmitReq.class));
    }

    @Test
    void shouldPassTheBodyThroughUnchanged() throws Exception {
        when(service.doSubmit(isNull(), any(POSubmitReq.class)))
                .thenReturn(new Response(201, ResponseMsg.SUCCESS, null));

        mvc.perform(post("/po").contentType(APPLICATION_JSON)
                        .content(JSON.writeValueAsString(validRequest())))
                .andExpect(status().isOk());

        ArgumentCaptor<POSubmitReq> body = ArgumentCaptor.forClass(POSubmitReq.class);
        verify(service).doSubmit(isNull(), body.capture());

        assertEquals(1L, body.getValue().getSupplierId());
        assertEquals(1, body.getValue().getDetails().size());
        assertEquals(7L, body.getValue().getDetails().get(0).getProductId());
        assertEquals(3, body.getValue().getDetails().get(0).getQuantity());
    }

    // ------------------------------------------------------------------
    // plain id-only endpoints
    // ------------------------------------------------------------------

    @Test
    void shouldReadOneDocument() throws Exception {
        when(service.doDetail(4L)).thenReturn(new Response(200, ResponseMsg.SUCCESS, "detail"));

        mvc.perform(get("/po/4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("detail"));

        verify(service).doDetail(4L);
    }

    @Test
    void shouldActivate() throws Exception {
        when(service.doActivate(4L)).thenReturn(new Response(200, ResponseMsg.SUCCESS, "activated"));

        mvc.perform(put("/po/activate/4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("activated"));

        verify(service).doActivate(4L);
    }

    @Test
    void shouldDeactivate() throws Exception {
        when(service.doDeactivate(4L)).thenReturn(new Response(200, ResponseMsg.SUCCESS, "deactivated"));

        mvc.perform(put("/po/deactivate/4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("deactivated"));

        verify(service).doDeactivate(4L);
    }

    @Test
    void shouldDelete() throws Exception {
        when(service.doDelete(4L)).thenReturn(new Response(200, ResponseMsg.SUCCESS, "deleted"));

        mvc.perform(delete("/po/4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("deleted"));

        verify(service).doDelete(4L);
    }

    @Test
    void shouldCancel() throws Exception {
        when(service.doCancel(4L)).thenReturn(new Response(200, ResponseMsg.SUCCESS, "cancelled"));

        mvc.perform(put("/po/cancel/4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("cancelled"));

        verify(service).doCancel(4L);
    }

    // ------------------------------------------------------------------
    // approve carries a body here, unlike the purchase return
    // ------------------------------------------------------------------

    @Test
    void shouldApproveWithTheReportedQuantities() throws Exception {
        when(service.doApprove(eq(4L), any(POSubmitReq.class)))
                .thenReturn(new Response(200, ResponseMsg.SUCCESS, "approved"));

        mvc.perform(put("/po/approve/4").contentType(APPLICATION_JSON)
                        .content(JSON.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("approved"));

        verify(service).doApprove(eq(4L), any(POSubmitReq.class));
    }

    // ------------------------------------------------------------------
    // search
    // ------------------------------------------------------------------

    @Test
    void shouldSearchAndReturnTheRecordCounts() throws Exception {
        when(service.doSearch(any(POSearchReq.class)))
                .thenReturn(new PagingResponse(200, ResponseMsg.SUCCESS, List.of(), 12L, 12L));

        mvc.perform(post("/po/list").contentType(APPLICATION_JSON)
                        .content("{\"page\":0,\"size\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRecord").value(12))
                .andExpect(jsonPath("$.filterRecord").value(12));

        verify(service).doSearch(any(POSearchReq.class));
    }

    @Test
    void shouldAcceptAnEmptySearchBody() throws Exception {
        when(service.doSearch(any(POSearchReq.class)))
                .thenReturn(new PagingResponse(200, ResponseMsg.SUCCESS, List.of(), 0L, 0L));

        mvc.perform(post("/po/list").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // the HTTP layer's real contract: statuses
    // ------------------------------------------------------------------

    @Test
    void shouldRejectASubmitWithoutASupplier() throws Exception {
        PODetailSubmitReq detail = new PODetailSubmitReq();
        detail.setProductId(7L);
        detail.setQuantity(3);

        POSubmitReq req = new POSubmitReq();
        req.setDetails(List.of(detail));

        mvc.perform(post("/po").contentType(APPLICATION_JSON)
                        .content(JSON.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(ResponseMsg.BAD_REQUEST))
                .andExpect(jsonPath("$.data.error").value("Supplier id cannot be null"));

        // Validation has to stop the request before any work is attempted.
        verifyNoInteractions(service);
    }

    @Test
    void shouldRejectASubmitWithNoDetails() throws Exception {
        POSubmitReq req = new POSubmitReq();
        req.setSupplierId(1L);
        req.setDetails(List.of());

        mvc.perform(post("/po").contentType(APPLICATION_JSON)
                        .content(JSON.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.error").value("Details cannot be empty"));

        verifyNoInteractions(service);
    }

    @Test
    void shouldRejectADetailWithoutAQuantity() throws Exception {
        PODetailSubmitReq detail = new PODetailSubmitReq();
        detail.setProductId(7L);

        POSubmitReq req = new POSubmitReq();
        req.setSupplierId(1L);
        req.setDetails(List.of(detail));

        mvc.perform(post("/po").contentType(APPLICATION_JSON)
                        .content(JSON.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.error").value("Quantity cannot be null"));
    }

    @Test
    void shouldRejectANonPositiveQuantity() throws Exception {
        PODetailSubmitReq detail = new PODetailSubmitReq();
        detail.setProductId(7L);
        detail.setQuantity(0);

        POSubmitReq req = new POSubmitReq();
        req.setSupplierId(1L);
        req.setDetails(List.of(detail));

        mvc.perform(post("/po").contentType(APPLICATION_JSON)
                        .content(JSON.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.error").value("Quantity must be greater than 0"));
    }

    @Test
    void shouldAnswerNotFoundWhenTheServiceCannotFindTheDocument() throws Exception {
        when(service.doDetail(404L)).thenThrow(new NotFoundException("Data PurchaseOrder with id 404 not found"));

        mvc.perform(get("/po/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(ResponseMsg.NOT_FOUND))
                .andExpect(jsonPath("$.data.error").value("Data PurchaseOrder with id 404 not found"));
    }

    @Test
    void shouldAnswerBadRequestWhenTheServiceRefusesTheTransition() throws Exception {
        when(service.doCancel(4L)).thenThrow(new BadRequestException("Only draft purchase orders can be cancelled"));

        mvc.perform(put("/po/cancel/4"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.error").value("Only draft purchase orders can be cancelled"));
    }
}
