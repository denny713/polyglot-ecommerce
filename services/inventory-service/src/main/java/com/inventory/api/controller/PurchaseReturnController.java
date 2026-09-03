package com.inventory.api.controller;

import com.inventory.api.model.dto.request.pr.PRSearchReq;
import com.inventory.api.model.dto.request.pr.PRSubmitReq;
import com.inventory.api.model.dto.response.PagingResponse;
import com.inventory.api.model.dto.response.Response;
import com.inventory.api.service.PurchaseReturnService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pr")
@AllArgsConstructor
public class PurchaseReturnController {

    private final PurchaseReturnService prService;

    @PostMapping("")
    public ResponseEntity<Response> doCreate(@Valid @RequestBody PRSubmitReq req) {
        return ResponseEntity.ok(prService.doSubmit(null, req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Response> doUpdate(@PathVariable Long id, @Valid @RequestBody PRSubmitReq req) {
        return ResponseEntity.ok(prService.doSubmit(id, req));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Response> doDetail(@PathVariable Long id) {
        return ResponseEntity.ok(prService.doDetail(id));
    }

    @PutMapping("/activate/{id}")
    public ResponseEntity<Response> doActivate(@PathVariable Long id) {
        return ResponseEntity.ok(prService.doActivate(id));
    }

    @PutMapping("/deactivate/{id}")
    public ResponseEntity<Response> doDeactivate(@PathVariable Long id) {
        return ResponseEntity.ok(prService.doDeactivate(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Response> doDelete(@PathVariable Long id) {
        return ResponseEntity.ok(prService.doDelete(id));
    }

    @PutMapping("/approve/{id}")
    public ResponseEntity<Response> doApprove(@PathVariable Long id, @Valid @RequestBody PRSubmitReq req) {
        return ResponseEntity.ok(prService.doApprove(id, req));
    }

    @PutMapping("/cancel/{id}")
    public ResponseEntity<Response> doCancel(@PathVariable Long id) {
        return ResponseEntity.ok(prService.doCancel(id));
    }

    @PostMapping("/list")
    public ResponseEntity<PagingResponse> doSearch(@Valid @RequestBody PRSearchReq req) {
        return ResponseEntity.ok(prService.doSearch(req));
    }
}
