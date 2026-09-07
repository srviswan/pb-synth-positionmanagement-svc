package com.bank.esps.api.v1;

import com.bank.esps.api.v1.dto.PositionV1Dtos.ClosingLotDto;
import com.bank.esps.api.v1.dto.PositionV1Dtos.DividendClosingLotDto;
import com.bank.esps.api.v1.dto.PositionV1Dtos.DividendOpenLotDto;
import com.bank.esps.api.v1.dto.PositionV1Dtos.DividendRequest;
import com.bank.esps.api.v1.dto.PositionV1Dtos.OpenLotDto;
import com.bank.esps.api.v1.dto.PositionV1Dtos.PositionResponse;
import com.bank.esps.api.v1.dto.PositionV1Dtos.SettlementDto;
import com.bank.esps.api.v1.dto.PositionV1Dtos.SettlementRequest;
import com.bank.esps.api.v1.dto.PositionV1Dtos.TransactionDto;
import com.bank.esps.api.v1.mapper.PositionV1Mapper;
import com.bank.esps.application.cdm.PositionV1Service;
import com.bank.esps.domain.cdm.basket.BasketActivity;
import com.bank.esps.domain.cdm.basket.DividendClosingLot;
import com.bank.esps.domain.cdm.basket.DividendOpenLot;
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
@RequestMapping("/v1/positions")
public class PositionV1Controller {

    private final PositionV1Service service;
    private final PositionV1Mapper mapper;

    public PositionV1Controller(PositionV1Service service, PositionV1Mapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    public PositionResponse findByNaturalKey(@RequestParam String contractId,
                                             @RequestParam String securityId,
                                             @RequestParam String direction,
                                             @RequestParam(required = false) String include) {
        BasketActivity activity = service.findByNaturalKey(
                contractId, securityId, PositionV1Service.directionOf(direction));
        return mapper.toPosition(activity, include);
    }

    @GetMapping("/{positionId}")
    public PositionResponse get(@PathVariable String positionId,
                                @RequestParam(required = false) String include) {
        return mapper.toPosition(service.getPosition(positionId), include);
    }

    @GetMapping("/{positionId}/transactions")
    public List<TransactionDto> transactions(@PathVariable String positionId) {
        return service.getPosition(positionId).getDetails().stream().map(mapper::toTransaction).toList();
    }

    @GetMapping("/{positionId}/open-lots")
    public List<OpenLotDto> openLots(@PathVariable String positionId) {
        return service.getPosition(positionId).getOpenLots().stream().map(mapper::toOpenLot).toList();
    }

    @GetMapping("/{positionId}/closing-lots")
    public List<ClosingLotDto> closingLots(@PathVariable String positionId) {
        return service.getPosition(positionId).getClosingLots().stream().map(mapper::toClosingLot).toList();
    }

    @GetMapping("/{positionId}/settlements")
    public List<SettlementDto> settlements(@PathVariable String positionId) {
        return service.getPosition(positionId).getSettlements().stream().map(mapper::toSettlement).toList();
    }

    @PostMapping("/{positionId}/settlements")
    public PositionResponse applySettlement(@PathVariable String positionId,
                                            @RequestBody SettlementRequest request,
                                            @RequestHeader(value = "Idempotency-Key", required = false)
                                            String idempotencyKey) {
        BasketActivity activity = service.applySettlement(positionId, mapper.toInstruction(request), idempotencyKey);
        return mapper.toPosition(activity, "transactions,lots,settlements,dividends");
    }

    @GetMapping("/{positionId}/dividend-open-lots")
    public List<DividendOpenLotDto> dividendOpenLots(@PathVariable String positionId) {
        List<DividendOpenLot> lots = service.getPosition(positionId).getDividendOpenLots();
        if (lots == null) {
            return List.of();
        }
        return lots.stream().map(mapper::toDividendOpen).toList();
    }

    @GetMapping("/{positionId}/dividend-closing-lots")
    public List<DividendClosingLotDto> dividendClosingLots(@PathVariable String positionId) {
        List<DividendClosingLot> lots = service.getPosition(positionId).getDividendClosingLots();
        if (lots == null) {
            return List.of();
        }
        return lots.stream().map(mapper::toDividendClose).toList();
    }

    @PostMapping("/{positionId}/corporate-actions/dividends")
    public PositionResponse applyDividend(@PathVariable String positionId,
                                          @RequestBody DividendRequest request,
                                          @RequestHeader(value = "Idempotency-Key", required = false)
                                          String idempotencyKey) {
        BasketActivity activity = service.applyDividend(positionId, mapper.toInstruction(request), idempotencyKey);
        return mapper.toPosition(activity, "transactions,lots,settlements,dividends");
    }
}
