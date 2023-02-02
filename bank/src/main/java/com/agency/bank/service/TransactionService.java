package com.agency.bank.service;

import com.agency.bank.dto.*;
import com.agency.bank.enums.TransactionStatus;
import com.agency.bank.model.*;
import com.agency.bank.repository.AccountRepository;
import com.agency.bank.repository.TransactionRepository;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

@PropertySource(value = "application.properties", ignoreResourceNotFound = true)
@AllArgsConstructor
@Service
public class TransactionService {

    private TransactionRepository transactionRepository;
    private CardService cardService;
    private ReservationService reservationService;
    private AccountRepository accountRepository;
    private ClientService clientService;
    private static String paymentUrl;
    private static String panAcquirer;

    @Value("${bank.paymentUrl}")
    public void setPaymentUrl(String paymentUrl){
        this.paymentUrl = paymentUrl;
    }

    @Value("${bank.panAcquirer}")
    public void setPanAcquirer(String panAcquirer){
        this.panAcquirer = panAcquirer;
    }


    private boolean checkClientAccountState(double amount, Client client) {
        double sum = 0;
        if (client.getReservations().size() > 0) {
            sum = sumReservations(client.getReservations());
        }

        if ((client.getAccount().getAmount() - sum - amount) >= 0) //uzima u obzir i neobradjene rezervacije
            return true;
        else
            return false;
    }

    private double sumReservations(List<Reservation> reservations) { //sabira sumu svih rezervacija
        double sum = 0;
        for (Reservation reservation: reservations) {
            sum += reservation.getAmount();
        }
        return sum;
    }

    private boolean sameBankForAcquirerAndIssuer(String pan) {
        if (pan.substring(0,7).equals(panAcquirer.substring(0,7)))
            return true;
        return false;
    }

    private int generateRandomNumber() {
        int m = (int) Math.pow(10, 10 - 1);
        return m + new Random().nextInt(9 * m);
    }
//
//    public String getPaymentURL(Transaction transaction, CardDto cardDto) {
//        if (transaction.getTransactionStatus() == TransactionStatus.SUCCESS || transaction.getTransactionStatus() == TransactionStatus.IN_PROGRESS)
//            return cardDto.getSuccessUrl();
//        else if (transaction.getTransactionStatus() == TransactionStatus.FAILED)
//            return cardDto.getFailedUrl();
//        else
//            return cardDto.getErrorUrl();
//    }

    @Scheduled(cron = "${greeting.cron}")
    private void finishTransactions(){
        //nisam ova cuvanja verovatno napisala kako treba zato je zakomentarisano
        Client acquirer = clientService.findByPan(panAcquirer);//prodji kroz sve klijente i sve njihove rezervacije
        for (Reservation reservation: reservationService.getAllWithClients()) {
            double newAmount = reservation.getClient().getAccount().getAmount() - reservation.getAmount();//smanji novac kupcu
            Account clientAccount = reservation.getClient().getAccount();
            clientAccount.setAmount(newAmount);
            accountRepository.save(clientAccount);

            reservation.setClient(null);
            reservationService.save(reservation);
            reservationService.delete(reservation);//izbrsi rezervaciju
        }
    }

    //Banka 2 koja je banka kupca, korak 5
    public Transaction payBuyer(CardPaymentRequestDto cardDto) {

        Transaction transaction = new Transaction();
        if (cardService.findByPan(cardDto.getPan()) == null) { //ako stvarno jeste clan te banke
            transaction.setTransactionStatus(TransactionStatus.FAILED);
        }

        if(checkClientAccountState(cardDto.getAmount(), clientService.findByPan(cardDto.getPan()))){

            Reservation reservation = Reservation.builder()
                    .description(cardDto.getDescription())
                    .amount(cardDto.getAmount())
                    .client(clientService.findByPan(cardDto.getPan()))
               //     .acquirerAccountNumber(clientService.findByPan(cardDto.getPanAcquirer()).getAccount().getAccountNumber())
                    .acquirerAccountNumber(cardDto.getPanAcquirer()) //neka bude PAN???
                    .build();

            transaction.setTransactionStatus(TransactionStatus.SUCCESS); //bice uspesna cim ima sredstava, nema sta da pukne
            transaction.setAcquirerOrderId(cardDto.getAcquirerOrderId());
            transaction.setAcquirerTimestamp(cardDto.getAcquirerTimestamp()); //rezervacija na vreme samog zahteva iz pcca
            transaction.setPaymentId(cardDto.getPaymentId());
            transaction.setMerchantTimestamp(LocalDateTime.now());
            transaction.setIssuerOrderId(generateRandomNumber());
            transaction.setIssuerTimestamp(LocalDateTime.now());
            transaction.setDescription(cardDto.getDescription());
            transaction.setAmount(cardDto.getAmount());
            transaction.setPaymentId(cardDto.getPaymentId());
            transaction.setAcquirerPan(cardDto.getPanAcquirer());
            transaction.setMerchantOrderId(0);
            transaction.setClient(clientService.findByPan(cardDto.getPan()));

            reservationService.save(reservation);
        } else {
            transaction.setTransactionStatus(TransactionStatus.FAILED);
        }
        transactionRepository.saveAndFlush(transaction);
        return transaction;
    }

    public TransactionPCCResponseDto responseToPCC(Transaction transaction){
        TransactionPCCResponseDto response = TransactionPCCResponseDto.builder()
                .transactionStatus(transaction.getTransactionStatus())
                .amount(transaction.getAmount())
                .description(transaction.getDescription())
                .acquirerOrderId(transaction.getAcquirerOrderId())
                .acquirerTimestamp(transaction.getAcquirerTimestamp())
                .merchantOrderId(transaction.getMerchantOrderId())
                .transactionStatus(transaction.getTransactionStatus())
                .issuerOrderId(transaction.getIssuerOrderId())
                .issuerOrderTimestamp(transaction.getIssuerTimestamp())
                .paymentId(transaction.getPaymentId())
                .acquirerPan(transaction.getAcquirerPan())
                .payer(transaction.getClient().getName() + " " + transaction.getClient().getSurname())
                .build();
        return response;
    }

}
