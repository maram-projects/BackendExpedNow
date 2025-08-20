package com.example.ExpedNow.services.core.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Pattern;

@Service
public class IntelligentChatBotService {

    private static final Logger logger = LoggerFactory.getLogger(IntelligentChatBotService.class);

    @Autowired
    private MongoTemplate mongoTemplate;

    // Cache for frequently accessed data
    private Map<String, Object> dataCache = new HashMap<>();
    private LocalDateTime lastCacheUpdate = LocalDateTime.now().minusHours(1);

    public String generateIntelligentResponse(String message, String userRole, String userId) {
        logger.info("Generating intelligent response for role: {}, user: {}, message: {}", userRole, userId, message);

        try {
            // Refresh cache if needed
            refreshCacheIfNeeded();

            // Analyze message intent
            MessageIntent intent = analyzeMessageIntent(message);
            logger.info("Detected intent: {}", intent);

            // Generate response based on intent and role
            return generateContextualResponse(intent, message, userRole, userId);

        } catch (Exception e) {
            logger.error("Error generating intelligent response", e);
            return generateBasicFallbackResponse(message, userRole);
        }
    }

    private void refreshCacheIfNeeded() {
        if (LocalDateTime.now().isAfter(lastCacheUpdate.plusMinutes(15))) {
            logger.info("Refreshing data cache...");
            try {
                // Cache system statistics
                cacheSystemStatistics();

                // Cache user counts
                cacheUserCounts();

                // Cache order statistics
                cacheOrderStatistics();

                lastCacheUpdate = LocalDateTime.now();
                logger.info("Data cache refreshed successfully");
            } catch (Exception e) {
                logger.error("Error refreshing cache", e);
            }
        }
    }

    private void cacheSystemStatistics() {
        try {
            // Total users
            long totalUsers = mongoTemplate.getCollection("users").countDocuments();
            dataCache.put("totalUsers", totalUsers);

            // Active users (logged in last 30 days)
            LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
            Query activeUsersQuery = new Query(Criteria.where("lastLoginDate").gte(thirtyDaysAgo));
            long activeUsers = mongoTemplate.count(activeUsersQuery, "users");
            dataCache.put("activeUsers", activeUsers);

            // Users by role
            long adminCount = mongoTemplate.count(new Query(Criteria.where("role").is("ADMIN")), "users");
            long clientCount = mongoTemplate.count(new Query(Criteria.where("role").in("CLIENT", "INDIVIDUAL", "ENTERPRISE")), "users");
            long deliveryPersonCount = mongoTemplate.count(new Query(Criteria.where("role").in("DELIVERY_PERSON", "PROFESSIONAL", "TEMPORARY")), "users");

            dataCache.put("adminCount", adminCount);
            dataCache.put("clientCount", clientCount);
            dataCache.put("deliveryPersonCount", deliveryPersonCount);

        } catch (Exception e) {
            logger.error("Error caching user statistics", e);
        }
    }

    private void cacheUserCounts() {
        try {
            // New users today
            LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
            Query todayUsersQuery = new Query(Criteria.where("createdDate").gte(startOfDay));
            long newUsersToday = mongoTemplate.count(todayUsersQuery, "users");
            dataCache.put("newUsersToday", newUsersToday);

            // New users this week
            LocalDateTime startOfWeek = LocalDateTime.now().minusDays(7);
            Query weekUsersQuery = new Query(Criteria.where("createdDate").gte(startOfWeek));
            long newUsersThisWeek = mongoTemplate.count(weekUsersQuery, "users");
            dataCache.put("newUsersThisWeek", newUsersThisWeek);

        } catch (Exception e) {
            logger.error("Error caching user counts", e);
        }
    }

