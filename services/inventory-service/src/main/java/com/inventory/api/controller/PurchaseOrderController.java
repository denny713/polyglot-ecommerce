package com.inventory.api.controller;

import com.inventory.api.constant.SecurityType;
import com.inventory.api.model.dto.request.po.POSearchReq;
import com.inventory.api.model.dto.request.po.POSubmitReq;
import com.inventory.api.model.dto.response.PagingResponse;
import com.inventory.api.model.dto.response.Response;
import com.inventory.api.service.PurchaseOrderService;
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
import org.springframework.web.bind.annotation.*;

/**
 * HTTP surface for purchase orders, under {@code /po}.
 * <p>
 * Create and update share one method: {@code doSubmit} treats a null id as a new
 * document. Note that approve takes a body while cancel does not, because
 * approving is where the real received quantity per line is reported and stock is
 * moved.
 * <p>
 * Authorization is not declared here. {@code TokenFilter} requires the Keycloak
 * {@code admin} role before the request ever reaches this class.
 * <p>
 * The OpenAPI annotations are written out by hand: every method returns
 * {@code Response}, so the status codes come from {@code ResponseHandler} and
 * {@code TokenFilter} rather than from anything visible in a signature.
 * {@link SecurityRequirement} is documentation only — it puts the padlock in
 * Swagger UI and enforces nothing.
 */
@RestController
@RequestMapping("/po")
@AllArgsConstructor
@Tag(name = "Purchase Order", description = "Ordering goods from a supplier")
@SecurityRequirement(name = SecurityType.SECURITY_SCHEME)
public class PurchaseOrderController {

    private final PurchaseOrderService poService;

    @PostMapping("")
    @Operation(
            operationId = "createPurchaseOrder",
            summary = "Create a new purchase order",
            description = "Create a new purchase order in draft, the lines are priced from the product's buy price.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK, the envelope code is 201",
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
    public ResponseEntity<Response> doCreate(@Valid @RequestBody POSubmitReq req) {
        return ResponseEntity.ok(poService.doSubmit(null, req));
    }

    @PutMapping("/{id}")
    @Operation(
            operationId = "updatePurchaseOrder",
            summary = "Update an existing purchase order",
            description = "Update a draft or cancelled purchase order, the details are reconciled and an omitted line is deleted.")
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
    public ResponseEntity<Response> doUpdate(
            @Parameter(description = "Purchase order ID", example = "1") @PathVariable Long id,
            @Valid @RequestBody POSubmitReq req) {
        return ResponseEntity.ok(poService.doSubmit(id, req));
    }

    @GetMapping("/{id}")
    @Operation(
            operationId = "getPurchaseOrder",
            summary = "Detail a purchase order",
            description = "Get details of a specific purchase order, with its lines.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK",
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
    public ResponseEntity<Response> doDetail(
            @Parameter(description = "Purchase order ID", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(poService.doDetail(id));
    }

    @PutMapping("/activate/{id}")
    @Operation(
            operationId = "activatePurchaseOrder",
            summary = "Activate a purchase order",
            description = "Activate a specific purchase order, this is visibility only and does not touch the status.")
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
    public ResponseEntity<Response> doActivate(
            @Parameter(description = "Purchase order ID", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(poService.doActivate(id));
    }

    @PutMapping("/deactivate/{id}")
    @Operation(
            operationId = "deactivatePurchaseOrder",
            summary = "Deactivate a purchase order",
            description = "Deactivate a specific purchase order, this is visibility only and does not touch the status.")
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
    public ResponseEntity<Response> doDeactivate(
            @Parameter(description = "Purchase order ID", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(poService.doDeactivate(id));
    }

    @DeleteMapping("/{id}")
    @Operation(
            operationId = "deletePurchaseOrder",
            summary = "Delete a purchase order",
            description = "Delete a specific purchase order, the row is only marked deleted and is not removed.")
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
    public ResponseEntity<Response> doDelete(
            @Parameter(description = "Purchase order ID", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(poService.doDelete(id));
    }

    @PutMapping("/approve/{id}")
    @Operation(
            operationId = "approvePurchaseOrder",
            summary = "Approve a purchase order",
            description = "Approve a draft purchase order, the quantity on each line is the received one and is added to stock.")
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
    public ResponseEntity<Response> doApprove(
            @Parameter(description = "Purchase order ID", example = "1") @PathVariable Long id,
            @Valid @RequestBody POSubmitReq req) {
        return ResponseEntity.ok(poService.doApprove(id, req));
    }

    @PutMapping("/cancel/{id}")
    @Operation(
            operationId = "cancelPurchaseOrder",
            summary = "Cancel a purchase order",
            description = "Cancel a draft purchase order, submitting it again puts it back into draft.")
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
            @Parameter(description = "Purchase order ID", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(poService.doCancel(id));
    }

    @PostMapping("/list")
    @Operation(
            operationId = "searchPurchaseOrders",
            summary = "Search purchase orders",
            description = "Search purchase orders by document number (ILIKE), supplier, status, order and real grand total range, created range and active flag.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = PagingResponse.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = Response.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = Response.class))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = Response.class)))
    })
    public ResponseEntity<PagingResponse> doSearch(@Valid @RequestBody POSearchReq req) {
        return ResponseEntity.ok(poService.doSearch(req));
    }
}
