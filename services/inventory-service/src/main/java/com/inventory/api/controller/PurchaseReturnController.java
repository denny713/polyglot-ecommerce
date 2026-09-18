package com.inventory.api.controller;

import com.inventory.api.constant.SecurityType;
import com.inventory.api.model.dto.request.pr.PRSearchReq;
import com.inventory.api.model.dto.request.pr.PRSubmitReq;
import com.inventory.api.model.dto.response.PagingResponse;
import com.inventory.api.model.dto.response.Response;
import com.inventory.api.service.PurchaseReturnService;
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
 * HTTP surface for purchase returns, under {@code /pr}.
 * <p>
 * Mirrors {@code PurchaseOrderController} with one difference: approve carries no
 * body, because a return ships the quantities already recorded on the document
 * instead of reporting new ones.
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
@RequestMapping("/pr")
@AllArgsConstructor
@Tag(name = "Purchase Return", description = "Sending goods back to a supplier")
@SecurityRequirement(name = SecurityType.SECURITY_SCHEME)
public class PurchaseReturnController {

    private final PurchaseReturnService prService;

    @PostMapping("")
    @Operation(
            operationId = "createPurchaseReturn",
            summary = "Create a new purchase return",
            description = "Create a new purchase return in draft, the lines are priced from the product's buy price.")
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
    public ResponseEntity<Response> doCreate(@Valid @RequestBody PRSubmitReq req) {
        return ResponseEntity.ok(prService.doSubmit(null, req));
    }

    @PutMapping("/{id}")
    @Operation(
            operationId = "updatePurchaseReturn",
            summary = "Update an existing purchase return",
            description = "Update a draft or cancelled purchase return, the details are reconciled and an omitted line is deleted.")
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
            @Parameter(description = "Purchase return ID", example = "1") @PathVariable Long id,
            @Valid @RequestBody PRSubmitReq req) {
        return ResponseEntity.ok(prService.doSubmit(id, req));
    }

    @GetMapping("/{id}")
    @Operation(
            operationId = "getPurchaseReturn",
            summary = "Detail a purchase return",
            description = "Get details of a specific purchase return, with its lines.")
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
            @Parameter(description = "Purchase return ID", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(prService.doDetail(id));
    }

    @PutMapping("/activate/{id}")
    @Operation(
            operationId = "activatePurchaseReturn",
            summary = "Activate a purchase return",
            description = "Activate a specific purchase return, this is visibility only and does not touch the status.")
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
            @Parameter(description = "Purchase return ID", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(prService.doActivate(id));
    }

    @PutMapping("/deactivate/{id}")
    @Operation(
            operationId = "deactivatePurchaseReturn",
            summary = "Deactivate a purchase return",
            description = "Deactivate a specific purchase return, this is visibility only and does not touch the status.")
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
            @Parameter(description = "Purchase return ID", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(prService.doDeactivate(id));
    }

    @DeleteMapping("/{id}")
    @Operation(
            operationId = "deletePurchaseReturn",
            summary = "Delete a purchase return",
            description = "Delete a specific purchase return, the row is only marked deleted and is not removed.")
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
            @Parameter(description = "Purchase return ID", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(prService.doDelete(id));
    }

    @PutMapping("/approve/{id}")
    @Operation(
            operationId = "approvePurchaseReturn",
            summary = "Approve a purchase return",
            description = "Approve a draft purchase return, the stored quantities are deducted from stock and cannot go below zero.")
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
            @Parameter(description = "Purchase return ID", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(prService.doApprove(id));
    }

    @PutMapping("/cancel/{id}")
    @Operation(
            operationId = "cancelPurchaseReturn",
            summary = "Cancel a purchase return",
            description = "Cancel a draft purchase return, submitting it again puts it back into draft.")
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
            @Parameter(description = "Purchase return ID", example = "1") @PathVariable Long id) {
        return ResponseEntity.ok(prService.doCancel(id));
    }

    @PostMapping("/list")
    @Operation(
            operationId = "searchPurchaseReturns",
            summary = "Search purchase returns",
            description = "Search purchase returns by document number (ILIKE), supplier, status, grand total range, created range and active flag.")
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
    public ResponseEntity<PagingResponse> doSearch(@Valid @RequestBody PRSearchReq req) {
        return ResponseEntity.ok(prService.doSearch(req));
    }
}