    private void cacheOrderStatistics() {
        try {
            // Total orders
            long totalOrders = mongoTemplate.getCollection("orders").countDocuments();
            dataCache.put("totalOrders", totalOrders);

            // Orders today
            LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
            Query todayOrdersQuery = new Query(Criteria.where("createdDate").gte(startOfDay));
            long ordersToday = mongoTemplate.count(todayOrdersQuery, "orders");
            dataCache.put("ordersToday", ordersToday);

            // Active orders
            Query activeOrdersQuery = new Query(Criteria.where("status").in("PENDING", "ACCEPTED", "PICKED_UP", "IN_TRANSIT"));
            long activeOrders = mongoTemplate.count(activeOrdersQuery, "orders");
            dataCache.put("activeOrders", activeOrders);

            // Completed orders today
            Query completedTodayQuery = new Query(Criteria.where("status").is("DELIVERED").and("completedDate").gte(startOfDay));
            long completedToday = mongoTemplate.count(completedTodayQuery, "orders");
            dataCache.put("completedOrdersToday", completedToday);

        } catch (Exception e) {
            logger.error("Error caching order statistics", e);
        }
    }

    private MessageIntent analyzeMessageIntent(String message) {
        String lowerMessage = message.toLowerCase();

        // Statistics intent
        if (lowerMessage.contains("كم") || lowerMessage.contains("عدد") || lowerMessage.contains("إحصائ") ||
                lowerMessage.contains("combien") || lowerMessage.contains("utilisateurs") || lowerMessage.contains("actifs") ||
                lowerMessage.contains("how many") || lowerMessage.contains("users") || lowerMessage.contains("active") ||
                lowerMessage.contains("statistics") || lowerMessage.contains("stats")) {

            if (lowerMessage.contains("مستخدم") || lowerMessage.contains("utilisateurs") || lowerMessage.contains("users")) {
                return MessageIntent.USER_STATISTICS;
            }
            if (lowerMessage.contains("طلب") || lowerMessage.contains("commande") || lowerMessage.contains("order")) {
                return MessageIntent.ORDER_STATISTICS;
            }
            return MessageIntent.GENERAL_STATISTICS;
        }

        // Order management intent
        if (lowerMessage.contains("طلب") || lowerMessage.contains("order") || lowerMessage.contains("commande")) {
            if (lowerMessage.contains("جديد") || lowerMessage.contains("nouveau") || lowerMessage.contains("new") ||
                    lowerMessage.contains("إنشاء") || lowerMessage.contains("créer") || lowerMessage.contains("create")) {
                return MessageIntent.CREATE_ORDER;
            }
            if (lowerMessage.contains("تتبع") || lowerMessage.contains("suivi") || lowerMessage.contains("track")) {
                return MessageIntent.TRACK_ORDER;
            }
            return MessageIntent.ORDER_HELP;
        }

        // User management intent
        if (lowerMessage.contains("مستخدم") || lowerMessage.contains("utilisateur") || lowerMessage.contains("user") ||
                lowerMessage.contains("عميل") || lowerMessage.contains("client")) {
            return MessageIntent.USER_MANAGEMENT;
        }

        // Payment intent
        if (lowerMessage.contains("دفع") || lowerMessage.contains("paiement") || lowerMessage.contains("payment") ||
                lowerMessage.contains("فلوس") || lowerMessage.contains("argent") || lowerMessage.contains("money")) {
            return MessageIntent.PAYMENT_HELP;
        }

        // Earnings intent (for delivery persons)
        if (lowerMessage.contains("ربح") || lowerMessage.contains("gains") || lowerMessage.contains("earnings") ||
                lowerMessage.contains("راتب") || lowerMessage.contains("salaire") || lowerMessage.contains("salary")) {
            return MessageIntent.EARNINGS_INQUIRY;
        }

        // General help
        if (lowerMessage.contains("مساعد") || lowerMessage.contains("aide") || lowerMessage.contains("help") ||
                lowerMessage.contains("كيف") || lowerMessage.contains("comment") || lowerMessage.contains("how")) {
            return MessageIntent.GENERAL_HELP;
        }

        return MessageIntent.GENERAL_INQUIRY;
    }

