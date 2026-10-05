package com.order.api.controller;

import com.order.api.constant.SecurityType;
import com.order.api.model.dto.request.payment.PaymentReq;
import com.order.api.model.dto.response.Response;
import com.order.api.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP surface for paying sales orders, under {@code /order/payment}. */
@RestController
@RequestMapping("/order/payment")
@AllArgsConstructor
@Tag(name = "Payment", description = "Managing payment process")
@SecurityRequirement(name = SecurityType.SECURITY_SCHEME)
public class PaymentController {

    private final PaymentService paymentService;

    /** Pays towards a pending sales order. */
    @PostMapping("")
    @Operation(
            operationId = "pay",
            summary = "Pay towards a pending sales order",
            description = "An order may be paid in instalments inside its payment window; the one that "
                    + "clears the outstanding makes it paid, and anything sent over the outstanding is "
                    + "refunded. Sending the same reference again returns the payment already recorded.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = Response.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = Response.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = Response.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = Response.class))),
            @ApiResponse(responseCode = "404", description = "Not Found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = Response.class)))
    })
    public ResponseEntity<Response> doPayment(@Valid @RequestBody PaymentReq req) {
        return ResponseEntity.ok(paymentService.doPayment(req));
    }

    /** Cancels a paid sales order, refunding it and returning its stock. */
    @PutMapping("/{id}")
    @Operation(
            operationId = "cancelPayment",
            summary = "Cancel a paid sales order",
            description = "Only a paid order can be cancelled here; its grand total is refunded payment by "
                    + "payment to the account each came from, and its stock goes back to inventory. A "
                    + "pending order is cancelled through checkout instead.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = Response.class))),
            @ApiResponse(responseCode = "400", description = "Bad Request",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = Response.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = Response.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = Response.class))),
            @ApiResponse(responseCode = "404", description = "Not Found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = Response.class)))
    })
    public ResponseEntity<Response> doCancel(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.doCancel(id));
    }
}
