package org.ikigaidigital.infrastructure.http;

import org.ikigaidigital.application.TimeDepositService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * HTTP adapter for listing deposits and applying monthly interest. It defines the routes
 * and response shapes while delegating use case execution and transaction management to
 * the application service.
 */
@RestController
@RequestMapping("/time-deposits")
public class TimeDepositController {
    private final TimeDepositService service;

    /**
     * Creates the HTTP adapter with the service that owns both use cases.
     *
     * @param service application entry point for listing and updating deposits
     */
    public TimeDepositController(TimeDepositService service) {
        this.service = service;
    }

    /**
     * Handles {@code GET /time-deposits} with a {@code 200 OK} JSON response.
     *
     * @return deposit responses with nested withdrawals, or an empty list serialized as an empty array
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<TimeDepositResponse> getTimeDeposits() {
        return service.findAll().stream().map(TimeDepositResponse::from).toList();
    }

    /**
     * Handles {@code POST /time-deposits/interest}, which requires no request body.
     * Delegates the balance update and responds with {@code 204 No Content} on success.
     */
    @PostMapping("/interest")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void applyMonthlyInterest() {
        service.applyMonthlyInterest();
    }
}
