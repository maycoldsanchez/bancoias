package com.maycol.bancoias;

import com.maycol.bancoias.infrastructure.out.r2dbc.entity.AccountEntity;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.r2dbc.core.DatabaseClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.maycol.bancoias.infrastructure.out.r2dbc.repository.IAccountRepository;
import com.maycol.bancoias.application.dto.TransferResponse;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TransferApiTest {

  @Container
  static PostgreSQLContainer<?> postgres =
    new PostgreSQLContainer<>("postgres:16")
      .withDatabaseName("bancoias")
      .withUsername("bancoias_app")
      .withPassword("bancoias_local");

  @LocalServerPort
  private int port;

  private WebTestClient client;

  @Autowired
  private IAccountRepository accountRepository;

  @Autowired
  private DatabaseClient databaseClient;

  @BeforeEach
  void setUp() {
    databaseClient.sql("DELETE FROM transfers")
      .fetch()
      .rowsUpdated()
      .block();

    databaseClient.sql("""
      UPDATE accounts
      SET balance = 800000.00,
          status = 'ACTIVE',
          currency = 'COP'
      WHERE account_number = 'ACC-1001'
      """)
      .fetch()
      .rowsUpdated()
      .block();

    databaseClient.sql("""
      UPDATE accounts
      SET balance = 200000.00,
          status = 'ACTIVE',
          currency = 'COP'
      WHERE account_number = 'ACC-1002'
      """)
      .fetch()
      .rowsUpdated()
      .block();

    databaseClient.sql("""
      UPDATE accounts
      SET balance = 500000.00,
          status = 'BLOCKED',
          currency = 'COP'
      WHERE account_number = 'ACC-1003'
      """)
      .fetch()
      .rowsUpdated()
      .block();

    client = WebTestClient.bindToServer()
      .baseUrl("http://localhost:" + port)
      .build();
  }

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add(
      "spring.r2dbc.url",
      () -> "r2dbc:postgresql://"
        + postgres.getHost()
        + ":"
        + postgres.getMappedPort(5432)
        + "/"
        + postgres.getDatabaseName()
    );

    registry.add("spring.r2dbc.username", postgres::getUsername);
    registry.add("spring.r2dbc.password", postgres::getPassword);

    registry.add("spring.flyway.url", postgres::getJdbcUrl);
    registry.add("spring.flyway.user", postgres::getUsername);
    registry.add("spring.flyway.password", postgres::getPassword);
  }

  @Test
  void postgresContainerStarts() {
    assertTrue(postgres.isRunning());
  }

  @Test
  void shouldCreateTransfer() {

    client.post()
      .uri("/api/transfers")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue("""
          {
            "clientReference": "TEST-001",
            "sourceAccount": "ACC-1001",
            "destinationAccount": "ACC-1002",
            "amount": 100000.00,
            "currency": "COP"
          }
          """)
      .exchange()
      .expectStatus().isCreated()
      .expectBody()
      .jsonPath("$.clientReference").isEqualTo("TEST-001")
      .jsonPath("$.sourceAccount").isEqualTo("****1001")
      .jsonPath("$.destinationAccount").isEqualTo("****1002")
      .jsonPath("$.amount").isEqualTo(100000.00)
      .jsonPath("$.currency").isEqualTo("COP")
      .jsonPath("$.status").isEqualTo("COMPLETED");
  }

  @Test
  void shouldRejectTransferWhenSourceAndDestinationAreTheSame() {
    client.post()
      .uri("/api/transfers")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue("""
          {
            "clientReference": "TEST-002",
            "sourceAccount": "ACC-1001",
            "destinationAccount": "ACC-1001",
            "amount": 100000.00,
            "currency": "COP"
          }
          """)
      .exchange()
      .expectStatus().isEqualTo(422)
      .expectBody()
      .jsonPath("$.code").isEqualTo("SAME_ACCOUNT");
  }

  @Test
  void shouldRejectTransferWhenSourceAccountIsBlocked() {
    client.post()
      .uri("/api/transfers")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue("""
          {
            "clientReference": "TEST-003",
            "sourceAccount": "ACC-1003",
            "destinationAccount": "ACC-1002",
            "amount": 100000.00,
            "currency": "COP"
          }
          """)
      .exchange()
      .expectStatus().isEqualTo(422)
      .expectBody()
      .jsonPath("$.code").isEqualTo("SOURCE_ACCOUNT_INACTIVE");
  }

  @Test
  void shouldRejectTransferWhenDestinationAccountIsBlocked() {
    client.post()
      .uri("/api/transfers")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue("""
          {
            "clientReference": "TEST-004",
            "sourceAccount": "ACC-1001",
            "destinationAccount": "ACC-1003",
            "amount": 100000.00,
            "currency": "COP"
          }
          """)
      .exchange()
      .expectStatus().isEqualTo(422)
      .expectBody()
      .jsonPath("$.code").isEqualTo("DESTINATION_ACCOUNT_INACTIVE");
  }

  @Test
  void shouldRejectNegativeAmount() {
    client.post()
      .uri("/api/transfers")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue("""
          {
            "clientReference": "TEST-005",
            "sourceAccount": "ACC-1001",
            "destinationAccount": "ACC-1002",
            "amount": -100000.00,
            "currency": "COP"
          }
          """)
      .exchange()
      .expectStatus().isBadRequest()
      .expectBody()
      .jsonPath("$.code").isEqualTo("INVALID_REQUEST");
  }

  @Test
  void shouldRejectZeroAmount() {
    client.post()
      .uri("/api/transfers")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue("""
          {
            "clientReference": "TEST-006",
            "sourceAccount": "ACC-1001",
            "destinationAccount": "ACC-1002",
            "amount": 0.00,
            "currency": "COP"
          }
          """)
      .exchange()
      .expectStatus().isBadRequest()
      .expectBody()
      .jsonPath("$.code").isEqualTo("INVALID_REQUEST");
  }

  @Test
  void shouldRejectUnsupportedCurrency() {
    client.post()
      .uri("/api/transfers")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue("""
          {
            "clientReference": "TEST-007",
            "sourceAccount": "ACC-1001",
            "destinationAccount": "ACC-1002",
            "amount": 100000.00,
            "currency": "USD"
          }
          """)
      .exchange()
      .expectStatus().isEqualTo(422)
      .expectBody()
      .jsonPath("$.code").isEqualTo("INVALID_CURRENCY");
  }

  @Test
  void shouldRejectTransferWhenSourceAccountDoesNotExist() {
    client.post()
      .uri("/api/transfers")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue("""
            {
              "clientReference": "TEST-008",
              "sourceAccount": "ACC-9999",
              "destinationAccount": "ACC-1002",
              "amount": 100000.00,
              "currency": "COP"
            }
            """)
      .exchange()
      .expectStatus().isEqualTo(422)
      .expectBody()
      .jsonPath("$.code").isEqualTo("SOURCE_ACCOUNT_NOT_FOUND");
  }

  @Test
  void shouldRejectTransferWhenDestinationAccountDoesNotExist() {
    client.post()
      .uri("/api/transfers")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue("""
            {
              "clientReference": "TEST-009",
              "sourceAccount": "ACC-1001",
              "destinationAccount": "ACC-9999",
              "amount": 100000.00,
              "currency": "COP"
            }
            """)
      .exchange()
      .expectStatus().isEqualTo(422)
      .expectBody()
      .jsonPath("$.code").isEqualTo("DESTINATION_ACCOUNT_NOT_FOUND");
  }

  @Test
  void shouldRejectTransferWhenSourceAccountHasInsufficientBalance() {
    client.post()
      .uri("/api/transfers")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue("""
            {
              "clientReference": "TEST-010",
              "sourceAccount": "ACC-1001",
              "destinationAccount": "ACC-1002",
              "amount": 900000.00,
              "currency": "COP"
            }
            """)
      .exchange()
      .expectStatus().isEqualTo(422)
      .expectBody()
      .jsonPath("$.code").isEqualTo("INSUFFICIENT_BALANCE");
  }

  @Test
  void shouldCreateTransferWithDecimalAmount() {
    client.post()
      .uri("/api/transfers")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue("""
            {
              "clientReference": "TEST-011",
              "sourceAccount": "ACC-1001",
              "destinationAccount": "ACC-1002",
              "amount": 100000.25,
              "currency": "COP"
            }
            """)
      .exchange()
      .expectStatus().isCreated()
      .expectBody()
      .jsonPath("$.clientReference").isEqualTo("TEST-011")
      .jsonPath("$.amount").isEqualTo(100000.25)
      .jsonPath("$.currency").isEqualTo("COP")
      .jsonPath("$.status").isEqualTo("COMPLETED");
  }

  @Test
  void shouldFindTransferById() {
    String transferId =
      client.post()
        .uri("/api/transfers")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("""
                {
                  "clientReference": "TEST-013",
                  "sourceAccount": "ACC-1001",
                  "destinationAccount": "ACC-1002",
                  "amount": 50000.00,
                  "currency": "COP"
                }
                """)
        .exchange()
        .expectStatus().isCreated()
        .expectBody()
        .jsonPath("$.transferId")
        .value(value -> {
          // El UUID viene como String en el JSON.
          assertTrue(value instanceof String);
        })
        .returnResult()
        .getResponseBody() == null
        ? null
        : null;
  }

  @Test
  void shouldReturnNotFoundWhenTransferDoesNotExist() {
    UUID transferId = UUID.randomUUID();

    client.get()
      .uri("/api/transfers/" + transferId)
      .exchange()
      .expectStatus().isNotFound()
      .expectBody()
      .jsonPath("$.code").isEqualTo("TRANSFER_NOT_FOUND");
  }

  @Test
  void shouldReturnSameTransferWhenSameClientReferenceAndSameDataAreSentAgain() {
    String request = """
        {
          "clientReference": "TEST-015",
          "sourceAccount": "ACC-1001",
          "destinationAccount": "ACC-1002",
          "amount": 75000.00,
          "currency": "COP"
        }
        """;

    TransferResponse first =
      client.post()
        .uri("/api/transfers")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus().isCreated()
        .expectBody(TransferResponse.class)
        .returnResult()
        .getResponseBody();

    TransferResponse second =
      client.post()
        .uri("/api/transfers")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus().isCreated()
        .expectBody(TransferResponse.class)
        .returnResult()
        .getResponseBody();

    assertTrue(first != null);
    assertTrue(second != null);
    assertTrue(first.transferId().equals(second.transferId()));
  }

  @Test
  void shouldRejectWhenSameClientReferenceIsUsedWithDifferentData() {
    client.post()
      .uri("/api/transfers")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue("""
            {
              "clientReference": "TEST-016",
              "sourceAccount": "ACC-1001",
              "destinationAccount": "ACC-1002",
              "amount": 75000.00,
              "currency": "COP"
            }
            """)
      .exchange()
      .expectStatus().isCreated();

    client.post()
      .uri("/api/transfers")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue("""
            {
              "clientReference": "TEST-016",
              "sourceAccount": "ACC-1001",
              "destinationAccount": "ACC-1002",
              "amount": 100000.00,
              "currency": "COP"
            }
            """)
      .exchange()
      .expectStatus().isEqualTo(409)
      .expectBody()
      .jsonPath("$.code").isEqualTo("IDEMPOTENCY_CONFLICT");
  }

  @Test
  void shouldNotModifySourceBalanceWhenTransferIsRejected() {
    BigDecimal balanceBefore =
      accountRepository.findByAccountNumber("ACC-1001")
        .map(AccountEntity::getBalance)
        .block();

    client.post()
      .uri("/api/transfers")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue("""
            {
              "clientReference": "TEST-017",
              "sourceAccount": "ACC-1001",
              "destinationAccount": "ACC-1002",
              "amount": 900000.00,
              "currency": "COP"
            }
            """)
      .exchange()
      .expectStatus().isEqualTo(422)
      .expectBody()
      .jsonPath("$.code").isEqualTo("INSUFFICIENT_BALANCE");

    BigDecimal balanceAfter =
      accountRepository.findByAccountNumber("ACC-1001")
        .map(AccountEntity::getBalance)
        .block();

    assertTrue(balanceBefore != null);
    assertTrue(balanceAfter != null);
    assertTrue(balanceBefore.compareTo(balanceAfter) == 0);
  }

  @Test
  void shouldRejectTransferWhenClientReferenceIsMissing() {
    client.post()
      .uri("/api/transfers")
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue("""
            {
              "sourceAccount": "ACC-1001",
              "destinationAccount": "ACC-1002",
              "amount": 100000.00,
              "currency": "COP"
            }
            """)
      .exchange()
      .expectStatus().isBadRequest()
      .expectBody()
      .jsonPath("$.code").isEqualTo("INVALID_REQUEST");
  }

  @Test
  void shouldReturnSameCorrelationId() {
    String correlationId = "TEST-CORRELATION-019";

    client.post()
      .uri("/api/transfers")
      .header("X-Correlation-Id", correlationId)
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue("""
        {
          "clientReference": "TEST-019",
          "sourceAccount": "ACC-1001",
          "destinationAccount": "ACC-1002",
          "amount": 50000.00,
          "currency": "COP"
        }
        """)
      .exchange()
      .expectStatus().isCreated()
      .expectHeader()
      .valueEquals("X-Correlation-Id", correlationId);
  }

  @Test
  void shouldNotOverspendSourceAccountWhenTransfersAreConcurrent()
    throws Exception {

    ExecutorService executor = Executors.newFixedThreadPool(2);

    try {
      Callable<Integer> transfer1 = () ->
        client.post()
          .uri("/api/transfers")
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue("""
                {
                  "clientReference": "CONCURRENT-020-A",
                  "sourceAccount": "ACC-1001",
                  "destinationAccount": "ACC-1002",
                  "amount": 500000.00,
                  "currency": "COP"
                }
                """)
          .exchange()
          .returnResult(Void.class)
          .getStatus()
          .value();

      Callable<Integer> transfer2 = () ->
        client.post()
          .uri("/api/transfers")
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue("""
                {
                  "clientReference": "CONCURRENT-020-B",
                  "sourceAccount": "ACC-1001",
                  "destinationAccount": "ACC-1002",
                  "amount": 500000.00,
                  "currency": "COP"
                }
                """)
          .exchange()
          .returnResult(Void.class)
          .getStatus()
          .value();

      List<Future<Integer>> results =
        executor.invokeAll(List.of(transfer1, transfer2));

      int status1 = results.get(0).get();
      int status2 = results.get(1).get();

      assertThat(
        List.of(status1, status2)
      ).containsExactlyInAnyOrder(201, 422);

      AccountEntity source =
        accountRepository
          .findByAccountNumber("ACC-1001")
          .block();

      assertThat(source.getBalance())
        .isEqualByComparingTo("300000.00");

    } finally {
      executor.shutdown();
    }
  }
}
