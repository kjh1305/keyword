# Demo Project

Spring Boot 기반 키워드 분석 + 재고 관리 시스템

## 기술 스택

- **Java 17** / **Spring Boot 3.4.3**
- **빌드**: Gradle
- **ORM**: JPA + MyBatis
- **데이터베이스**: MySQL (운영), H2 (테스트)
- **캐시**: Redis
- **메시지 큐**: RabbitMQ
- **기타**: Lombok, Apache POI, Jsoup, Gson, Thymeleaf, Spring Security

## 프로젝트 구조

```
src/main/java/com/example/demo/
├── DemoApplication.java
├── api/
│   ├── keyword/                     # 키워드 분석 API
│   │   ├── work/                    #   작업 관리
│   │   ├── category/                #   카테고리 관리
│   │   ├── rank/                    #   랭킹 관리
│   │   ├── backup/                  #   백업 관리
│   │   └── apicount/                #   API 호출 카운트
│   ├── inventory/                   # 재고 관리 모듈
│   │   ├── stock/                   #   재고 핵심 (아래 상세 참조)
│   │   ├── product/                 #   제품 관리 (CRUD)
│   │   ├── order/                   #   발주/입고 관리 (FIFO)
│   │   ├── excel/                   #   엑셀 Import/Export
│   │   ├── log/                     #   활동 로그 (감사 추적)
│   │   ├── user/                    #   사용자/권한 관리
│   │   └── admin/                   #   관리자 기능
│   ├── status/                      # 상태 API
│   └── queue/                       # RabbitMQ
│       ├── producer/
│       └── consumer/
├── common/
│   ├── config/                      # 설정 (Redis, RabbitMQ, WebMvc, Security)
│   └── util/
├── advice/                          # 글로벌 예외 처리
└── controller/                      # 메인 컨트롤러

src/main/resources/templates/inventory/
├── layout.html                      # 공통 레이아웃 (사이드바, CSS/JS)
├── inventory-list.html              # 재고 현황 (메인 화면)
├── product-list.html                # 제품 관리
├── order-list.html                  # 발주/입고 관리
├── import.html                      # 엑셀 Import
├── log-list.html                    # 활동 로그
├── user-list.html                   # 사용자 관리
└── admin.html                       # 관리자 대시보드
```

## 빌드 및 실행

```bash
./gradlew build        # 빌드
./gradlew bootRun      # 실행
./gradlew test         # 테스트
./gradlew bootJar      # JAR 생성
```

## 설정 파일

- `application-dev.properties`: 개발 환경
- `application-prod.properties`: 운영 환경

---

## 재고 관리 모듈 상세

### 핵심 개념: 보고 기간(ReportPeriod) 시스템

재고 데이터는 **보고 기간** 단위로 관리된다. 기존 월 단위(`yearMonth = "yyyy-MM"`) 방식에서 유동적인 기간 기반으로 전환됨.

#### 기간 상태
- **OPEN**: 현재 진행중인 기간. 항상 1개만 존재
- **CONFIRMED**: 최종보고 완료된 확정 기간

#### 기간 전환 흐름 (최종보고완료 버튼)
```
1. 사용자가 최종보고일 입력 (예: 2026-02-26)
2. 현재 OPEN 기간 확정:
   - endDate = 보고일 - 1 (2026-02-25)
   - status = CONFIRMED
3. 새 OPEN 기간 생성:
   - startDate = 보고일 (2026-02-26)
   - name = 사용자 입력 (예: "3월 1차")
4. 이전 기간의 남은재고 → 새 기간의 월초재고로 이월
```

#### 핵심 규칙
- 최종보고일 당일은 **다음 기간**에 포함
- 기간 이름은 자유 형식 (예: "2026-03", "3월 1차", "2026-02 추가보고")
- 기간 생성은 수동(최종보고완료 버튼)으로만 가능 (자동 스케줄러 없음)

### 엔티티 관계

```
Product (1) ──── (N) Inventory ──── (N:1) ReportPeriod
    │
    ├──── (N) StockOrder (발주/입고, FIFO 관리)
    ├──── (N) UsageLog (사용량 이력: DEDUCT/RESTORE)
    └──── (N) ActivityLog (활동 로그)
```

### 주요 파일 및 역할

#### stock/ 디렉토리
| 파일 | 역할 |
|------|------|
| `ReportPeriod.java` | 보고 기간 엔티티 (id, name, startDate, endDate, status, confirmedAt) |
| `ReportPeriodRepository.java` | 기간 조회 (findOpenPeriod, findAllByOrderByStartDateDesc, findPreviousPeriod) |
| `ReportPeriodDTO.java` | 기간 DTO (displayLabel로 드롭다운 표시) |
| `Inventory.java` | 재고 엔티티. product(FK), yearMonth(호환용), reportPeriod(FK), initialStock, usedQuantity 등 |
| `InventoryRepository.java` | 재고 조회. 기간 기반 + yearMonth 기반 쿼리 모두 보유 |
| `InventoryService.java` | **핵심 비즈니스 로직** (아래 계산 공식 참조) |
| `InventoryController.java` | REST API + 화면 렌더링 |
| `InventoryDTO.java` | 재고 DTO (totalOrderQty, pendingStock, completedStock 등 동적 계산 필드 포함) |
| `UsageLog.java` | 사용량 이력 엔티티 (action: DEDUCT/RESTORE, quantity, beforeUsed, afterUsed) |
| `UsageLogRepository.java` | 사용량 조회 (기간 날짜 범위 기반) |
| `DataMigrationService.java` | 앱 시작 시 1회 마이그레이션 (yearMonth → ReportPeriod). 멱등성 보장 |

