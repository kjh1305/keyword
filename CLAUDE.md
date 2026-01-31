# Demo Project

Spring Boot 기반 키워드 분석 API 서버

## 기술 스택

- **Java 17** / **Spring Boot 3.4.3**
- **빌드**: Gradle
- **ORM**: JPA + MyBatis
- **데이터베이스**: MySQL (운영), H2 (테스트)
- **캐시**: Redis
- **메시지 큐**: RabbitMQ
- **기타**: Lombok, Apache POI, Jsoup, Gson

## 프로젝트 구조

```
src/main/java/com/example/demo/
├── DemoApplication.java          # 메인 애플리케이션
├── api/
│   ├── keyword/                  # 키워드 관련 API
│   │   ├── work/                 # 작업 관리
│   │   ├── category/             # 카테고리 관리
│   │   ├── rank/                 # 랭킹 관리
│   │   ├── backup/               # 백업 관리
│   │   └── apicount/             # API 호출 카운트
│   ├── status/                   # 상태 API
│   └── queue/                    # RabbitMQ
│       ├── producer/             # 메시지 발행
│       └── consumer/             # 메시지 소비
├── common/
│   ├── config/                   # 설정 (Redis, RabbitMQ, WebMvc)
│   └── util/                     # 유틸리티
├── advice/                       # 글로벌 예외 처리
└── controller/                   # 메인 컨트롤러
```

## 빌드 및 실행

```bash
# 빌드
./gradlew build

# 실행
./gradlew bootRun

# 테스트
./gradlew test

# JAR 생성
./gradlew bootJar
```

## 설정 파일

- `application-dev.properties`: 개발 환경
- `application-prod.properties`: 운영 환경

## 주요 의존성

- `spring-boot-starter-web`: REST API
- `spring-boot-starter-data-jpa`: JPA
- `mybatis-spring-boot-starter`: MyBatis
- `spring-boot-starter-data-redis`: Redis
- `spring-boot-starter-amqp`: RabbitMQ
- `spring-boot-starter-actuator`: 모니터링
