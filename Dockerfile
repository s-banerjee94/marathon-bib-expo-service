# Built for arm64 (the box is Graviton); CodeBuild must run an ARM image.
# Base images come from ECR Public, not Docker Hub, which rate-limits anonymous pulls.
FROM public.ecr.aws/docker/library/eclipse-temurin:17-jre-jammy AS extract
WORKDIR /build
COPY target/*.jar application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM public.ecr.aws/docker/library/eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN useradd --system --uid 1001 --no-create-home bibexpo

# Least to most likely to change: only the application layer is rebuilt and pushed per deploy.
COPY --from=extract /build/extracted/dependencies/ ./
COPY --from=extract /build/extracted/spring-boot-loader/ ./
COPY --from=extract /build/extracted/snapshot-dependencies/ ./
COPY --from=extract /build/extracted/application/ ./

USER 1001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "application.jar"]
