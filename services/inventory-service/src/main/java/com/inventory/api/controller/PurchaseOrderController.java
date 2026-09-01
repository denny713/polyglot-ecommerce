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
}
