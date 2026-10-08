package com.example.fintech;

public final class FintechReindexServiceTest {
    public static void main(String[] args) throws Exception {
        FintechReindexService service = new FintechReindexService(null, "payments");
        require("REVIEW".equals(service.riskDecision(new PaymentEvent("p1", "shop", 100000, "US", false))));
        require("REVIEW".equals(service.riskDecision(new PaymentEvent("p2", "shop", 500, "US", true))));
        require("ALLOW".equals(service.riskDecision(new PaymentEvent("p3", "shop", 500, "US", false))));
        require("[0.25,-0.5,1.0]".equals(FintechReindexService.extractEmbedding(
                "{\"ok\":true,\"data\":{\"embedding\":[0.25,-0.5,1.0]}}")));
        System.out.println("risk decision checks passed");
    }
    private static void require(boolean condition) { if (!condition) throw new AssertionError(); }
}
