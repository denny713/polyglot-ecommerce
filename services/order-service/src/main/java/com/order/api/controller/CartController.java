package com.order.api.controller;

import com.order.api.constant.SecurityType;
import com.order.api.model.dto.request.cart.CartPushReq;
import com.order.api.model.dto.response.Response;
import com.order.api.service.CartService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP surface for the cart, under {@code /order/cart}. */
@RestController
@RequestMapping("/order/cart")
@AllArgsConstructor
@Tag(name = "Cart Order", description = "Managing cart orders")
@SecurityRequirement(name = SecurityType.SECURITY_SCHEME)
public class CartController {

    private final CartService cartService;

    @PostMapping("")
    @Operation(
            operationId = "pushCart",
            summary = "Put a product into the cart",
            description = "Put a product into the signed-in customer's cart. Sending the same product "
                    + "again replaces the quantity on that line, it does not add to it.")
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
                            schema = @Schema(implementation = Response.class)))
    })
    public ResponseEntity<Response> doPush(@Valid @RequestBody CartPushReq req) {
        return ResponseEntity.ok(cartService.doPush(req));
    }

    @DeleteMapping("")
    @Operation(
            operationId = "removeCart",
            summary = "Take a product out of the cart",
            description = "Remove one product from the signed-in customer's cart. The whole line goes, "
                    + "whatever quantity it held.")
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
    public ResponseEntity<Response> doRemove(
            // Not declared required: a missing product is reported by the service, in
            // the same envelope as every other refusal.
            @Parameter(description = "Product ID", example = "7")
            @RequestParam(required = false) Long productId) {
        return ResponseEntity.ok(cartService.doRemove(productId));
    }
}
