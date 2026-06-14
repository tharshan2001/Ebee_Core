# Testing Guide

## Overview

Ebee uses a **layered testing strategy**:

| Layer | Framework | Speed | Database | Run with |
|-------|-----------|-------|----------|----------|
| **Unit** (services, utils) | JUnit 5 + Mockito | ~5s | None | `mvn test` |
| **Controller** (`@WebMvcTest`) | Spring MockMvc | ~5s | None | `mvn test` |
| **Repository** (`@DataJpaTest`, planned) | Testcontainers | ~30s | PostgreSQL 16 | `mvn test` |
| **Integration** (`@SpringBootTest`, planned) | Testcontainers | ~60s | PostgreSQL 16 | `mvn test` |

Service tests mock all dependencies and run in milliseconds. Controller tests slice the web layer. Repository and integration tests use Testcontainers for real PostgreSQL fidelity.

---

## Running Tests

```bash
# All tests (unit + controller)
mvn test

# Single test class
mvn test -Dtest="OrderServiceTest"

# Multiple classes
mvn test -Dtest="AuthControllerTest,ExpenseControllerTest"

# Single method
mvn test -Dtest="OrderServiceTest#createOrder_asAdmin_returns201"

# Tests + JaCoCo coverage check
mvn verify

# Skip tests
mvn install -DskipTests
```

---

## Project Structure

```
src/test/
├── java/com/ebee/
│   ├── AbstractIntegrationTest.java       # Base class for integration tests
│   ├── auth/
│   │   ├── controller/AuthControllerTest.java
│   │   └── service/
│   │       ├── AuthServiceTest.java
│   │       └── JwtServiceTest.java
│   ├── commission/service/
│   │   ├── CommissionCalculationServiceTest.java
│   │   └── CommissionLifecycleServiceTest.java
│   ├── common/util/
│   │   ├── DateUtilsTest.java
│   │   ├── PasswordGeneratorTest.java
│   │   └── PhoneUtilsTest.java
│   ├── delivery/service/DeliveryServiceTest.java
│   ├── expense/
│   │   ├── controller/ExpenseControllerTest.java
│   │   └── service/ExpenseServiceTest.java
│   ├── inventory/service/InventoryServiceTest.java
│   └── order/service/OrderServiceTest.java
└── resources/
    └── application-test.yml              # Testcontainers + overrides
```

---

## Unit Tests (Service Layer)

### Pattern

Every service test extends no Spring context — pure JUnit 5 + Mockito:

```java
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private SecurityService securityService;
    @Mock private InventoryService inventoryService;
    // ... all dependencies mocked

    @InjectMocks private OrderService orderService;

    @Captor private ArgumentCaptor<Order> orderCaptor;

    private User adminUser;
    private Order order;

    @BeforeEach
    void setUp() {
        adminUser = User.builder()
                .id(UUID.randomUUID())
                .role(RoleType.ADMIN)
                .build();
        order = Order.builder()
                .id(UUID.randomUUID())
                .status(OrderStatus.PENDING)
                .build();
    }

    @Test
    void createOrder_asAdmin_returnsOrderResponse() {
        when(securityService.getCurrentUser()).thenReturn(adminUser);
        when(orderRepository.save(any())).thenReturn(order);

        OrderResponse result = orderService.createOrder(orderRequest);

        assertThat(result).isNotNull();
        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void cancelOrder_alreadyCancelled_throwsException() {
        order.setStatus(OrderStatus.CANCELLED);
        when(orderRepository.findById(any())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(order.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already cancelled");
    }
}
```

### Conventions

1. **Mock all dependencies** — never use `@SpringBootTest` for service tests
2. **`@InjectMocks` on the service** under test
3. **`@Mock` on every collaborator** — the compiler helps you spot missing mocks
4. **`@Captor` for `ArgumentCaptor`** when verifying saved/updated entities
5. **Set up entities eagerly** — `open-in-view: false` means no lazy loading outside transactions
6. **Test both happy path and error cases** — use `assertThatThrownBy` for exceptions
7. **Use AssertJ** (`assertThat`) over JUnit assertions (more fluent, better diffs)
8. **Naming**: `{method}_{scenario}_{expectedResult}` — e.g. `cancelOrder_alreadyCancelled_throwsException`

