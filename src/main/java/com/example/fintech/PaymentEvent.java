package com.example.fintech;

public record PaymentEvent(String id, String merchant, int amountCents, String country, boolean chargeback) { }
