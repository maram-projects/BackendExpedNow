package com.example.ExpedNow.services.core.impl;

import com.example.ExpedNow.dto.ImageAnalysisResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class ImageAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(ImageAnalysisService.class);

    @Value("${image.analysis.api.url:http://localhost:5001}")
    private String apiUrl;

    @Value("${image.analysis.api.timeout:120000}")  // 120 seconds
    private int timeout;

    @Value("${image.analysis.api.connection-timeout:30000}")  // 30 seconds
    private int connectionTimeout;

    @Value("${image.analysis.enabled:true}")
    private boolean enabled;

    @Value("${image.analysis.fallback-on-error:true}")
    private boolean fallbackOnError;

    @Value("${image.analysis.max-retries:3}")
    private int maxRetries;

    @Value("${image.analysis.retry-delay:1000}")
    private int retryDelay;

    @Value("${image.analysis.supported-formats:jpg,jpeg,png,gif,bmp,webp}")
    private List<String> supportedFormats;

    @Value("${image.analysis.max-file-size:16777216}") // 16MB in bytes
    private long maxFileSize;

    @Value("${image.analysis.quality-threshold:0.5}")
    private double qualityThreshold;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public ImageAnalysisService(RestTemplateBuilder builder, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;

        // Ensure timeouts are positive
        int connectTimeoutMs = connectionTimeout > 0 ? connectionTimeout : 30000;
        int readTimeoutMs = timeout > 0 ? timeout : 120000;

        // Create RequestConfig with timeouts
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(connectTimeoutMs))
                .setResponseTimeout(Timeout.ofMilliseconds(readTimeoutMs))
                .build();

        // Create HttpClient with the RequestConfig
        HttpClient httpClient = HttpClientBuilder.create()
                .setDefaultRequestConfig(requestConfig)
                .build();

        // Create HttpComponentsClientHttpRequestFactory with the configured HttpClient
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);

        // Build RestTemplate with the custom request factory
        this.restTemplate = builder
                .requestFactory(() -> factory)
                .build();
    }

    /**
     * تحليل شامل للصورة مع إعادة المحاولة
     */
    public ImageAnalysisResponse analyzeImage(MultipartFile imageFile) {
        if (!enabled) {
            logger.warn("Image analysis is disabled in configuration");
            return createDisabledResponse();
        }

        // Validate image file first
        String validationError = validateImageFile(imageFile);
        if (validationError != null) {
            logger.warn("Invalid image file: {}", validationError);
            return createErrorResponse(validationError);
        }

        String url = apiUrl + "/analyze";
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                logger.info("Attempting image analysis (attempt {}/{}) for file: {}",
                        attempt, maxRetries, imageFile.getOriginalFilename());

                ImageAnalysisResponse response = performImageAnalysis(url, imageFile);

                if (response != null && response.isSuccess()) {
                    logger.info("Image analysis successful on attempt {}", attempt);
                    return enrichResponse(response);
                }

                String errorMsg = response != null ? response.getError() : "Unknown error";
                logger.warn("Image analysis failed on attempt {}: {}", attempt, errorMsg);
                lastException = new RuntimeException(errorMsg);

                // إذا كانت هذه المحاولة الأخيرة، تعامل مع الخطأ
                if (attempt == maxRetries) {
                    if (fallbackOnError) {
                        return createFallbackResponse(errorMsg);
                    } else {
                        return response != null ? response : createErrorResponse(errorMsg);
                    }
                }

                // انتظار قبل المحاولة التالية
                Thread.sleep(retryDelay);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Image analysis interrupted");
                break;
            } catch (Exception e) {
                logger.error("Error during image analysis attempt {}: {}", attempt, e.getMessage());
                lastException = e;

                if (attempt == maxRetries) {
                    if (fallbackOnError) {
                        return createFallbackResponse("فشل في تحليل الصورة بعد " + maxRetries + " محاولات: " + e.getMessage());
                    } else {
                        return createErrorResponse("فشل في تحليل الصورة: " + e.getMessage());
                    }
                }

                try {
                    Thread.sleep(retryDelay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        String finalError = lastException != null ? lastException.getMessage() : "فشل في تحليل الصورة بعد عدة محاولات";
        return createErrorResponse(finalError);
    }

    /**
     * استخراج النص من الصورة فقط
     */
    public ImageAnalysisResponse extractTextFromImage(MultipartFile imageFile) {
        if (!enabled) {
            logger.warn("Image analysis is disabled in configuration");
            return createDisabledResponse();
        }

        String validationError = validateImageFile(imageFile);
        if (validationError != null) {
            return createErrorResponse(validationError);
        }

        String url = apiUrl + "/ocr";

        try {
            ImageAnalysisResponse response = performImageAnalysis(url, imageFile);
            return response != null ? enrichResponse(response) : createErrorResponse("No response from OCR service");
        } catch (Exception e) {
            logger.error("Error extracting text from image: {}", e.getMessage());
            if (fallbackOnError) {
                return createFallbackResponse("فشل في استخراج النص من الصورة: " + e.getMessage());
            } else {
                return createErrorResponse("فشل في استخراج النص: " + e.getMessage());
            }
        }
    }

    /**
     * تنفيذ تحليل الصورة الفعلي
     */
    private ImageAnalysisResponse performImageAnalysis(String url, MultipartFile imageFile) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("image", new ByteArrayResource(imageFile.getBytes()) {
            @Override
            public String getFilename() {
                return imageFile.getOriginalFilename();
            }
        });

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<ImageAnalysisResponse> response = restTemplate.exchange(
                    url, HttpMethod.POST, requestEntity, ImageAnalysisResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            } else {
                logger.error("Flask API returned error status: {}", response.getStatusCode());
                return createErrorResponse("خدمة تحليل الصور ترد برمز خطأ: " + response.getStatusCode());
            }
        } catch (RestClientException e) {
            logger.error("REST client error: {}", e.getMessage());
            throw new IOException("خطأ في الاتصال مع خدمة تحليل الصور: " + e.getMessage(), e);
        }
    }

    /**
     * تحسين الاستجابة بإضافة معلومات إضافية
     */
    private ImageAnalysisResponse enrichResponse(ImageAnalysisResponse response) {
        if (response == null) {
            return createErrorResponse("استجابة فارغة من خدمة التحليل");
        }

        // إضافة الوقت الحالي
        if (response.getDeliveryRelevantInfo() != null) {
            response.getDeliveryRelevantInfo().setAnalyzedAt(
                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            );
        }

        // تحسين البيانات للعرض
        if (response.getAnalysis() != null && response.getAnalysis().getTextExtraction() != null) {
            response.withAnalysisData(response.getAnalysis().getTextExtraction());
        }

        return response;
    }

    /**
     * التحقق من صحة ملف الصورة
     */
    private String validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return "لم يتم إرفاق ملف صورة";
        }

        // فحص حجم الملف
        if (file.getSize() > maxFileSize) {
            return String.format("حجم الملف كبير جداً. الحد الأقصى %d MB", maxFileSize / (1024 * 1024));
        }

        // فحص نوع المحتوى
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return "نوع الملف يجب أن يكون صورة";
        }

        // فحص امتداد الملف
        String filename = file.getOriginalFilename();
        if (filename == null || filename.trim().isEmpty()) {
            return "اسم الملف غير صحيح";
        }

        String extension = getFileExtension(filename).toLowerCase();
        if (!supportedFormats.contains(extension)) {
            return String.format("امتداد الملف غير مدعوم. الامتدادات المدعومة: %s", supportedFormats);
        }

        return null; // الملف صحيح
    }

    /**
     * استخراج امتداد الملف
     */
    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }

        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
            return "";
        }

        return filename.substring(lastDotIndex + 1);
    }

    /**
     * إنشاء استجابة خطأ
     */
    private ImageAnalysisResponse createErrorResponse(String errorMessage) {
        ImageAnalysisResponse response = new ImageAnalysisResponse();
        response.setSuccess(false);
        response.setError(errorMessage);
        response.setTimestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        response.setImageAnalyzed(false);

        // إنشاء كائنات فارغة لتجنب null pointer exceptions
        ImageAnalysisResponse.Analysis analysis = new ImageAnalysisResponse.Analysis();
        ImageAnalysisResponse.Analysis.TextExtraction textExtraction =
                new ImageAnalysisResponse.Analysis.TextExtraction();
        textExtraction.setFullText("");
        analysis.setTextExtraction(textExtraction);

        ImageAnalysisResponse.DeliveryRelevantInfo deliveryInfo =
                new ImageAnalysisResponse.DeliveryRelevantInfo();
        deliveryInfo.setImageQuality("error");
        deliveryInfo.setHasText(false);
        deliveryInfo.setSuitableForDelivery(false);
        deliveryInfo.setAnalyzedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        response.setAnalysis(analysis);
        response.setDeliveryRelevantInfo(deliveryInfo);

        return response;
    }

    /**
     * إنشاء استجابة احتياطية عند فشل التحليل
     */
    private ImageAnalysisResponse createFallbackResponse(String originalError) {
        ImageAnalysisResponse response = new ImageAnalysisResponse();
        response.setSuccess(true); // نجح في الحفظ حتى لو فشل التحليل
        response.setError("تم رفع الصورة بنجاح، لكن فشل التحليل: " + originalError);
        response.setTimestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        response.setImageAnalyzed(false);

        // إنشاء تحليل أساسي
        ImageAnalysisResponse.Analysis analysis = new ImageAnalysisResponse.Analysis();
        ImageAnalysisResponse.Analysis.TextExtraction textExtraction =
                new ImageAnalysisResponse.Analysis.TextExtraction();
        textExtraction.setFullText("لم يتم استخراج النص بسبب فشل التحليل");
        analysis.setTextExtraction(textExtraction);

        ImageAnalysisResponse.DeliveryRelevantInfo deliveryInfo =
                new ImageAnalysisResponse.DeliveryRelevantInfo();
        deliveryInfo.setImageQuality("unknown");
        deliveryInfo.setHasText(false);
        deliveryInfo.setSuitableForDelivery(false);
        deliveryInfo.setAnalyzedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        response.setAnalysis(analysis);
        response.setDeliveryRelevantInfo(deliveryInfo);

        return response;
    }

    /**
     * إنشاء استجابة عند إيقاف الخدمة
     */
    private ImageAnalysisResponse createDisabledResponse() {
        ImageAnalysisResponse response = new ImageAnalysisResponse();
        response.setSuccess(true);
        response.setError("خدمة تحليل الصور معطلة");
        response.setTimestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        response.setImageAnalyzed(false);

        ImageAnalysisResponse.Analysis analysis = new ImageAnalysisResponse.Analysis();
        ImageAnalysisResponse.Analysis.TextExtraction textExtraction =
                new ImageAnalysisResponse.Analysis.TextExtraction();
        textExtraction.setFullText("خدمة تحليل الصور معطلة");
        analysis.setTextExtraction(textExtraction);

        ImageAnalysisResponse.DeliveryRelevantInfo deliveryInfo =
                new ImageAnalysisResponse.DeliveryRelevantInfo();
        deliveryInfo.setImageQuality("not_analyzed");
        deliveryInfo.setHasText(false);
        deliveryInfo.setSuitableForDelivery(false);
        deliveryInfo.setAnalyzedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        response.setAnalysis(analysis);
        response.setDeliveryRelevantInfo(deliveryInfo);

        return response;
    }

    /**
     * فحص حالة خدمة تحليل الصور
     */
    public boolean isImageServiceHealthy() {
        HealthCheckResult result = checkImageServiceHealth();
        return result.isHealthy();
    }

    /**
     * فحص مفصل لحالة خدمة تحليل الصور
     */
    public HealthCheckResult checkImageServiceHealth() {
        if (!enabled) {
            return new HealthCheckResult(false, "خدمة تحليل الصور معطلة في الإعدادات");
        }

        try {
            String url = apiUrl + "/health";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                boolean tesseractAvailable = (Boolean) body.getOrDefault("tesseract_available", false);
                String status = (String) body.getOrDefault("status", "unknown");

                if ("healthy".equals(status) && tesseractAvailable) {
                    return new HealthCheckResult(true, "خدمة تحليل الصور تعمل بشكل طبيعي");
                } else {
                    return new HealthCheckResult(false,
                            String.format("خدمة تحليل الصور متاحة لكن Tesseract لا يعمل (status: %s, tesseract: %s)",
                                    status, tesseractAvailable));
                }
            } else {
                return new HealthCheckResult(false, "خدمة تحليل الصور ترد برمز خطأ: " + response.getStatusCode());
            }
        } catch (Exception e) {
            String errorMessage = e.getMessage();
            if (errorMessage != null) {
                if (errorMessage.contains("Connection refused") || errorMessage.contains("ConnectException")) {
                    return new HealthCheckResult(false,
                            "لا يمكن الاتصال بخدمة تحليل الصور على " + apiUrl + " - تأكد من تشغيل Flask API");
                } else if (errorMessage.contains("timeout") || errorMessage.contains("SocketTimeoutException")) {
                    return new HealthCheckResult(false, "انتهت مهلة الاتصال بخدمة تحليل الصور");
                }
            }
            return new HealthCheckResult(false, "خطأ في الاتصال بخدمة تحليل الصور: " + errorMessage);
        }
    }

    /**
     * الحصول على معلومات التكوين
     */
    public String getConfigurationInfo() {
        return String.format(
                "Image Analysis Service Configuration:\n" +
                        "- Enabled: %s\n" +
                        "- API URL: %s\n" +
                        "- Timeout: %d ms\n" +
                        "- Connection Timeout: %d ms\n" +
                        "- Max Retries: %d\n" +
                        "- Retry Delay: %d ms\n" +
                        "- Supported Formats: %s\n" +
                        "- Max File Size: %d bytes (%.1f MB)\n" +
                        "- Quality Threshold: %.2f\n" +
                        "- Fallback on Error: %s",
                enabled, apiUrl, timeout, connectionTimeout, maxRetries, retryDelay,
                supportedFormats, maxFileSize, maxFileSize / (1024.0 * 1024.0), qualityThreshold, fallbackOnError
        );
    }

    /**
     * إعادة تشغيل الاتصال (إعادة إنشاء RestTemplate)
     */
    public void resetConnection() {
        logger.info("Resetting image analysis service connection...");
        // يمكن إضافة منطق إعادة تشغيل إذا لزم الأمر
    }

    /**
     * نتيجة فحص الحالة
     */
    public static class HealthCheckResult {
        private final boolean healthy;
        private final String message;
        private final long timestamp;

        public HealthCheckResult(boolean healthy, String message) {
            this.healthy = healthy;
            this.message = message;
            this.timestamp = System.currentTimeMillis();
        }

        public boolean isHealthy() {
            return healthy;
        }

        public String getMessage() {
            return message;
        }

        public long getTimestamp() {
            return timestamp;
        }

        @Override
        public String toString() {
            return String.format("HealthCheck{healthy=%s, message='%s', timestamp=%d}",
                    healthy, message, timestamp);
        }
    }
}