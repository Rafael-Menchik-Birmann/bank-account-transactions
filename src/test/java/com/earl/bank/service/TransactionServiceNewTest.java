package com.earl.bank.service;

import com.earl.bank.dto.CreateAccountDTO;
import com.earl.bank.dto.CreateTransactionDTO;
import com.earl.bank.entity.Currency;
import com.earl.bank.entity.Direction;
import com.earl.bank.exception.InvalidAmountException;
import com.earl.bank.logging.AOPLogging;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class TransactionServiceNewTest {

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
    @DisplayName("CT-UNITARIO-01: Saque com valor exatamente igual ao saldo disponivel nao deve lancar excecao")
    void devePermitirSaqueComValorExatamenteIgualAoSaldo() {
        var accountDTO = new CreateAccountDTO();
        accountDTO.setCountry("Brasil");
        accountDTO.setCurrency(Set.of(Currency.EUR));
        accountDTO.setCustomerId("cliente-01");
        var account = accountService.createAccount(accountDTO);

        var deposito = new CreateTransactionDTO();
        deposito.setAccountId(account.getAccountId());
        deposito.setAmount(new BigDecimal("100.00"));
        deposito.setCurrency(Currency.EUR);
        deposito.setDirection(Direction.IN);
        deposito.setDescription("Deposito inicial");
        transactionService.createTransaction(deposito);

        var saque = new CreateTransactionDTO();
        saque.setAccountId(account.getAccountId());
        saque.setAmount(new BigDecimal("100.00"));
        saque.setCurrency(Currency.EUR);
        saque.setDirection(Direction.OUT);
        saque.setDescription("Saque total");

        var resultado = assertDoesNotThrow(() -> transactionService.createTransaction(saque));
        assertNotNull(resultado);
        assertEquals(0, resultado.getBalance().getAmount().compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("CT-UNITARIO-02: Transacao com valor zero deve lancar InvalidAmountException")
    void deveRejeitarTransacaoComValorZero() {
        var accountDTO = new CreateAccountDTO();
        accountDTO.setCountry("Brasil");
        accountDTO.setCurrency(Set.of(Currency.EUR));
        accountDTO.setCustomerId("cliente-02");
        var account = accountService.createAccount(accountDTO);

        var transactionDTO = new CreateTransactionDTO();
        transactionDTO.setAccountId(account.getAccountId());
        transactionDTO.setAmount(BigDecimal.ZERO);
        transactionDTO.setCurrency(Currency.EUR);
        transactionDTO.setDirection(Direction.IN);
        transactionDTO.setDescription("Transacao com zero");

        assertThrows(
            InvalidAmountException.class,
            () -> transactionService.createTransaction(transactionDTO),
            "Valor zero deveria ser rejeitado como InvalidAmountException"
        );
    }

    @Test
    @DisplayName("CT-UNITARIO-03: Transacao com descricao nula deve ser rejeitada ou tratada sem NullPointerException")
    void deveRejeitarOuTratarDescricaoNula() {
        var accountDTO = new CreateAccountDTO();
        accountDTO.setCountry("Brasil");
        accountDTO.setCurrency(Set.of(Currency.EUR));
        accountDTO.setCustomerId("cliente-03");
        var account = accountService.createAccount(accountDTO);

        var transactionDTO = new CreateTransactionDTO();
        transactionDTO.setAccountId(account.getAccountId());
        transactionDTO.setAmount(new BigDecimal("50.00"));
        transactionDTO.setCurrency(Currency.EUR);
        transactionDTO.setDirection(Direction.IN);
        transactionDTO.setDescription(null);

        assertThrows(
            Exception.class,
            () -> transactionService.createTransaction(transactionDTO),
            "Descricao nula deve ser rejeitada pelo sistema"
        );
    }
}
