package com.example.ExpedNow.controllers;

import com.example.ExpedNow.models.Bonus;
import com.example.ExpedNow.models.enums.BonusStatus;
import com.example.ExpedNow.services.core.impl.BonusService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bonuses")
public class BonusController {

    private final BonusService bonusService;

    public BonusController(BonusService bonusService) {
        this.bonusService = bonusService;
    }

    // Response wrapper class
    public static class ApiResponse<T> {
        private boolean success;
        private String message;
        private T data;

        public ApiResponse(boolean success, String message, T data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }

        // Getters and setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public T getData() { return data; }
        public void setData(T data) { this.data = data; }
    }

    // List response wrapper
    public static class ApiListResponse<T> {
        private boolean success;
        private String message;
        private List<T> data;
        private List<T> bonuses; // Alternative field name
        private Map<String, Object> pagination;
        private Integer count;

        public ApiListResponse(boolean success, String message, List<T> data) {
            this.success = success;
            this.message = message;
            this.data = data;
            this.bonuses = data; // For compatibility
            this.count = data != null ? data.size() : 0;
        }

        // Getters and setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public List<T> getData() { return data; }
        public void setData(List<T> data) { this.data = data; this.bonuses = data; }
        public List<T> getBonuses() { return bonuses; }
        public void setBonuses(List<T> bonuses) { this.bonuses = bonuses; }
        public Map<String, Object> getPagination() { return pagination; }
        public void setPagination(Map<String, Object> pagination) { this.pagination = pagination; }
        public Integer getCount() { return count; }
        public void setCount(Integer count) { this.count = count; }
    }

    // =============================================================================
    // MAIN BONUS OPERATIONS
    // =============================================================================

