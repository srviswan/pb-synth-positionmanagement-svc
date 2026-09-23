package com.bank.esps.api.controller;

import com.bank.esps.application.service.state.StateManagedPositionService;
import com.bank.esps.application.service.state.StateManagedPositionService.ActivityRequest;
import com.bank.esps.application.service.state.StateManagedPositionService.LotView;
import com.bank.esps.application.service.state.StateManagedPositionService.PositionView;
import com.bank.esps.application.service.state.StateManagedPositionService.RollRequest;
import com.bank.esps.application.service.state.StateManagedPositionService.TradeView;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * State-managed positions. The live row and its lots are the source of truth.
 * The event-sourced API remains at /api/positions and /api/trades.
 */
@RestController
@RequestMapping("/api/state")
public class StatePositionController {

    private final StateManagedPositionService service;

    public StatePositionController(StateManagedPositionService service) {
        this.service = service;
    }

    @PostMapping("/positions/{positionKey}/activities")
    public PositionView apply(@PathVariable String positionKey, @RequestBody ActivityRequest request) {
        return service.apply(positionKey, request.toActivity());
    }

    @GetMapping("/positions/{positionKey}")
    public ResponseEntity<PositionView> get(@PathVariable String positionKey) {
        PositionView position = service.get(positionKey);
        return position == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(position);
    }

    @GetMapping("/positions/{positionKey}/lots")
    public List<LotView> lots(@PathVariable String positionKey,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        return service.lots(positionKey, businessDate);
    }

    @GetMapping("/positions/{positionKey}/trades")
    public List<TradeView> trades(@PathVariable String positionKey) {
        return service.trades(positionKey);
    }

    @GetMapping("/trades/{tradeId}")
    public ResponseEntity<TradeView> trade(@PathVariable String tradeId) {
        TradeView trade = service.trade(tradeId);
        return trade == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(trade);
    }

    @PostMapping("/regions/{region}/roll")
    public Map<String, Object> roll(@PathVariable String region, @RequestBody RollRequest request) {
        int lots = service.roll(region, request.businessDate(), request.previousBusinessDate());
        return Map.of("region", region, "businessDate", request.businessDate(), "lotRows", lots);
    }

    @PostMapping("/positions/{positionKey}/rebuild")
    public PositionView rebuild(@PathVariable String positionKey,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate) {
        return service.rebuild(positionKey, fromDate);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> conflict(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", exception.getMessage()));
    }
}