---

## Controller Tests (`@WebMvcTest`)

### Pattern

Controller tests slice the web layer and mock the service layer:

```java
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private AuthService authService;
    @MockBean private JwtService jwtService;
    @MockBean private UserDetailsServiceImpl userDetailsService;
    @MockBean private RateLimitingService rateLimitingService;

    @BeforeEach
    void setUp() {
        when(rateLimitingService.isIpAllowed(anyString())).thenReturn(true);
    }

    @Test
    void login_withValidCredentials_returns200() throws Exception {
        when(authService.login(any())).thenReturn(AuthResponse.builder()
                .token("access-token")
                .refreshToken("refresh-token")
                .email("admin@ebee.com")
                .build());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void register_asAdmin_returns201() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());
    }
}
```

### Key Setup

| Annotation | Purpose |
|-----------|---------|
| `@WebMvcTest(Controller.class)` | Slice: only web layer, no full context |
| `@Import(SecurityConfig.class)` | Production security filter chain (permit rules, JWT filter, rate limiter) |
| `@MockBean` | All beans that `SecurityConfig` needs: `JwtService`, `UserDetailsServiceImpl`, `RateLimitingService` |
| `@MockBean` | The controller's service dependency (`AuthService`, `ExpenseService`, etc.) |
| `@BeforeEach` | Stub `rateLimitingService.isIpAllowed()` to return `true` |
| `@WithMockUser(roles = "ADMIN")` | Set authenticated user for role-based tests |

### Testing `@PreAuthorize` Rules

```java
// Allowed role → expect 2xx
@WithMockUser(roles = "ADMIN")
void approveExpense_asAdmin_returns200() throws Exception { ... }

// Denied role → expect 403
@WithMockUser(roles = "AGENT")
void approveExpense_asNonAdmin_returns403() throws Exception { ... }

// Public endpoint → no @WithMockUser needed
void forgotPassword_publicEndpoint_returns200() throws Exception { ... }
```

### Multipart Requests

When the controller uses `@RequestPart`, send the data as a `MockMultipartFile` (not `.param()`):

```java
MockMultipartFile expensePart = new MockMultipartFile(
        "expense",                          // part name
        "",                                 // original filename
        "application/json",                 // content type
        objectMapper.writeValueAsBytes(request));

mockMvc.perform(multipart("/api/expenses")
                .file(expensePart)
                .file("receipt", new byte[0]))
        .andExpect(status().isCreated());
```

---

## Utility Tests

### Parameterized Tests

Use `@CsvSource` for testing multiple input/output pairs:

```java
@ParameterizedTest
@CsvSource({
    "'+94771234567', '0771234567'",
    "'0771234567', '0771234567'",
    "null, null",
})
void normalize_returnsExpected(String input, String expected) {
    String result = PhoneUtils.normalize("null".equals(input) ? null : input);
    if ("null".equals(input)) {
        assertThat(result).isNull();
    } else {
        assertThat(result).isEqualTo(expected);
    }
}
```

### Avoiding Flaky Tests

For classes using random generation (e.g. `PasswordGenerator`), test the **properties of the output** rather than specific values:

```java
@Test
void generatedPassword_containsOnlyValidChars() {
    String password = PasswordGenerator.generate();
    assertThat(password).containsPattern("^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]+$");
}

@Test
void generatedPassword_meetsLengthRequirement() {
    assertThat(PasswordGenerator.generate()).hasSize(16);
}

@Test
void generatedPassword_containsAtLeastOneLetter() {
    assertThat(PasswordGenerator.generate()).containsPattern("[a-zA-Z]");
}
```

---

## Integration Tests (`@SpringBootTest` with Testcontainers)

