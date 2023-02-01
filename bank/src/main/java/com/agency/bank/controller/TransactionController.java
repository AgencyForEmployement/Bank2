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
    private static String pspUrl; //mozda bude pucalo zbog noArgsCont a kada ne stavim njega ne prolazi mi ovo za citanje iz application.properties
    private static String pcpUrl;
    @Value("${bank.pspUrl}")
    public void setPspUrl(String pspUrl){
        this.pspUrl = pspUrl;
    }

    @Value("${bank.pcpUrl}")
    public void setPcpUrl(String pcpUrl){
        this.pcpUrl = pcpUrl;
    }

    //kada kupac na pspu klike nacin placanja karticom i psp gadja ovaj endpoint
    @PostMapping
    public ResponseEntity<PaymentResponseDTO> requestPayment(@RequestBody PaymentForBankRequestDto paymentForBankRequestDto){
        return new ResponseEntity<>(transactionService.requestPayment(paymentForBankRequestDto), HttpStatus.OK);
    }

    //front banke nakon popunjenih podataka iz kartice
    @PostMapping(value = "/withCard")
    public ResponseEntity<String> pay(@RequestBody CardDto cardDto){
        Transaction transaction = transactionService.pay(cardDto);

        if (transaction == null)
            sendRequestToPCC(cardDto);
        else
            sendRequestToPSP(transaction);
            //obavestiti web shop da je nesto kupljeno, npr da mu se ne prikazuje kao artikal ??

        return new ResponseEntity<>(transactionService.getPaymentURL(transaction, cardDto), HttpStatus.OK);
    }

    private void sendRequestToPCC(CardDto cardDto) {
        //napraviti dto sa podacima koji su potrebni pcc-u
        //posalti zahtev pcc preko restTemplate

    }

    private void sendRequestToPSP(Transaction transaction) {
        PSPResponseDto pspResponseDto = PSPResponseDto.builder()
                .paymentId(transaction.getPaymentId())
                .acquirerOrderId(transaction.getAcquirerOrderId())
                .acquirerTimestamp(transaction.getAcquirerTimestamp())
                .merchantOrderId(transaction.getMerchantOrderId())
                .amount(transaction.getAmount())
                .description(transaction.getDescription())
                .transactionStatus(transaction.getTransactionStatus())
                .build();

        HttpStatus response = restTemplate.postForObject(pspUrl, pspResponseDto, HttpStatus.class);
        System.out.println(response);
    }


    //BANKA 2 kupca, njegova sredstva se rezervisu, i vraca odgovor pccu koji ga i gadja
    @PostMapping(value = "/bank-payment")
    public ResponseEntity<TransactionPCCResponseDto> payToBank(@RequestBody CardPaymentRequestDto cardDto){
        Transaction transaction = transactionService.payBuyer(cardDto);
        return new ResponseEntity<>(transactionService.responseToPCC(transaction), HttpStatus.CREATED);
    }
}