    private String generateContextualResponse(MessageIntent intent, String message, String userRole, String userId) {
        boolean isArabic = containsArabic(message);
        boolean isFrench = containsFrench(message);

        switch (intent) {
            case USER_STATISTICS:
                return generateUserStatisticsResponse(userRole, isArabic, isFrench);

            case ORDER_STATISTICS:
                return generateOrderStatisticsResponse(userRole, isArabic, isFrench);

            case GENERAL_STATISTICS:
                return generateGeneralStatisticsResponse(userRole, isArabic, isFrench);

            case CREATE_ORDER:
                return generateCreateOrderResponse(userRole, isArabic, isFrench);

            case TRACK_ORDER:
                return generateTrackOrderResponse(userRole, userId, isArabic, isFrench);

            case USER_MANAGEMENT:
                return generateUserManagementResponse(userRole, isArabic, isFrench);

            case PAYMENT_HELP:
                return generatePaymentHelpResponse(userRole, isArabic, isFrench);

            case EARNINGS_INQUIRY:
                return generateEarningsResponse(userRole, userId, isArabic, isFrench);

            case GENERAL_HELP:
                return generateGeneralHelpResponse(userRole, isArabic, isFrench);

            default:
                return generateGeneralInquiryResponse(userRole, isArabic, isFrench);
        }
    }

