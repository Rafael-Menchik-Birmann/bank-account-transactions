package com.earl.bank.integration;

import com.earl.bank.dto.CreateAccountDTO;
import com.earl.bank.dto.CreateTransactionDTO;
import com.earl.bank.entity.Account;
import com.earl.bank.entity.Currency;
import com.earl.bank.entity.Direction;
import com.earl.bank.logging.AOPLogging;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class TransactionSystemNewTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    AOPLogging aopLoggingMock;

    @BeforeEach
    void setUp() {
        doNothing().when(aopLoggingMock).afterCreateTransaction(any(), any());
        doNothing().when(aopLoggingMock).afterCreateAccount(any(), any());
    }

    @Test
    @DisplayName("CT-SISTEMA-01: Saque superior ao saldo deve retornar HTTP 4xx e saldo deve permanecer inalterado")
    void deveRetornarErroQuandoSaldoInsuficiente() throws Exception {
        var accountDTO = new CreateAccountDTO();
        accountDTO.setCustomerId("sys-cliente-01");
        accountDTO.setCountry("Brasil");
        accountDTO.setCurrency(Set.of(Currency.EUR));

        MvcResult criacao = mvc.perform(post("/account")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(accountDTO)))
                .andExpect(status().isCreated())
                .andReturn();

        Account account = objectMapper.readValue(
            criacao.getResponse().getContentAsString(), Account.class);

        var deposito = new CreateTransactionDTO();
        deposito.setAccountId(account.getAccountId());
        deposito.setAmount(new BigDecimal("100.00"));
        deposito.setCurrency(Currency.EUR);
        deposito.setDirection(Direction.IN);
        deposito.setDescription("Deposito");

        mvc.perform(post("/transaction")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(deposito)))
                .andExpect(status().isCreated());

        var saque = new CreateTransactionDTO();
        saque.setAccountId(account.getAccountId());
        saque.setAmount(new BigDecimal("150.00"));
        saque.setCurrency(Currency.EUR);
        saque.setDirection(Direction.OUT);
        saque.setDescription("Saque indevido");

        mvc.perform(post("/transaction")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(saque)))
                .andExpect(status().is4xxClientError());

        mvc.perform(get("/account/" + account.getAccountId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balances[0].amount").value(100.0));
    }

    @Test
    @DisplayName("CT-SISTEMA-02: Fluxo completo de deposito e saque deve refletir saldo e historico corretos")
    void deveProcessarDepositoESaqueEExibirExtrato() throws Exception {
        var accountDTO = new CreateAccountDTO();
        accountDTO.setCustomerId("sys-cliente-02");
        accountDTO.setCountry("Brasil");
        accountDTO.setCurrency(Set.of(Currency.EUR));

        MvcResult criacao = mvc.perform(post("/account")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(accountDTO)))
                .andExpect(status().isCreated())
                .andReturn();

        Account account = objectMapper.readValue(
            criacao.getResponse().getContentAsString(), Account.class);

        var deposito = new CreateTransactionDTO();
        deposito.setAccountId(account.getAccountId());
        deposito.setAmount(new BigDecimal("1000.00"));
        deposito.setCurrency(Currency.EUR);
        deposito.setDirection(Direction.IN);
        deposito.setDescription("Deposito");

        mvc.perform(post("/transaction")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(deposito)))
                .andExpect(status().isCreated());

        var saque = new CreateTransactionDTO();
        saque.setAccountId(account.getAccountId());
        saque.setAmount(new BigDecimal("300.00"));
        saque.setCurrency(Currency.EUR);
        saque.setDirection(Direction.OUT);
        saque.setDescription("Saque");

        mvc.perform(post("/transaction")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(saque)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balance.amount").value(700.0));
        mvc.perform(get("/transaction/" + account.getAccountId() + "/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("CT-SISTEMA-03: Deposito em EUR nao deve alterar saldo em GBP")
    void saldoEmMoedasDiferentesDeveSerIndependente() throws Exception {
        var accountDTO = new CreateAccountDTO();
        accountDTO.setCustomerId("sys-cliente-03");
        accountDTO.setCountry("Brasil");
        accountDTO.setCurrency(Set.of(Currency.EUR, Currency.GBP));

        MvcResult criacao = mvc.perform(post("/account")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(accountDTO)))
                .andExpect(status().isCreated())
                .andReturn();

        Account account = objectMapper.readValue(
            criacao.getResponse().getContentAsString(), Account.class);

        var deposito = new CreateTransactionDTO();
        deposito.setAccountId(account.getAccountId());
        deposito.setAmount(new BigDecimal("100.00"));
        deposito.setCurrency(Currency.EUR);
        deposito.setDirection(Direction.IN);
        deposito.setDescription("Deposito EUR");

        mvc.perform(post("/transaction")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(deposito)))
                .andExpect(status().isCreated());

        MvcResult consulta = mvc.perform(get("/account/" + account.getAccountId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balances").isArray())
                .andExpect(jsonPath("$.balances.length()").value(2))
                .andReturn();

        String body = consulta.getResponse().getContentAsString();
        Account contaAtualizada = objectMapper.readValue(body, Account.class);

        var saldoEUR = contaAtualizada.getBalances().stream()
                .filter(b -> b.getCurrency() == Currency.EUR)
                .findFirst().orElseThrow();
        var saldoGBP = contaAtualizada.getBalances().stream()
                .filter(b -> b.getCurrency() == Currency.GBP)
                .findFirst().orElseThrow();

        assertEquals(0, saldoEUR.getAmount().compareTo(new BigDecimal("100.00")),
                "Saldo EUR deve ser 100.00");
        assertEquals(0, saldoGBP.getAmount().compareTo(BigDecimal.ZERO),
                "Saldo GBP deve permanecer 0.00");
    }
}
