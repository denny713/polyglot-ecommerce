package com.inventory.api.controller;

import com.inventory.api.model.dto.request.po.POSearchReq;
import com.inventory.api.model.dto.request.po.POSubmitReq;
import com.inventory.api.model.dto.response.PagingResponse;
import com.inventory.api.model.dto.response.Response;
import com.inventory.api.service.PurchaseOrderService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
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
 */
@RestController
@RequestMapping("/po")
@AllArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService poService;

    @PostMapping("")
    public ResponseEntity<Response> doCreate(@Valid @RequestBody POSubmitReq req) {
        return ResponseEntity.ok(poService.doSubmit(null, req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Response> doUpdate(@PathVariable Long id, @Valid @RequestBody POSubmitReq req) {
        return ResponseEntity.ok(poService.doSubmit(id, req));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Response> doDetail(@PathVariable Long id) {
        return ResponseEntity.ok(poService.doDetail(id));
    }

    @PutMapping("/activate/{id}")
    public ResponseEntity<Response> doActivate(@PathVariable Long id) {
        return ResponseEntity.ok(poService.doActivate(id));
    }

    @PutMapping("/deactivate/{id}")
    public ResponseEntity<Response> doDeactivate(@PathVariable Long id) {
        return ResponseEntity.ok(poService.doDeactivate(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Response> doDelete(@PathVariable Long id) {
        return ResponseEntity.ok(poService.doDelete(id));
    }

    @PutMapping("/approve/{id}")
    public ResponseEntity<Response> doApprove(@PathVariable Long id, @Valid @RequestBody POSubmitReq req) {
        return ResponseEntity.ok(poService.doApprove(id, req));
    }

    @PutMapping("/cancel/{id}")
    public ResponseEntity<Response> doCancel(@PathVariable Long id) {
        return ResponseEntity.ok(poService.doCancel(id));
    }

    @PostMapping("/list")
    public ResponseEntity<PagingResponse> doSearch(@Valid @RequestBody POSearchReq req) {
        return ResponseEntity.ok(poService.doSearch(req));
    }
}
