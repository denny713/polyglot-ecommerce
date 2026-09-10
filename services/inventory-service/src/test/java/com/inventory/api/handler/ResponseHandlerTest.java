package com.inventory.api.handler;

import com.inventory.api.constant.ResponseMsg;
import com.inventory.api.exception.BadRequestException;
import com.inventory.api.exception.ForbiddenException;
import com.inventory.api.exception.NotFoundException;
import com.inventory.api.exception.ServiceException;
import com.inventory.api.model.dto.request.po.POSubmitReq;
import com.inventory.api.model.dto.response.Response;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.mock.web.MockHttpServletRequest;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the exception-to-status mapping.
 * <p>
 * Two properties matter beyond the status codes: the body always has the same
 * shape as a successful response, and a 500 must not leak its real message —
 * that one is replaced with a generic sentence and only logged.
 */
class ResponseHandlerTest {

    private final ResponseHandler handler = new ResponseHandler();

    private static ServletWebRequest request() {
        return new ServletWebRequest(new MockHttpServletRequest("GET", "/api/po/1"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> errorOf(ResponseEntity<Response> response) {
        return (Map<String, Object>) response.getBody().getData();
    }

    // ------------------------------------------------------------------
    // statuses
    // ------------------------------------------------------------------

    @Test
    void shouldMapNotFoundTo404() {
        ResponseEntity<Response> response =
                handler.handlingNotFoundExc(new NotFoundException("Data PurchaseOrder with id 9 not found"), request());

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(404, response.getBody().getCode());
        assertEquals(ResponseMsg.NOT_FOUND, response.getBody().getStatus());
        assertEquals("Data PurchaseOrder with id 9 not found", errorOf(response).get("error"));
    }

    @Test
    void shouldMapBadRequestTo400() {
        ResponseEntity<Response> response =
                handler.handlingBadReqExc(new BadRequestException("Only draft purchase orders can be approved"), request());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(ResponseMsg.BAD_REQUEST, response.getBody().getStatus());
        assertEquals("Only draft purchase orders can be approved", errorOf(response).get("error"));
    }

    @Test
    void shouldMapForbiddenTo403() {
        ResponseEntity<Response> response =
                handler.handlingForbiddenExc(new ForbiddenException("not allowed"), request());

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(ResponseMsg.FORBIDDEN, response.getBody().getStatus());
        assertEquals("not allowed", errorOf(response).get("error"));
    }

    @Test
    void shouldMapAServiceFailureTo500() {
        ResponseEntity<Response> response =
                handler.handlingServiceExc(new ServiceException("relation \"purchase_order\" does not exist"), request());

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(ResponseMsg.INTERNAL_SERVER_ERROR, response.getBody().getStatus());
    }

    @Test
    void shouldHideTheRealCauseOfA500() {
        String leaky = "relation \"purchase_order\" does not exist";

        ResponseEntity<Response> response = handler.handlingServiceExc(new ServiceException(leaky), request());

        assertNotEquals(leaky, errorOf(response).get("error"), "schema detail must not reach the caller");
        assertEquals("There are some internal server error, please contact the administrator",
                errorOf(response).get("error"));
    }

    // ------------------------------------------------------------------
    // validation failures are flattened into one message
    // ------------------------------------------------------------------

    @Test
    void shouldJoinEveryValidationMessage() throws Exception {
        MethodArgumentNotValidException exception = validationFailure(
                Map.of("supplierId", "Supplier id cannot be null"));

        ResponseEntity<Response> response = handler.handlingValidationExc(exception, request());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Supplier id cannot be null", errorOf(response).get("error"));
    }

    @Test
    void shouldFallBackWhenThereIsNoFieldMessage() throws Exception {
        MethodArgumentNotValidException exception = validationFailure(Map.of());

        ResponseEntity<Response> response = handler.handlingValidationExc(exception, request());

        // reduce() on an empty stream would otherwise produce nothing to report.
        assertEquals("Invalid request", errorOf(response).get("error"));
    }

    @Test
    void shouldReportSeveralViolationsAtOnce() throws Exception {
        MethodArgumentNotValidException exception = validationFailure(
                Map.of("supplierId", "Supplier id cannot be null"));
        exception.getBindingResult().rejectValue("details", "NotEmpty", "Details cannot be empty");

        ResponseEntity<Response> response = handler.handlingValidationExc(exception, request());

        String message = (String) errorOf(response).get("error");
        assertTrue(message.contains("Supplier id cannot be null"));
        assertTrue(message.contains("Details cannot be empty"));
        assertTrue(message.contains(", "), "several messages must be comma separated");
    }

    // ------------------------------------------------------------------
    // the envelope
    // ------------------------------------------------------------------

    @Test
    void shouldAlwaysCarryTimestampStatusErrorAndPath() {
        ResponseEntity<Response> response =
                handler.handlingNotFoundExc(new NotFoundException("missing"), request());

        Map<String, Object> error = errorOf(response);

        assertInstanceOf(LocalDateTime.class, error.get("timestamp"));
        assertEquals(404, error.get("status"));
        assertEquals("missing", error.get("error"));
        assertTrue(error.get("path").toString().contains("/api/po/1"), "the path helps locate the call");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** Builds a real {@code MethodArgumentNotValidException} around rejected fields. */
    private static MethodArgumentNotValidException validationFailure(Map<String, String> fieldErrors)
            throws NoSuchMethodException {
        // A real DTO, not a bare Object: rejectValue resolves the property against
        // the target, and a null field name would register a global error that
        // getFieldErrors() never returns.
        BindingResult binding = new BeanPropertyBindingResult(new POSubmitReq(), "req");
        fieldErrors.forEach((field, message) -> binding.rejectValue(field, "invalid", message));

        Method method = ResponseHandlerTest.class.getDeclaredMethod("validationFailure", Map.class);
        return new MethodArgumentNotValidException(new MethodParameter(method, 0), binding);
    }
}
