package com.inventory.api.controller;

import com.inventory.api.service.PurchaseOrderService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/po")
@AllArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService poService;
}
