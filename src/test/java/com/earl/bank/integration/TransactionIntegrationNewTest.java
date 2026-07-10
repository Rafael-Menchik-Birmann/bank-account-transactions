package com.earl.bank.integration;

import com.earl.bank.dto.CreateAccountDTO;
import com.earl.bank.dto.CreateTransactionDTO;
import com.earl.bank.entity.Currency;
import com.earl.bank.entity.Direction;
import com.earl.bank.exception.InsufficientFundException;
import com.earl.bank.logging.AOPLogging;
import com.earl.bank.service.AccountService;
import com.earl.bank.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class TransactionIntegrationNewTest {

    @Autowired
    TransactionService transactionService;

    @Autowired
    AccountService accountService;

    @Autowired
    AOPLogging aopLoggingMock;

    @BeforeEach
    void setUp() {
        doNothing().when(aopLoggingMock).afterCreateTransaction(any(), any());
    }

    @Test
    @DisplayName("CT-INTEGRACAO-01: Saldo nao deve ser alterado apos falha por saldo insuficiente")
    void saldoNaoDeveSerAlteradoAposFalhaPorSaldoInsuficiente() {
        var accountDTO = new CreateAccountDTO();
        accountDTO.setCountry("Brasil");
        accountDTO.setCurrency(Set.of(Currency.EUR));
        accountDTO.setCustomerId("cliente-int-01");
        var account = accountService.createAccount(accountDTO);

        var deposito = new CreateTransactionDTO();
        deposito.setAccountId(account.getAccountId());
        deposito.setAmount(new BigDecimal("100.00"));
        deposito.setCurrency(Currency.EUR);
        deposito.setDirection(Direction.IN);
        deposito.setDescription("Deposito");
        transactionService.createTransaction(deposito);

        var saque = new CreateTransactionDTO();
        saque.setAccountId(account.getAccountId());
        saque.setAmount(new BigDecimal("150.00"));
        saque.setCurrency(Currency.EUR);
        saque.setDirection(Direction.OUT);
        saque.setDescription("Saque indevido");

        assertThrows(InsufficientFundException.class,
            () -> transactionService.createTransaction(saque));

        var contaAtualizada = accountService.getAccount(account.getAccountId());
        var saldoEUR = contaAtualizada.getBalances().stream()
            .filter(b -> b.getCurrency() == Currency.EUR)
            .findFirst()
            .orElseThrow();
        assertEquals(0, saldoEUR.getAmount().compareTo(new BigDecimal("100.00")));
    }

    @Test
    @DisplayName("CT-INTEGRACAO-02: Historico deve conter todas as transacoes e saldo deve ser consistente")
    void historicoDeveRefletirTodasAsTransacoes() {
        var accountDTO = new CreateAccountDTO();
        accountDTO.setCountry("Brasil");
        accountDTO.setCurrency(Set.of(Currency.EUR));
        accountDTO.setCustomerId("cliente-int-02");
        var account = accountService.createAccount(accountDTO);

        var deposito = new CreateTransactionDTO();
        deposito.setAccountId(account.getAccountId());
        deposito.setAmount(new BigDecimal("500.00"));
        deposito.setCurrency(Currency.EUR);
        deposito.setDirection(Direction.IN);
        deposito.setDescription("Deposito");
        transactionService.createTransaction(deposito);

        var saque = new CreateTransactionDTO();
        saque.setAccountId(account.getAccountId());
        saque.setAmount(new BigDecimal("200.00"));
        saque.setCurrency(Currency.EUR);
        saque.setDirection(Direction.OUT);
        saque.setDescription("Saque");
        var resultado = transactionService.createTransaction(saque);

        assertEquals(0, resultado.getBalance().getAmount().compareTo(new BigDecimal("300.00")));

        var historico = accountService.getTransactions(account.getAccountId(), (short) 0, (short) 10);
        assertEquals(2, historico.size());
    }

    @Test
    @DisplayName("CT-INTEGRACAO-03: Apenas um saque deve ser aprovado em operacoes concorrentes")
    void apenasUmSaqueDeveSerAprovadoEmConcorrencia() throws InterruptedException {
        var accountDTO = new CreateAccountDTO();
        accountDTO.setCountry("Brasil");
        accountDTO.setCurrency(Set.of(Currency.EUR));
        accountDTO.setCustomerId("cliente-int-03");
        var account = accountService.createAccount(accountDTO);

        var deposito = new CreateTransactionDTO();
        deposito.setAccountId(account.getAccountId());
        deposito.setAmount(new BigDecimal("100.00"));
        deposito.setCurrency(Currency.EUR);
        deposito.setDirection(Direction.IN);
        deposito.setDescription("Deposito inicial");
        transactionService.createTransaction(deposito);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger sucessos = new AtomicInteger(0);
        AtomicInteger falhas = new AtomicInteger(0);

        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    var saque = new CreateTransactionDTO();
                    saque.setAccountId(account.getAccountId());
                    saque.setAmount(new BigDecimal("80.00"));
                    saque.setCurrency(Currency.EUR);
                    saque.setDirection(Direction.OUT);
                    saque.setDescription("Saque concorrente");
                    transactionService.createTransaction(saque);
                    sucessos.incrementAndGet();
                } catch (Exception e) {
                    falhas.incrementAndGet();
                }
            });
        }

        latch.countDown();
        executor.shutdown();
        executor.awaitTermination(15, TimeUnit.SECONDS);

        assertEquals(1, sucessos.get(), "Apenas um saque deve ser aprovado");
        assertEquals(1, falhas.get(), "O outro saque deve falhar por saldo insuficiente");

        var contaFinal = accountService.getAccount(account.getAccountId());
        var saldoFinal = contaFinal.getBalances().stream()
            .filter(b -> b.getCurrency() == Currency.EUR)
            .findFirst()
            .orElseThrow();
        assertEquals(0, saldoFinal.getAmount().compareTo(new BigDecimal("20.00")));
    }
}
