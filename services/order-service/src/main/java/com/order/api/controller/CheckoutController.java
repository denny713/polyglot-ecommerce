package com.order.api.controller;

import com.order.api.constant.SecurityType;
import com.order.api.model.dto.request.checkout.CheckoutReq;
import com.order.api.model.dto.response.Response;
import com.order.api.service.CheckoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

/** HTTP surface for checkout, under {@code /order/checkout}. */
@RestController
@RequestMapping("/order/checkout")
@AllArgsConstructor
@Tag(name = "Checkout", description = "Managing checkout process")
@SecurityRequirement(name = SecurityType.SECURITY_SCHEME)
public class CheckoutController {

    private final CheckoutService checkoutService;

    /** Turns the picked products into a pending sales order. */
    @PostMapping("")
    @Operation(
            operationId = "checkout",
            summary = "Turn products into a pending sales order",
            description = "From the cart, send the lines picked with the quantity the cart holds; a cart "
                    + "changed since is refused and only the lines checked out leave the cart. Buying "
                    + "straight away takes exactly one product and leaves the cart untouched.")
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
    public ResponseEntity<Response> doCheckout(@Valid @RequestBody CheckoutReq req) {
        return ResponseEntity.ok(checkoutService.doCheckout(req));
    }

    /** Cancels a sales order, releasing the stock it was holding. */
    @PutMapping("/{id}")
    @Operation(
            operationId = "cancelCheckout",
            summary = "Cancel a sales order",
            description = "Cancel a sales order so the stock it was holding is free for other orders "
                    + "again. An order that is already cancelled is refused.")
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
    public ResponseEntity<Response> doCancel(
            @Parameter(description = "Sales order ID", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(checkoutService.doCancel(id));
    }
}