### Base Class

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ebee_test")
            .withReuse(true);

    static {
        postgres.start();
        System.setProperty("spring.datasource.url", postgres.getJdbcUrl());
        System.setProperty("spring.datasource.username", postgres.getUsername());
        System.setProperty("spring.datasource.password", postgres.getPassword());
    }
}
```

Subclasses get a full Spring context with a real PostgreSQL container.

### Application Test Profile

`src/test/resources/application-test.yml` provides overrides for:
- **Datasource**: Testcontainers JDBC URL (`jdbc:tc:postgresql:16-alpine:///ebee_test`)
- **Redis**: localhost:16379 (matching CI service container)
- **Mail**: localhost:3025 (matching FakeSMTP or greenmail)
- **JWT**: fixed test secrets
- **Rate limiting**: relaxed limits (10000 IP req/min)
- **AWS S3**: fake credentials

---

## JaCoCo Coverage

### Configuration

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <executions>
        <execution>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>verify</phase>
            <goals><goal>report</goal></goals>
        </execution>
        <execution>
            <id>check</id>
            <phase>verify</phase>
            <goals><goal>check</goal></goals>
            <configuration>
                <rules>
                    <rule>
                        <element>BUNDLE</element>
                        <limits>
                            <limit>
                                <counter>INSTRUCTION</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.10</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
                <excludes>
                    <exclude>com/ebee/**/dto/*</exclude>
                    <exclude>com/ebee/**/entity/*</exclude>
                    <exclude>com/ebee/**/enums/*</exclude>
                    <exclude>com/ebee/**/config/*</exclude>
                    <exclude>com/ebee/common/response/*</exclude>
                    <exclude>com/ebee/EbeeApplication.class</exclude>
                </excludes>
            </configuration>
        </execution>
    </executions>
</plugin>
```

Run `mvn verify` to generate the report at `target/site/jacoco/index.html`.

---

## CI (GitHub Actions)

```yaml
name: CI
on:
  push: { branches: [main, develop] }
  pull_request: { branches: [main] }

jobs:
  build:
    runs-on: ubuntu-latest
    services:
      redis:
        image: redis:7-alpine
        ports: [16379:6379]
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: 21
          distribution: temurin
          cache: maven
      - run: mvn verify --batch-mode --no-transfer-progress
      - uses: actions/upload-artifact@v4
        if: success() || failure()
        with:
          name: jacoco-report
          path: target/site/jacoco/
```

Redis on port 16379 matches the `application-test.yml` Redis config. The PostgreSQL container is managed by Testcontainers (no service container needed).

---

## Test Count (Current: 143)

| Test Class | Count |
|-----------|-------|
| AuthServiceTest | 10 |
| JwtServiceTest | 11 |
| CommissionCalculationServiceTest | 8 |
| CommissionLifecycleServiceTest | 4 |
| DateUtilsTest | 4 |
| PasswordGeneratorTest | 3 |
| PhoneUtilsTest | 8 |
| InventoryServiceTest | 11 |
| OrderServiceTest | 18 |
| DeliveryServiceTest | 13 |
| ExpenseServiceTest | 10 |
| AuthControllerTest | 4 |
| ExpenseControllerTest | 10 |
| AbstractIntegrationTest | 0 (base) |
| **Total** | **143** |

---

## Guidelines

1. **No `@SpringBootTest` in service tests** — mock everything for sub-second execution
2. **One assertion concept per test method** — verify the status _or_ the body _or_ an interaction, not all three
3. **Use `.andDo(print())` sparingly** — it slows tests and clutters output; remove before commit
4. **Test both sides of every branch** — happy path + error path, authorized + denied role
5. **Use `@Captor` to verify saved entities** — confirms the exact object passed to the repository
6. **Keep mocks in `@BeforeEach` when shared** — per-test stubs in individual `@Test` methods
7. **Test `@PreAuthorize` with multiple roles** — at least one allowed and one denied per secured endpoint
8. **Don't test the framework** — verify your business logic, not that `@Valid` works
9. **Use AssertJ** over `assertEquals`, `assertTrue`, etc.
10. **Name tests descriptively** — `{method}_{scenario}_{expected}` makes failures immediately understandable