#### order/ 디렉토리
| 파일 | 역할 |
|------|------|
| `StockOrder.java` | 발주 엔티티 (PENDING → COMPLETED, FIFO용 remainingQuantity, consumed) |
| `StockOrderService.java` | 발주 처리, 입고완료 시 OPEN 기간 인벤토리에 반영 |
| `StockOrderRepository.java` | 날짜 범위 기반 주문수량/입고수량 합계 쿼리 |

#### excel/ 디렉토리
| 파일 | 역할 |
|------|------|
| `ExcelService.java` | 엑셀 Import/Export 핵심 로직. 기간 기반 + yearMonth 기반 메서드 모두 보유 |
| `ExcelController.java` | periodId 파라미터 우선, yearMonth 폴백 |

### 계산 공식 (화면 + 엑셀 동일)

```
월초재고 = DB이월값(initialStock) + 주문수량(입고대기+입고완료)
사용량  = UsageLog 기반 운영 사용량 (DEDUCT - RESTORE, 기간 날짜 범위)
남은재고 = 월초재고 - 사용량
```

#### 이월값 계산 (기간 확정 시)
```
새 기간 initialStock = 이전기간 initialStock + 이전기간 입고완료 - 이전기간 보고용사용량 - 이전기간 운영사용량
```

### FIFO 재고 차감/복원

- **차감(DEDUCT)**: 유효기간 빠른 순으로 StockOrder.remainingQuantity 감소, 0이면 consumed=true
- **복원(RESTORE)**: 유효기간 늦은 순으로 복구 (역FIFO)
- 차감/복원 시 UsageLog에 기록, Inventory.usedQuantity(보고용)는 변경하지 않음

### 엑셀 다운로드 종류

| 메서드 | 설명 | 헤더 예시 |
|--------|------|-----------|
| `exportToExcelByPeriod` | 선택 기간 1개 시트 | `(기간명) 피부 물품(보고용)` |
| `exportAllToExcel` | 전체 기간 시트별 | 기간별 시트 생성 (오래된 순) |
| `exportWeeklyReportByPeriod` | 당일 보고용 | `(기간명) 피부 재고현황(당일보고)` |
| `exportWeeklyBreakdownReportByPeriod` | 주간 보고용 | `(기간명) 피부 재고현황(주간)` |

### 데이터 마이그레이션 (DataMigrationService)

앱 시작 시 `@EventListener(ApplicationReadyEvent.class)`로 1회 실행:
1. `report_period` 테이블이 비어있으면 실행 (멱등성)
2. 기존 `yearMonth` 데이터 → CONFIRMED 기간 생성 (현재 월 제외)
3. 현재 월 OPEN 기간 생성
4. 기존 인벤토리에 `report_period_id` 연결
5. 이전 CONFIRMED 기간 → OPEN 기간으로 재고 이월
6. `TransactionTemplate`으로 트랜잭션 관리 (self-invocation 문제 방지)
7. 실패해도 앱 시작은 정상 진행 (try-catch)

### UI (inventory-list.html) 주요 기능

- **기간 드롭다운**: 전체 기간 목록 (OPEN은 "진행중" 표시)
- **월초재고**: 편집 가능한 input (DB이월값, 화면에는 +주문수량 포함 표시)
- **주문수량 컬럼**: 주문(파란배지) + 입고(초록텍스트) / 대기(노란텍스트) 2단 표시
- **사용량**: 운영 사용량 (UsageLog 합계)
- **남은재고**: 월초재고 - 사용량
- **최종보고완료 버튼**: ADMIN 권한만 표시, 최종보고일+새기간명 입력

### API 엔드포인트

| Method | URL | 설명 |
|--------|-----|------|
| GET | `/inventory/stocks?periodId=` | 재고 현황 (화면) |
| POST | `/api/inventory/stocks/confirm-period` | 기간 확정 |
| GET | `/api/inventory/stocks/periods` | 기간 목록 |
| GET | `/api/inventory/export/excel?periodId=` | 엑셀 다운로드 |
| GET | `/api/inventory/export/weekly-report?periodId=` | 당일보고 다운로드 |
| GET | `/api/inventory/export/weekly-breakdown?periodId=` | 주간보고 다운로드 |
| POST | `/api/inventory/import` | 엑셀 Import |
| POST | `/api/inventory/import/sheets` | 시트 목록 조회 |
| POST | `/api/inventory/import/parse` | 파일 분석 |

### 주의사항

- `Inventory.yearMonth` 컬럼은 호환성을 위해 유지 중 (삭제하지 않음)
- `Inventory` 테이블에 unique constraint: `(product_id, ym)` — 같은 제품+월에 중복 불가
- `Hibernate ddl-auto=update` 사용 — 엔티티 변경 시 자동 스키마 반영
- `@PostConstruct` + `@Transactional` 조합은 self-invocation 문제 발생 → `TransactionTemplate` 사용
- 엑셀 export 메서드는 기간 기반(`ByPeriod`)과 월 기반(레거시) 두 버전 공존

## 주요 의존성

- `spring-boot-starter-web`: REST API
- `spring-boot-starter-data-jpa`: JPA
- `spring-boot-starter-security`: 인증/인가
- `spring-boot-starter-thymeleaf`: 템플릿 엔진
- `mybatis-spring-boot-starter`: MyBatis
- `spring-boot-starter-data-redis`: Redis
- `spring-boot-starter-amqp`: RabbitMQ
- `spring-boot-starter-actuator`: 모니터링
- `apache-poi`: 엑셀 처리