    private String generateUserStatisticsResponse(String userRole, boolean isArabic, boolean isFrench) {
        if (!"ADMIN".equals(userRole)) {
            if (isFrench) {
                return "Désolé, les statistiques détaillées ne sont disponibles que pour les administrateurs. Vous pouvez voir vos propres informations dans votre profil.";
            } else {
                return "عذراً، الإحصائيات التفصيلية متاحة فقط للمسؤولين. يمكنك مراجعة معلوماتك الشخصية في ملفك الشخصي.";
            }
        }

        // Get real data from cache
        long totalUsers = (Long) dataCache.getOrDefault("totalUsers", 0L);
        long activeUsers = (Long) dataCache.getOrDefault("activeUsers", 0L);
        long newUsersToday = (Long) dataCache.getOrDefault("newUsersToday", 0L);
        long newUsersThisWeek = (Long) dataCache.getOrDefault("newUsersThisWeek", 0L);
        long adminCount = (Long) dataCache.getOrDefault("adminCount", 0L);
        long clientCount = (Long) dataCache.getOrDefault("clientCount", 0L);
        long deliveryPersonCount = (Long) dataCache.getOrDefault("deliveryPersonCount", 0L);

        if (isFrench) {
            return String.format("📊 **Statistiques des utilisateurs ExpedNow:**\n\n" +
                            "👥 **Total des utilisateurs:** %d\n" +
                            "✅ **Utilisateurs actifs (30 derniers jours):** %d\n" +
                            "🆕 **Nouveaux utilisateurs aujourd'hui:** %d\n" +
                            "📅 **Nouveaux utilisateurs cette semaine:** %d\n\n" +
                            "**Répartition par rôle:**\n" +
                            "👨‍💼 Administrateurs: %d\n" +
                            "👤 Clients: %d\n" +
                            "🚗 Livreurs: %d\n\n" +
                            "*Données mises à jour: %s*",
                    totalUsers, activeUsers, newUsersToday, newUsersThisWeek,
                    adminCount, clientCount, deliveryPersonCount,
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        } else {
            return String.format("📊 **إحصائيات مستخدمي ExpedNow:**\n\n" +
                            "👥 **إجمالي المستخدمين:** %d\n" +
                            "✅ **المستخدمين النشطين (آخر 30 يوم):** %d\n" +
                            "🆕 **مستخدمين جدد اليوم:** %d\n" +
                            "📅 **مستخدمين جدد هذا الأسبوع:** %d\n\n" +
                            "**التوزيع حسب الدور:**\n" +
                            "👨‍💼 مسؤولين: %d\n" +
                            "👤 عملاء: %d\n" +
                            "🚗 مندوبين توصيل: %d\n\n" +
                            "*آخر تحديث للبيانات: %s*",
                    totalUsers, activeUsers, newUsersToday, newUsersThisWeek,
                    adminCount, clientCount, deliveryPersonCount,
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        }
    }

    private String generateOrderStatisticsResponse(String userRole, boolean isArabic, boolean isFrench) {
        if (!"ADMIN".equals(userRole)) {
            if (isFrench) {
                return "Vous pouvez voir vos commandes personnelles dans la section 'Mes commandes'. Pour les statistiques générales, contactez un administrateur.";
            } else {
                return "يمكنك مراجعة طلباتك الشخصية في قسم 'طلباتي'. للإحصائيات العامة، تواصل مع المسؤول.";
            }
        }

        long totalOrders = (Long) dataCache.getOrDefault("totalOrders", 0L);
        long ordersToday = (Long) dataCache.getOrDefault("ordersToday", 0L);
        long activeOrders = (Long) dataCache.getOrDefault("activeOrders", 0L);
        long completedToday = (Long) dataCache.getOrDefault("completedOrdersToday", 0L);

        if (isFrench) {
            return String.format("📦 **Statistiques des commandes ExpedNow:**\n\n" +
                            "📊 **Total des commandes:** %d\n" +
                            "📅 **Commandes aujourd'hui:** %d\n" +
                            "⏳ **Commandes actives:** %d\n" +
                            "✅ **Commandes livrées aujourd'hui:** %d\n\n" +
                            "*Données mises à jour: %s*",
                    totalOrders, ordersToday, activeOrders, completedToday,
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        } else {
            return String.format("📦 **إحصائيات طلبات ExpedNow:**\n\n" +
                            "📊 **إجمالي الطلبات:** %d\n" +
                            "📅 **طلبات اليوم:** %d\n" +
                            "⏳ **طلبات نشطة:** %d\n" +
                            "✅ **طلبات مكتملة اليوم:** %d\n\n" +
                            "*آخر تحديث للبيانات: %s*",
                    totalOrders, ordersToday, activeOrders, completedToday,
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        }
    }

    private String generateTrackOrderResponse(String userRole, String userId, boolean isArabic, boolean isFrench) {
        try {
            // Get user's active orders
            Query userOrdersQuery = new Query(Criteria.where("userId").is(userId)
                    .and("status").in("PENDING", "ACCEPTED", "PICKED_UP", "IN_TRANSIT"));
            long userActiveOrders = mongoTemplate.count(userOrdersQuery, "orders");

            if (userActiveOrders == 0) {
                if (isFrench) {
                    return "Vous n'avez actuellement aucune commande active. Toutes vos commandes ont été livrées ou sont en attente d'acceptation.";
                } else {
                    return "ليس لديك أي طلبات نشطة حالياً. جميع طلباتك تم توصيلها أو في انتظار القبول.";
                }
            }

            if (isFrench) {
                return String.format("Vous avez %d commande(s) active(s). Allez dans 'Mes commandes' pour voir les détails et suivre vos livraisons en temps réel.", userActiveOrders);
            } else {
                return String.format("لديك %d طلب نشط. اذهب إلى 'طلباتي' لمراجعة التفاصيل وتتبع التوصيلات بشكل مباشر.", userActiveOrders);
            }

        } catch (Exception e) {
            logger.error("Error getting user orders", e);
            if (isFrench) {
                return "Pour suivre vos commandes, allez dans la section 'Mes commandes' où vous pouvez voir l'état en temps réel de toutes vos livraisons.";
            } else {
                return "لتتبع طلباتك، اذهب إلى قسم 'طلباتي' حيث يمكنك مراجعة حالة جميع توصيلاتك بشكل مباشر.";
            }
        }
    }

    // Add other response methods here...
    private String generateCreateOrderResponse(String userRole, boolean isArabic, boolean isFrench) {
        if (isFrench) {
            return "Pour créer une nouvelle commande:\n" +
                    "1. 📍 Cliquez sur 'Nouvelle commande'\n" +
                    "2. 📍 Entrez les adresses de collecte et livraison\n" +
                    "3. 📦 Décrivez votre colis (taille, poids)\n" +
                    "4. ⏰ Choisissez l'heure de livraison\n" +
                    "5. 💳 Sélectionnez votre mode de paiement\n" +
                    "6. ✅ Confirmez votre commande\n\n" +
                    "Le système calculera automatiquement le prix optimal!";
        } else {
            return "لإنشاء طلب جديد:\n" +
                    "1. 📍 اضغط على 'طلب جديد'\n" +
                    "2. 📍 أدخل عناوين الاستلام والتوصيل\n" +
                    "3. 📦 صف طردك (الحجم، الوزن)\n" +
                    "4. ⏰ اختر وقت التوصيل\n" +
                    "5. 💳 اختر طريقة الدفع\n" +
                    "6. ✅ أكد طلبك\n\n" +
                    "النظام سيحسب السعر الأمثل تلقائياً!";
        }
    }

    private String generateGeneralStatisticsResponse(String userRole, boolean isArabic, boolean isFrench) {
        return generateUserStatisticsResponse(userRole, isArabic, isFrench) + "\n\n" +
                generateOrderStatisticsResponse(userRole, isArabic, isFrench);
    }

    // Add other helper methods...
    private boolean containsArabic(String text) {
        return Pattern.compile("[\u0600-\u06FF]").matcher(text).find();
    }

    private boolean containsFrench(String text) {
        String[] frenchWords = {"combien", "utilisateurs", "actifs", "comment", "aide", "service", "prix", "livraison", "commande", "nouveau"};
        String lowerText = text.toLowerCase();
        for (String word : frenchWords) {
            if (lowerText.contains(word)) return true;
        }
        return false;
    }

    private String generateBasicFallbackResponse(String message, String userRole) {
        if ("ADMIN".equals(userRole)) {
            return "مرحباً! أنا المساعد الإداري. حالياً أعمل في الوضع المحدود بسبب مشكلة مؤقتة. يمكنني مساعدتك في الاستعلامات الأساسية.";
        } else {
            return "مرحباً! أنا مساعد ExpedNow. حالياً أعمل في وضع محدود. يمكنني مساعدتك في الاستعلامات الأساسية حول خدماتنا.";
        }
    }

    // Additional helper methods for other response types
    private String generateUserManagementResponse(String userRole, boolean isArabic, boolean isFrench) {
        if (!"ADMIN".equals(userRole)) {
            return isArabic ? "إدارة المستخدمين متاحة للمسؤولين فقط." : "La gestion des utilisateurs n'est disponible que pour les administrateurs.";
        }
        return isArabic ? "يمكنك إدارة المستخدمين من القائمة الجانبية 'إدارة المستخدمين'." :
                "Vous pouvez gérer les utilisateurs depuis le menu latéral 'Gestion des utilisateurs'.";
    }

    private String generatePaymentHelpResponse(String userRole, boolean isArabic, boolean isFrench) {
        return isArabic ? "طرق الدفع المتاحة: البطاقات البنكية، الدفع عند التوصيل، المحافظ الإلكترونية." :
                "Modes de paiement disponibles: cartes bancaires, paiement à la livraison, portefeuilles électroniques.";
    }

    private String generateEarningsResponse(String userRole, String userId, boolean isArabic, boolean isFrench) {
        if (!userRole.contains("DELIVERY")) {
            return isArabic ? "استعلام الأرباح متاح لمندوبي التوصيل فقط." : "La consultation des gains n'est disponible que pour les livreurs.";
        }
        return isArabic ? "يمكنك مراجعة أرباحك في قسم 'أرباحي' من القائمة الرئيسية." :
                "Vous pouvez consulter vos gains dans la section 'Mes gains' du menu principal.";
    }

    private String generateGeneralHelpResponse(String userRole, boolean isArabic, boolean isFrench) {
        return isArabic ? "كيف يمكنني مساعدتك؟ يمكنني الإجابة على أسئلة حول الطلبات، التوصيل، الدفع، والإحصائيات." :
                "Comment puis-je vous aider? Je peux répondre aux questions sur les commandes, livraisons, paiements et statistiques.";
    }

    private String generateGeneralInquiryResponse(String userRole, boolean isArabic, boolean isFrench) {
        return generateGeneralHelpResponse(userRole, isArabic, isFrench);
    }

    // Message Intent Enum
    private enum MessageIntent {
        USER_STATISTICS,
        ORDER_STATISTICS,
        GENERAL_STATISTICS,
        CREATE_ORDER,
        TRACK_ORDER,
        ORDER_HELP,
        USER_MANAGEMENT,
        PAYMENT_HELP,
        EARNINGS_INQUIRY,
        GENERAL_HELP,
        GENERAL_INQUIRY
    }
}