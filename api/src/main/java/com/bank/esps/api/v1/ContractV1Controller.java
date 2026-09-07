package com.bank.esps.api.v1;

import com.bank.esps.api.v1.dto.PositionV1Dtos.ContractRequest;
import com.bank.esps.api.v1.dto.PositionV1Dtos.ContractResponse;
import com.bank.esps.api.v1.dto.PositionV1Dtos.PositionListResponse;
import com.bank.esps.api.v1.dto.PositionV1Dtos.TransactionRequest;
import com.bank.esps.api.v1.mapper.PositionV1Mapper;
import com.bank.esps.application.cdm.PositionV1Service;
import com.bank.esps.application.cdm.PositionV1Service.UpsertResult;
import com.bank.esps.domain.cdm.basket.BasketActivity;
import com.bank.esps.domain.cdm.product.FinancialContract;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/contracts")
public class ContractV1Controller {

    private final PositionV1Service service;
    private final PositionV1Mapper mapper;

    public ContractV1Controller(PositionV1Service service, PositionV1Mapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<ContractResponse> upsert(@RequestBody ContractRequest request) {
        UpsertResult<FinancialContract> result = service.upsertContract(mapper.toCommand(request));
        HttpStatus status = result.existed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(mapper.toContract(result.value()));
    }

    @GetMapping("/{contractId}")
    public ContractResponse get(@PathVariable String contractId) {
        return mapper.toContract(service.getContract(contractId));
    }

    @GetMapping("/{contractId}/positions")
    public PositionListResponse positions(@PathVariable String contractId,
                                          @RequestParam(required = false) String include) {
        List<BasketActivity> activities = service.positionsForContract(contractId);
        return mapper.toPositionList(contractId, activities, include);
    }

    @PostMapping("/{contractId}/transactions")
    public ResponseEntity<PositionListResponse> allocate(@PathVariable String contractId,
                                                         @RequestBody TransactionRequest request,
                                                         @RequestHeader(value = "Idempotency-Key", required = false)
                                                         String idempotencyKey) {
        UpsertResult<List<BasketActivity>> result =
                service.allocate(contractId, mapper.toCommand(request), idempotencyKey);
        HttpStatus status = result.existed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status)
                .body(mapper.toPositionList(contractId, result.value(), "transactions,lots,settlements,dividends"));
    }
}
