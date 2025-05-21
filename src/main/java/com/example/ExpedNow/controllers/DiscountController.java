package com.example.ExpedNow.controllers;

import com.example.ExpedNow.models.Discount;
import com.example.ExpedNow.services.core.impl.DiscountService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/discounts") // <-- URL de base
public class DiscountController {

    private final DiscountService discountService;

    // Injection du Service
    public DiscountController(DiscountService discountService) {
        this.discountService = discountService;
    }

    // Endpoint: GET /api/discounts/client/{clientId}
    @GetMapping("/client/{clientId}")
    public List<Discount> getClientDiscounts(@PathVariable String clientId) {
        return discountService.getActiveDiscountsForClient(clientId);
    }

    @GetMapping
    public List<Discount> getAllDiscounts() {
        return discountService.getAllDiscounts();
    }

    @PostMapping
    public ResponseEntity<Discount> createDiscount(@RequestBody Discount discount) {
        try {
            Discount createdDiscount = discountService.createDiscount(discount);
            return ResponseEntity.ok(createdDiscount);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public void deleteDiscount(@PathVariable String id) {
        discountService.deleteDiscount(id);
    }

    // Endpoint: POST /api/discounts/validate?code=XXX&clientId=YYY
    @PostMapping("/validate")
    public Discount validateDiscount(
            @RequestParam String code,
            @RequestParam String clientId) {
        return discountService.validateDiscount(code, clientId);
    }

    // Endpoint: POST /api/discounts/use?code=XXX&clientId=YYY&orderId=ZZZ
    @PostMapping("/use")
    public Discount useDiscount(
            @RequestParam String code,
            @RequestParam String clientId,
            @RequestParam String orderId) {
        return discountService.useDiscount(code, clientId, orderId);
    }
}