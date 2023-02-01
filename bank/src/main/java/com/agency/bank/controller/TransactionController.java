package com.agency.bank.controller;

import com.agency.bank.dto.*;
import com.agency.bank.model.Transaction;
import com.agency.bank.service.TransactionService;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.client.RestTemplate;

@RequestMapping("/payment")
@AllArgsConstructor
@Controller
public class TransactionController {

    private TransactionService transactionService;
    private RestTemplate restTemplate;


    //gadja pcc i vraca mu se novokreirana transakcija
    @PostMapping()
    public ResponseEntity<TransactionPCCResponseDto> payToBank(@RequestBody CardPaymentRequestDto cardDto){
        Transaction transaction = transactionService.payBuyer(cardDto);
        return new ResponseEntity<>(transactionService.responseToPCC(transaction), HttpStatus.CREATED);
    }
}
