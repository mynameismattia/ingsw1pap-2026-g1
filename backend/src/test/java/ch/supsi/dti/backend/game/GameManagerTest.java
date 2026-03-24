package ch.supsi.dti.backend.game;

import org.junit.jupiter.api.Test;

public class GameManagerTest {

    @Test
    public void testPlaceBetUnderMinimum() {} // puntata < 5 rifiutata

    @Test
    public void testPlaceBetOverMaximum() {} // puntata > 1000 rifiutata

    @Test
    public void testPlaceBetOverBalance() {} // puntata > saldo rifiutata

    @Test
    public void testBlackjackPays3to2() {}

    @Test
    public void testDealerHitsOnSoft17() {}

    @Test
    public void testDealerStandsOnHard17() {}

    @Test
    public void testInsurancePays2to1() {}

    @Test
    public void testSplitCreates2Hands() {}

    @Test
    public void testResplitAces() {}

    @Test
    public void testDoubleDownAfterSplit() {}

    @Test
    public void testPlayerBusts() {}

    @Test
    public void testPushReturnsBet() {}

    @Test
    public void testMultiRoundSession() {}

    @Test
    public void testGameHistory() {}
}
