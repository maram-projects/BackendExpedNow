# نستعمل Java 17
FROM openjdk:17-jdk-slim

# نحطو المجلد اللي بش نخدم فيه
WORKDIR /app

# ننسخو ملف JAR متاع application متاعنا
COPY target/*.jar app.jar

# نفتحو port 8080
EXPOSE 8080

# كيفاش نشغلو application
ENTRYPOINT ["java", "-jar", "app.jar"]