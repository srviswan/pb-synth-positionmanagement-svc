package com.bank.esps.api.v1;

import com.bank.esps.api.v1.mapper.PositionV1Mapper;
import com.bank.esps.application.cdm.PositionV1Service;
import com.bank.esps.domain.cdm.repository.InMemoryBasketActivityRepository;
import com.bank.esps.domain.cdm.repository.InMemoryFinancialContractRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PositionV1ApiTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PositionV1Service service = new PositionV1Service(
                new InMemoryFinancialContractRepository(),
                new InMemoryBasketActivityRepository());
        PositionV1Mapper mapper = new PositionV1Mapper();
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ContractV1Controller(service, mapper),
                        new PositionV1Controller(service, mapper))
                .setControllerAdvice(new PositionV1ExceptionHandler())
                .setMessageConverters(converter)
                .build();
    }

    @Test
    void singleStockSwapAllocateDividendAndSettle() throws Exception {
        mockMvc.perform(post("/v1/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "contractId": "C-EQ-1",
                                  "productType": "SWAP",
                                  "currency": "USD",
                                  "accountId": "ACC1",
                                  "bookId": "EQ-BOOK",
                                  "underlier": {"type": "EQUITY", "identifier": "AAPL", "currency": "USD"}
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.product.productType").value("SWAP"))
                .andExpect(jsonPath("$.underlier.identifier").value("AAPL"));

        MvcResult allocated = mockMvc.perform(post("/v1/contracts/C-EQ-1/transactions")
                        .header("Idempotency-Key", "alloc-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tradeId": "H-1",
                                  "securityId": "AAPL",
                                  "quantity": 1000,
                                  "price": 50,
                                  "currency": "USD",
                                  "tradeDate": "2026-01-05",
                                  "settlementDate": "2026-01-07"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.positions[0].securityId").value("AAPL"))
                .andExpect(jsonPath("$.positions[0].direction").value("LONG"))
                .andExpect(jsonPath("$.positions[0].quantity").value(1000))
                .andExpect(jsonPath("$.positions[0].transactions.length()").value(1))
                .andExpect(jsonPath("$.positions[0].openLots.length()").value(1))
                .andReturn();

        mockMvc.perform(post("/v1/contracts/C-EQ-1/transactions")
                        .header("Idempotency-Key", "alloc-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tradeId": "H-1",
                                  "securityId": "AAPL",
                                  "quantity": 1000,
                                  "price": 50,
                                  "currency": "USD",
                                  "tradeDate": "2026-01-05"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positions.length()").value(1));

        String positionId = com.jayway.jsonpath.JsonPath.read(
                allocated.getResponse().getContentAsString(), "$.positions[0].positionId");

        mockMvc.perform(get("/v1/positions").param("contractId", "C-EQ-1")
                        .param("securityId", "AAPL").param("direction", "LONG"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positionId").value(positionId));

        mockMvc.perform(post("/v1/positions/{id}/corporate-actions/dividends", positionId)
                        .header("Idempotency-Key", "div-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dividendId": "DIV-1",
                                  "exDate": "2026-02-01",
                                  "payDate": "2026-02-15",
                                  "rate": 0.52,
                                  "currency": "USD",
                                  "action": "OPEN"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dividendOpenLots.length()").value(1))
                .andExpect(jsonPath("$.dividendOpenLots[0].amount").value(520.0));

        mockMvc.perform(post("/v1/positions/{id}/corporate-actions/dividends", positionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dividendId": "DIV-1",
                                  "action": "CLOSE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dividendOpenLots.length()").value(0))
                .andExpect(jsonPath("$.dividendClosingLots.length()").value(1));

        mockMvc.perform(post("/v1/positions/{id}/settlements", positionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tradeId": "H-1",
                                  "status": "SETTLED",
                                  "settledQuantity": 1000
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlements[0].status").value("SETTLED"));

        mockMvc.perform(get("/v1/positions/{id}/transactions", positionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void portfolioIndexCibAndCfdShareTheSameGrain() throws Exception {
        mockMvc.perform(post("/v1/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "contractId": "C-PORT",
                                  "productType": "SWAP",
                                  "currency": "USD",
                                  "accountId": "ACC1",
                                  "bookId": "EQ-BOOK",
                                  "underlier": {
                                    "type": "BASKET",
                                    "identifier": "TECH",
                                    "currency": "USD",
                                    "constituents": [
                                      {"identifier": "AAPL", "type": "EQUITY", "weight": 0.6, "currency": "USD"},
                                      {"identifier": "MSFT", "type": "EQUITY", "weight": 0.4, "currency": "USD"}
                                    ]
                                  }
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.product.productQualifier").value("EquityBasketSwap"));

        mockMvc.perform(post("/v1/contracts/C-PORT/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tradeId":"H-AAPL","securityId":"AAPL","quantity":100,"price":50,"currency":"USD","tradeDate":"2026-01-05"}
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/v1/contracts/C-PORT/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tradeId":"H-MSFT","securityId":"MSFT","quantity":40,"price":80,"currency":"USD","tradeDate":"2026-01-05"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/v1/contracts/C-PORT/positions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positions.length()").value(2))
                .andExpect(jsonPath("$.positions[*].securityId").value(containsInAnyOrder("AAPL", "MSFT")));

        mockMvc.perform(post("/v1/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "contractId": "C-IDX",
                                  "productType": "SWAP",
                                  "currency": "USD",
                                  "accountId": "ACC1",
                                  "underlier": {"type": "INDEX", "identifier": "SPX", "currency": "USD"}
                                }
                                """))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/v1/contracts/C-IDX/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tradeId":"H-IDX","securityId":"SPX","quantity":10,"price":5800,"currency":"USD","tradeDate":"2026-01-05"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.positions[0].product.underlier.type").value("INDEX"));

        mockMvc.perform(post("/v1/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "contractId": "C-CIB",
                                  "productType": "SWAP",
                                  "currency": "USD",
                                  "accountId": "ACC1",
                                  "underlier": {"type": "EQUITY", "identifier": "CIB123", "instrumentClass": "CIB", "identifierScheme": "CIB", "currency": "USD"}
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.underlier.instrumentClass").value("CIB"));
        mockMvc.perform(post("/v1/contracts/C-CIB/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tradeId":"H-CIB","securityId":"CIB123","quantity":25,"price":12,"currency":"USD","tradeDate":"2026-01-05"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.positions[0].product.underlier.instrumentClass").value("CIB"));

        mockMvc.perform(post("/v1/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "contractId": "C-CFD",
                                  "productType": "CFD",
                                  "currency": "USD",
                                  "accountId": "ACC1",
                                  "underlier": {"type": "EQUITY", "identifier": "AAPL", "currency": "USD"}
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.product.productType").value("CFD"));
        mockMvc.perform(post("/v1/contracts/C-CFD/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tradeId":"H-CFD","securityId":"AAPL","quantity":10,"price":100,"currency":"USD","tradeDate":"2026-01-05"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.positions[0].product.productType").value("CFD"));

        mockMvc.perform(get("/v1/contracts/missing"))
                .andExpect(status().isNotFound());
    }
}