    // Get all bonuses with optional filters and pagination
    @GetMapping
    public ResponseEntity<ApiListResponse<Bonus>> getAllBonuses(
            @RequestParam(required = false) BonusStatus status,
            @RequestParam(required = false) String deliveryPersonId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection) {

        try {
            Sort sort = sortDirection.equalsIgnoreCase("desc") ?
                    Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
            Pageable pageable = PageRequest.of(page, size, sort);

            List<Bonus> bonuses = bonusService.getAllBonusesWithFilters(
                    status, deliveryPersonId, startDate, endDate, pageable);

            ApiListResponse<Bonus> response = new ApiListResponse<>(true, "تم جلب المكافآت بنجاح", bonuses);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ApiListResponse<Bonus> response = new ApiListResponse<>(false, e.getMessage(), null);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Get all bonuses without pagination (simple version)
    @GetMapping("/all")
    public ResponseEntity<ApiListResponse<Bonus>> getAllBonusesSimple() {
        try {
            List<Bonus> bonuses = bonusService.getAllBonuses();
            ApiListResponse<Bonus> response = new ApiListResponse<>(true, "تم جلب جميع المكافآت بنجاح", bonuses);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ApiListResponse<Bonus> response = new ApiListResponse<>(false, e.getMessage(), null);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Get a specific bonus by ID
    @GetMapping("/{bonusId}")
    public ResponseEntity<ApiResponse<Bonus>> getBonus(@PathVariable String bonusId) {
        try {
            Bonus bonus = bonusService.getBonusById(bonusId);
            ApiResponse<Bonus> response = new ApiResponse<>(true, "تم جلب المكافأة بنجاح", bonus);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ApiResponse<Bonus> response = new ApiResponse<>(false, e.getMessage(), null);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }

    // Create a new bonus
    // In your Spring controller
    @PostMapping
    public ResponseEntity<ApiResponse<Bonus>> createBonus(@RequestBody Bonus bonusData) {
        try {
            // Convert Date to LocalDateTime if needed
            if (bonusData.getStartDate() == null) {
                bonusData.setStartDate(LocalDateTime.now());
            }
            if (bonusData.getEndDate() == null) {
                bonusData.setEndDate(LocalDateTime.now().plusDays(7));
            }

            bonusData.setCreatedAt(LocalDateTime.now());
            bonusData.setStatus(BonusStatus.PENDING);

            Bonus bonus = bonusService.createBonus(bonusData);
            ApiResponse<Bonus> response = new ApiResponse<>(true, "تم إنشاء المكافأة بنجاح", bonus);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            ApiResponse<Bonus> response = new ApiResponse<>(false, e.getMessage(), null);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    // Update bonus
    @PutMapping("/{bonusId}")
    public ResponseEntity<ApiResponse<Bonus>> updateBonus(
            @PathVariable String bonusId,
            @RequestBody Bonus bonusData) {
        try {
            Bonus bonus = bonusService.updateBonus(bonusId, bonusData);
            ApiResponse<Bonus> response = new ApiResponse<>(true, "تم تحديث المكافأة بنجاح", bonus);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ApiResponse<Bonus> response = new ApiResponse<>(false, e.getMessage(), null);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    // =============================================================================
    // BONUS STATUS OPERATIONS
    // =============================================================================

    // Approve bonus (for admin)
    @PatchMapping("/{bonusId}/approve")
    public ResponseEntity<ApiResponse<Bonus>> approveBonus(@PathVariable String bonusId) {
        try {
            Bonus approvedBonus = bonusService.approveBonus(bonusId);
            ApiResponse<Bonus> response = new ApiResponse<>(true, "تم الموافقة على المكافأة بنجاح", approvedBonus);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ApiResponse<Bonus> response = new ApiResponse<>(false, e.getMessage(), null);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    // Reject bonus (for admin)
    @PatchMapping("/{bonusId}/reject")
    public ResponseEntity<ApiResponse<Bonus>> rejectBonus(
            @PathVariable String bonusId,
            @RequestBody Map<String, String> requestBody) {
        try {
            String reason = requestBody.get("reason");
            Bonus rejectedBonus = bonusService.rejectBonus(bonusId, reason);
            ApiResponse<Bonus> response = new ApiResponse<>(true, "تم رفض المكافأة", rejectedBonus);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ApiResponse<Bonus> response = new ApiResponse<>(false, e.getMessage(), null);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    // Pay bonus (for admin)
    @PatchMapping("/{bonusId}/pay")
    public ResponseEntity<ApiResponse<Bonus>> payBonus(@PathVariable String bonusId) {
        try {
            Bonus paidBonus = bonusService.payBonus(bonusId);
            ApiResponse<Bonus> response = new ApiResponse<>(true, "تم دفع المكافأة بنجاح", paidBonus);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ApiResponse<Bonus> response = new ApiResponse<>(false, e.getMessage(), null);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    // Cancel bonus
    @PatchMapping("/{bonusId}/cancel")
    public ResponseEntity<ApiResponse<Bonus>> cancelBonus(@PathVariable String bonusId) {
        try {
            Bonus cancelledBonus = bonusService.cancelBonus(bonusId);
            ApiResponse<Bonus> response = new ApiResponse<>(true, "تم إلغاء المكافأة", cancelledBonus);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ApiResponse<Bonus> response = new ApiResponse<>(false, e.getMessage(), null);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    // Update bonus status directly
    @PatchMapping("/{bonusId}/status")
    public ResponseEntity<ApiResponse<Bonus>> updateBonusStatus(
            @PathVariable String bonusId,
            @RequestBody Map<String, String> requestBody) {
        try {
            BonusStatus status = BonusStatus.valueOf(requestBody.get("status"));
            Bonus bonus = bonusService.updateBonusStatus(bonusId, status);
            ApiResponse<Bonus> response = new ApiResponse<>(true, "تم تحديث حالة المكافأة", bonus);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ApiResponse<Bonus> response = new ApiResponse<>(false, e.getMessage(), null);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    // =============================================================================
    // DELIVERY PERSON SPECIFIC OPERATIONS
    // =============================================================================

    // Get delivery person bonuses
    @GetMapping("/delivery-person/{deliveryPersonId}")
    public ResponseEntity<ApiListResponse<Bonus>> getDeliveryPersonBonuses(@PathVariable String deliveryPersonId) {
        try {
            List<Bonus> bonuses = bonusService.getDeliveryPersonBonuses(deliveryPersonId);
            ApiListResponse<Bonus> response = new ApiListResponse<>(true, "تم جلب مكافآت الموصل بنجاح", bonuses);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ApiListResponse<Bonus> response = new ApiListResponse<>(false, e.getMessage(), null);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Get bonus history for a delivery person
    @GetMapping("/delivery-person/{deliveryPersonId}/history")
    public ResponseEntity<ApiListResponse<Bonus>> getBonusHistory(
            @PathVariable String deliveryPersonId,
            @RequestParam(required = false) Integer limit) {
        try {
            List<Bonus> bonuses = bonusService.getBonusHistory(deliveryPersonId, limit);
            ApiListResponse<Bonus> response = new ApiListResponse<>(true, "تم جلب تاريخ المكافآت بنجاح", bonuses);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ApiListResponse<Bonus> response = new ApiListResponse<>(false, e.getMessage(), null);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Get delivery person stats (NEW)
    @GetMapping("/delivery-person/{deliveryPersonId}/stats")
    public ResponseEntity<Map<String, Object>> getDeliveryPersonStats(@PathVariable String deliveryPersonId) {
        try {
            Map<String, Object> stats = bonusService.getDeliveryStats(deliveryPersonId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "تم جلب إحصائيات الموصل بنجاح");
            response.put("data", stats);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            response.put("data", null);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Calculate potential bonus for delivery person (NEW)
    @GetMapping("/delivery-person/{deliveryPersonId}/potential")
    public ResponseEntity<Map<String, Object>> calculatePotentialBonus(@PathVariable String deliveryPersonId) {
        try {
            Map<String, Object> result = bonusService.calculatePotentialBonus(deliveryPersonId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            response.put("potentialBonus", 0.0);
            response.put("criteria", null);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // =============================================================================
    // FILTERING AND SEARCHING
    // =============================================================================

    // Get bonuses by status
    @GetMapping("/status/{status}")
    public ResponseEntity<ApiListResponse<Bonus>> getBonusesByStatus(@PathVariable BonusStatus status) {
        try {
            List<Bonus> bonuses = bonusService.getBonusesByStatus(status);
            ApiListResponse<Bonus> response = new ApiListResponse<>(true, "تم جلب المكافآت حسب الحالة", bonuses);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ApiListResponse<Bonus> response = new ApiListResponse<>(false, e.getMessage(), null);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Search bonuses with filters
    @GetMapping("/search")
    public ResponseEntity<ApiListResponse<Bonus>> searchBonuses(
            @RequestParam(required = false) BonusStatus status,
            @RequestParam(required = false) String deliveryPersonId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        try {
            List<Bonus> bonuses = bonusService.searchBonuses(status, deliveryPersonId, startDate, endDate);
            ApiListResponse<Bonus> response = new ApiListResponse<>(true, "تم البحث بنجاح", bonuses);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ApiListResponse<Bonus> response = new ApiListResponse<>(false, e.getMessage(), null);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Advanced search with date range (NEW)
    // Advanced search with date range (FIXED)
    @GetMapping("/search/advanced")
    public ResponseEntity<ApiListResponse<Bonus>> searchBonusesWithDateRange(
            @RequestParam(required = false) BonusStatus status,
            @RequestParam(required = false) String deliveryPersonId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        try {
            LocalDateTime start = null;
            LocalDateTime end = null;

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            if (startDate != null && !startDate.isEmpty()) {
                try {
                    start = LocalDateTime.parse(startDate, formatter);
                } catch (DateTimeParseException e) {
                    try {
                        start = LocalDateTime.parse(startDate + " 00:00:00", formatter);
                    } catch (DateTimeParseException ex) {
                        // Fix: Use LocalDate.parse() then convert to LocalDateTime
                        start = LocalDate.parse(startDate, dateFormatter).atStartOfDay();
                    }
                }
            }

            if (endDate != null && !endDate.isEmpty()) {
                try {
                    end = LocalDateTime.parse(endDate, formatter);
                } catch (DateTimeParseException e) {
                    try {
                        end = LocalDateTime.parse(endDate + " 23:59:59", formatter);
                    } catch (DateTimeParseException ex) {
                        // Fix: Use LocalDate.parse() then convert to LocalDateTime with time
                        end = LocalDate.parse(endDate, dateFormatter).atTime(23, 59, 59);
                    }
                }
            }

            List<Bonus> bonuses = bonusService.searchBonusesWithDateRange(status, deliveryPersonId, start, end);
            ApiListResponse<Bonus> response = new ApiListResponse<>(true, "تم البحث المتقدم بنجاح", bonuses);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ApiListResponse<Bonus> response = new ApiListResponse<>(false, e.getMessage(), null);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    // =============================================================================
    // BULK OPERATIONS
    // =============================================================================

    // Bulk approve bonuses
    @PostMapping("/bulk/approve")
    public ResponseEntity<Map<String, Object>> bulkApproveBonuses(@RequestBody Map<String, List<String>> requestBody) {
        try {
            List<String> bonusIds = requestBody.get("bonusIds");
            if (bonusIds == null || bonusIds.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "يجب تحديد معرفات المكافآت");
                response.put("processedCount", 0);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            int processedCount = bonusService.bulkApproveBonuses(bonusIds);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "تم الموافقة على المكافآت بنجاح");
            response.put("processedCount", processedCount);
            response.put("totalRequested", bonusIds.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            response.put("processedCount", 0);

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    // Bulk pay bonuses (NEW)
    @PostMapping("/bulk/pay")
    public ResponseEntity<Map<String, Object>> bulkPayBonuses(@RequestBody Map<String, List<String>> requestBody) {
        try {
            List<String> bonusIds = requestBody.get("bonusIds");
            if (bonusIds == null || bonusIds.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "يجب تحديد معرفات المكافآت");
                response.put("processedCount", 0);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            int processedCount = bonusService.bulkPayBonuses(bonusIds);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "تم دفع المكافآت بنجاح");
            response.put("processedCount", processedCount);
            response.put("totalRequested", bonusIds.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            response.put("processedCount", 0);

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    // Bulk reject bonuses (NEW)
    @PostMapping("/bulk/reject")
    public ResponseEntity<Map<String, Object>> bulkRejectBonuses(@RequestBody Map<String, Object> requestBody) {
        try {
            @SuppressWarnings("unchecked")
            List<String> bonusIds = (List<String>) requestBody.get("bonusIds");
            String reason = (String) requestBody.get("reason");

            if (bonusIds == null || bonusIds.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "يجب تحديد معرفات المكافآت");
                response.put("processedCount", 0);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            if (reason == null || reason.trim().isEmpty()) {
                reason = "رفض جماعي";
            }

            int processedCount = bonusService.bulkRejectBonuses(bonusIds, reason);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "تم رفض المكافآت بنجاح");
            response.put("processedCount", processedCount);
            response.put("totalRequested", bonusIds.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            response.put("processedCount", 0);

            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    // =============================================================================
    // STATISTICS AND SUMMARY
    // =============================================================================

    // Get basic bonus statistics
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getBonusStats() {
        try {
            Map<String, Object> stats = bonusService.getBonusStats();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "تم جلب الإحصائيات بنجاح");
            response.put("data", stats);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            response.put("data", null);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Get enhanced bonus statistics (NEW)
    @GetMapping("/stats/enhanced")
    public ResponseEntity<Map<String, Object>> getEnhancedBonusStats() {
        try {
            Map<String, Object> stats = bonusService.getEnhancedBonusStats();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "تم جلب الإحصائيات المحسنة بنجاح");
            response.put("data", stats);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            response.put("data", null);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Get bonus summary
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getBonusSummary() {
        try {
            Map<String, Object> summary = bonusService.getBonusSummary();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "تم جلب الملخص بنجاح");
            response.put("data", summary);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            response.put("data", null);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // =============================================================================
    // ADMIN ACTIONS
    // =============================================================================

    // Trigger manual weekly bonus calculation (for testing/admin purposes)
    @PostMapping("/calculate-weekly")
    public ResponseEntity<Map<String, Object>> triggerWeeklyBonusCalculation() {
        try {
            bonusService.calculateWeeklyBonuses();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "تم تشغيل حساب المكافآت الأسبوعية بنجاح");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "خطأ في حساب المكافآت الأسبوعية: " + e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // BonusController.java
    @GetMapping("/delivery-person/{deliveryPersonId}/summary")
    public ResponseEntity<Map<String, Object>> getDeliveryPersonSummary(@PathVariable String deliveryPersonId) {
        try {
            Map<String, Object> summary = bonusService.getDeliveryPersonSummary(deliveryPersonId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "تم جلب ملخص المكافآت للموصل");
            response.put("data", summary);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            response.put("data", null);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}