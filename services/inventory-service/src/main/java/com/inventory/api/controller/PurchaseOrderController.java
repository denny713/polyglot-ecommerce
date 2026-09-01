package com.inventory.api.controller;

import com.inventory.api.model.dto.request.po.POSubmitReq;
import com.inventory.api.model.dto.response.Response;
import com.inventory.api.service.PurchaseOrderService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}
