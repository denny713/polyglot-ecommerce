package com.inventory.api.controller;

import com.inventory.api.constant.ResponseMsg;
import com.inventory.api.exception.BadRequestException;
import com.inventory.api.exception.NotFoundException;
import com.inventory.api.exception.ServiceException;
import com.inventory.api.handler.ResponseHandler;
import com.inventory.api.model.dto.request.pr.PRDetailSubmitReq;
import com.inventory.api.model.dto.request.pr.PRSearchReq;
import com.inventory.api.model.dto.request.pr.PRSubmitReq;
import com.inventory.api.model.dto.response.PagingResponse;
import com.inventory.api.model.dto.response.Response;
import com.inventory.api.service.PurchaseReturnService;
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
 * Tests the HTTP layer of {@code /pr} in isolation, the same way
 * {@link PurchaseOrderControllerTest} does.
 * <p>
 * The one behavioural difference worth a test of its own is approve: it takes no
 * body here, so a request with none must still succeed.
 */
class PurchaseReturnControllerTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private PurchaseReturnService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(PurchaseReturnService.class);
        mvc = MockMvcBuilders.standaloneSetup(new PurchaseReturnController(service))
                .setControllerAdvice(new ResponseHandler())
                .build();
    }

    private static PRSubmitReq validRequest() {
        PRDetailSubmitReq detail = new PRDetailSubmitReq();
        detail.setProductId(7L);
        detail.setQuantity(2);
        detail.setReason("damaged on arrival");

        PRSubmitReq req = new PRSubmitReq();
        req.setSupplierId(1L);
        req.setReason("wrong shipment");
        req.setDetails(List.of(detail));

        return req;
    }

    // ------------------------------------------------------------------
    // create and update share one service method
    // ------------------------------------------------------------------

    @Test
    void shouldSubmitACreateWithoutAnId() throws Exception {
        when(service.doSubmit(isNull(), any(PRSubmitReq.class)))
                .thenReturn(new Response(201, ResponseMsg.SUCCESS, "created"));

        mvc.perform(post("/pr").contentType(APPLICATION_JSON)
                        .content(JSON.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201));

        ArgumentCaptor<Long> id = ArgumentCaptor.forClass(Long.class);
        verify(service).doSubmit(id.capture(), any(PRSubmitReq.class));
        assertNull(id.getValue());
    }

    @Test
    void shouldSubmitAnUpdateWithThePathId() throws Exception {
        when(service.doSubmit(eq(9L), any(PRSubmitReq.class)))
                .thenReturn(new Response(200, ResponseMsg.SUCCESS, "updated"));

        mvc.perform(put("/pr/9").contentType(APPLICATION_JSON)
                        .content(JSON.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("updated"));

        verify(service).doSubmit(eq(9L), any(PRSubmitReq.class));
    }

    @Test
    void shouldPassTheBodyThroughUnchanged() throws Exception {
        when(service.doSubmit(isNull(), any(PRSubmitReq.class)))
                .thenReturn(new Response(201, ResponseMsg.SUCCESS, null));

        mvc.perform(post("/pr").contentType(APPLICATION_JSON)
                        .content(JSON.writeValueAsString(validRequest())))
                .andExpect(status().isOk());

        ArgumentCaptor<PRSubmitReq> body = ArgumentCaptor.forClass(PRSubmitReq.class);
        verify(service).doSubmit(isNull(), body.capture());

        assertEquals(1L, body.getValue().getSupplierId());
        assertEquals("wrong shipment", body.getValue().getReason());
        // The per-line reason must survive alongside the document-level one.
        assertEquals("damaged on arrival", body.getValue().getDetails().get(0).getReason());
    }

    // ------------------------------------------------------------------
    // plain id-only endpoints
    // ------------------------------------------------------------------

    @Test
    void shouldReadOneDocument() throws Exception {
        when(service.doDetail(4L)).thenReturn(new Response(200, ResponseMsg.SUCCESS, "detail"));

        mvc.perform(get("/pr/4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("detail"));

        verify(service).doDetail(4L);
    }

    @Test
    void shouldActivate() throws Exception {
        when(service.doActivate(4L)).thenReturn(new Response(200, ResponseMsg.SUCCESS, "activated"));

        mvc.perform(put("/pr/activate/4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("activated"));

        verify(service).doActivate(4L);
    }

    @Test
    void shouldDeactivate() throws Exception {
        when(service.doDeactivate(4L)).thenReturn(new Response(200, ResponseMsg.SUCCESS, "deactivated"));

        mvc.perform(put("/pr/deactivate/4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("deactivated"));

        verify(service).doDeactivate(4L);
    }

    @Test
    void shouldDelete() throws Exception {
        when(service.doDelete(4L)).thenReturn(new Response(200, ResponseMsg.SUCCESS, "deleted"));

        mvc.perform(delete("/pr/4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("deleted"));

        verify(service).doDelete(4L);
    }

    @Test
    void shouldCancel() throws Exception {
        when(service.doCancel(4L)).thenReturn(new Response(200, ResponseMsg.SUCCESS, "cancelled"));

        mvc.perform(put("/pr/cancel/4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("cancelled"));

        verify(service).doCancel(4L);
    }

    // ------------------------------------------------------------------
    // approve takes no body, unlike the purchase order
    // ------------------------------------------------------------------

    @Test
    void shouldApproveWithoutABody() throws Exception {
        when(service.doApprove(4L)).thenReturn(new Response(200, ResponseMsg.SUCCESS, "approved"));

        mvc.perform(put("/pr/approve/4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("approved"));

        verify(service).doApprove(4L);
    }

    // ------------------------------------------------------------------
    // search
    // ------------------------------------------------------------------

    @Test
    void shouldSearchAndReturnTheRecordCounts() throws Exception {
        when(service.doSearch(any(PRSearchReq.class)))
                .thenReturn(new PagingResponse(200, ResponseMsg.SUCCESS, List.of(), 5L, 5L));

        mvc.perform(post("/pr/list").contentType(APPLICATION_JSON)
                        .content("{\"documentNumber\":\"PR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRecord").value(5));

        ArgumentCaptor<PRSearchReq> req = ArgumentCaptor.forClass(PRSearchReq.class);
        verify(service).doSearch(req.capture());
        assertEquals("PR", req.getValue().getDocumentNumber());
    }

    // ------------------------------------------------------------------
    // statuses
    // ------------------------------------------------------------------

    @Test
    void shouldRejectASubmitWithoutASupplier() throws Exception {
        PRDetailSubmitReq detail = new PRDetailSubmitReq();
        detail.setProductId(7L);
        detail.setQuantity(2);

        PRSubmitReq req = new PRSubmitReq();
        req.setDetails(List.of(detail));

        mvc.perform(post("/pr").contentType(APPLICATION_JSON)
                        .content(JSON.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.error").value("Supplier id cannot be null"));

        verifyNoInteractions(service);
    }

    @Test
    void shouldRejectASubmitWithNoDetails() throws Exception {
        PRSubmitReq req = new PRSubmitReq();
        req.setSupplierId(1L);
        req.setDetails(List.of());

        mvc.perform(post("/pr").contentType(APPLICATION_JSON)
                        .content(JSON.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.error").value("Details cannot be empty"));

        verifyNoInteractions(service);
    }

    @Test
    void shouldAnswerNotFoundWhenTheServiceCannotFindTheDocument() throws Exception {
        when(service.doDetail(404L)).thenThrow(new NotFoundException("Data PurchaseReturn with id 404 not found"));

        mvc.perform(get("/pr/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.data.error").value("Data PurchaseReturn with id 404 not found"));
    }

    @Test
    void shouldAnswerBadRequestWhenTheServiceRefusesTheTransition() throws Exception {
        when(service.doApprove(4L)).thenThrow(new BadRequestException("Only draft purchase return can be approved"));

        mvc.perform(put("/pr/approve/4"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.error").value("Only draft purchase return can be approved"));
    }

    @Test
    void shouldHideTheDetailOfAnInternalFailure() throws Exception {
        when(service.doSearch(any(PRSearchReq.class)))
                .thenThrow(new ServiceException("could not extract ResultSet, table PR_X does not exist"));

        mvc.perform(post("/pr/list").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(ResponseMsg.INTERNAL_SERVER_ERROR))
                // The database detail must not reach the caller.
                .andExpect(jsonPath("$.data.error")
                        .value("There are some internal server error, please contact the administrator"));
    }
}
