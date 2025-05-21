package com.example.ExpedNow.controllers;

import com.example.ExpedNow.models.Bonus;
import com.example.ExpedNow.services.core.impl.BonusService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bonuses")
public class BonusController {

    private final BonusService bonusService;

    public BonusController(BonusService bonusService) {
        this.bonusService = bonusService;
    }

    // الحصول على جميع المكافآت (للمسؤول)
    @GetMapping
    public List<Bonus> getAllBonuses() {
        return bonusService.getAllBonuses();
    }

    // الحصول على مكافآت موصل معين
    @GetMapping("/delivery-person/{deliveryPersonId}")
    public List<Bonus> getDeliveryPersonBonuses(@PathVariable String deliveryPersonId) {
        return bonusService.getDeliveryPersonBonuses(deliveryPersonId);
    }

    // الموافقة على المكافأة
    @PostMapping("/{bonusId}/approve")
    public ResponseEntity<Bonus> approveBonus(@PathVariable String bonusId) {
        Bonus approvedBonus = bonusService.approveBonus(bonusId);
        return ResponseEntity.ok(approvedBonus);
    }

    // دفع المكافأة
    @PostMapping("/{bonusId}/pay")
    public ResponseEntity<Bonus> payBonus(@PathVariable String bonusId) {
        Bonus paidBonus = bonusService.payBonus(bonusId);
        return ResponseEntity.ok(paidBonus);
    }

    // رفض المكافأة مع سبب
    @PostMapping("/{bonusId}/reject")
    public ResponseEntity<Bonus> rejectBonus(
            @PathVariable String bonusId,
            @RequestParam String reason) {
        Bonus rejectedBonus = bonusService.rejectBonus(bonusId, reason);
        return ResponseEntity.ok(rejectedBonus);
    }
}