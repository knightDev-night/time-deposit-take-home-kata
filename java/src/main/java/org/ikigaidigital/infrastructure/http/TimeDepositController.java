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

@RestController
@RequestMapping("/time-deposits")
public class TimeDepositController {
    private final TimeDepositService service;

    public TimeDepositController(TimeDepositService service) {
        this.service = service;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<TimeDepositResponse> getTimeDeposits() {
        return service.findAll().stream().map(TimeDepositResponse::from).toList();
    }

    @PostMapping("/interest")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void applyMonthlyInterest() {
        service.applyMonthlyInterest();
    }
}
