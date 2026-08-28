package com.inventory.api.controller;

import com.inventory.api.service.PurchaseReturnService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/pr")
@AllArgsConstructor
public class PurchaseReturnController {

    private final PurchaseReturnService prService;
}
