# 1단계: 빌드 스테이지 (Java 17과 Maven이 공식 지원되는 이미지 사용)
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# [중요] pom.xml만 먼저 복사하여 의존성 라이브러리를 먼저 다운로드 (레이어 캐싱 활용)
COPY pom.xml .
RUN mvn dependency:go-offline -B -Dfile.encoding=UTF-8

# 실제 소스 코드를 복사하고 로컬 테스트 없이 패키징 수행
COPY src ./src
RUN mvn package -DskipTests -B -Dfile.encoding=UTF-8 -Dproject.build.sourceEncoding=UTF-8

# 2단계: 실행 스테이지 (용량이 작은 JRE Alpine 환경으로 최종 패키징)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 빌드 스테이지에서 생성된 정식 JAR 파일만 추출하여 복사
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

# 메모리 누수 방지를 위한 JVM 옵션 세팅 (컨테이너 환경 최적화)
ENTRYPOINT ["java", "-jar", "-Dfile.encoding=UTF-8", "app.jar"]