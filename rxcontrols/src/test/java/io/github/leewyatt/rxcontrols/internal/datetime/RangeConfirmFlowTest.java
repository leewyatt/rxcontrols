package io.github.leewyatt.rxcontrols.internal.datetime;

import io.github.leewyatt.rxcontrols.internal.datetime.RangeConfirmFlow.Endpoint;
import io.github.leewyatt.rxcontrols.internal.datetime.RangeConfirmFlow.Step;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round rules of {@link RangeConfirmFlow}: confirming, switching and closing.
 */
public class RangeConfirmFlowTest {

    /**
     * Verifies confirming the first endpoint moves on and confirming the second completes the round.
     */
    @Test
    public void confirmAdvancesThenCompletes() {
        RangeConfirmFlow<Integer> flow = new RangeConfirmFlow<>();
        flow.begin(Endpoint.START, null, null);
        flow.edit(Endpoint.START, 3);
        assertEquals(Step.ADVANCED, flow.confirm());
        assertEquals(Endpoint.END, flow.current());
        flow.edit(Endpoint.END, 7);
        assertEquals(Step.COMPLETED, flow.confirm());
        assertFalse(flow.isActive());
        assertEquals(3, flow.draft(Endpoint.START));
        assertEquals(7, flow.draft(Endpoint.END));
    }

    /**
     * Verifies an endpoint without a draft cannot be confirmed.
     */
    @Test
    public void confirmNeedsDraft() {
        RangeConfirmFlow<Integer> flow = new RangeConfirmFlow<>();
        assertEquals(Step.IGNORED, flow.confirm());
        flow.begin(Endpoint.START, null, 7);
        assertEquals(Step.IGNORED, flow.confirm());
        assertEquals(Endpoint.START, flow.current());
    }

    /**
     * Verifies a round started on the end goes on to the start and completes there.
     */
    @Test
    public void roundStartedOnEndWrapsToStart() {
        RangeConfirmFlow<Integer> flow = new RangeConfirmFlow<>();
        flow.begin(Endpoint.END, 3, 7);
        assertEquals(Step.ADVANCED, flow.confirm());
        assertEquals(Endpoint.START, flow.current());
        assertEquals(Step.COMPLETED, flow.confirm());
    }

    /**
     * Verifies the other endpoint is only left after the current one was confirmed weakly.
     */
    @Test
    public void switchNeedsConfirmation() {
        RangeConfirmFlow<Integer> flow = new RangeConfirmFlow<>();
        flow.begin(Endpoint.START, 3, null);
        assertTrue(flow.switchTo(Endpoint.START));
        assertFalse(flow.switchTo(Endpoint.END));
        assertEquals(Endpoint.START, flow.current());
        assertFalse(flow.confirmWeakly());
        assertTrue(flow.switchTo(Endpoint.END));
        assertEquals(Endpoint.END, flow.current());
    }

    /**
     * Verifies editing a weakly confirmed endpoint withdraws its confirmation.
     */
    @Test
    public void editWithdrawsConfirmation() {
        RangeConfirmFlow<Integer> flow = new RangeConfirmFlow<>();
        flow.begin(Endpoint.START, 3, null);
        flow.confirmWeakly();
        flow.edit(Endpoint.START, 4);
        assertFalse(flow.switchTo(Endpoint.END));
    }

    /**
     * Verifies a weak confirmation completes the round once the other endpoint was visited with a draft.
     */
    @Test
    public void weakConfirmCompletesVisitedRound() {
        RangeConfirmFlow<Integer> flow = new RangeConfirmFlow<>();
        flow.begin(Endpoint.START, 3, 7);
        flow.confirm();
        assertTrue(flow.confirmWeakly());
        assertFalse(flow.isActive());
        flow.begin(Endpoint.START, null, 7);
        assertFalse(flow.confirmWeakly());
        assertTrue(flow.isActive());
    }

    /**
     * Verifies closing commits a confirmed change while the endpoint then shown is untouched.
     */
    @Test
    public void closeCommitsConfirmedChange() {
        RangeConfirmFlow<Integer> flow = new RangeConfirmFlow<>();
        flow.begin(Endpoint.START, 3, 7);
        flow.edit(Endpoint.START, 4);
        flow.confirm();
        assertTrue(flow.close());
        assertFalse(flow.isActive());
        assertEquals(4, flow.draft(Endpoint.START));
    }

    /**
     * Verifies closing discards an untouched round, an unconfirmed change and a round with an endpoint left out.
     */
    @Test
    public void closeDiscardsOtherwise() {
        RangeConfirmFlow<Integer> flow = new RangeConfirmFlow<>();
        assertFalse(flow.close());

        flow.begin(Endpoint.START, 3, 7);
        flow.confirm();
        assertFalse(flow.close());

        flow.begin(Endpoint.START, 3, 7);
        flow.edit(Endpoint.START, 4);
        assertFalse(flow.close());

        flow.begin(Endpoint.START, 3, 7);
        flow.edit(Endpoint.START, 4);
        flow.confirm();
        flow.edit(Endpoint.END, 9);
        assertFalse(flow.close());

        flow.begin(Endpoint.START, null, null);
        flow.edit(Endpoint.START, 4);
        flow.confirm();
        assertFalse(flow.close());
    }

    /**
     * Verifies a reset ends the round and edits outside a round are ignored.
     */
    @Test
    public void resetEndsRound() {
        RangeConfirmFlow<Integer> flow = new RangeConfirmFlow<>();
        flow.begin(Endpoint.START, 3, 7);
        flow.reset();
        assertFalse(flow.isActive());
        assertNull(flow.current());
        flow.edit(Endpoint.START, 4);
        assertEquals(3, flow.draft(Endpoint.START));
        assertFalse(flow.switchTo(Endpoint.END));
    }

    /**
     * Verifies a completed round moves on instead while the other endpoint has no draft.
     */
    @Test
    public void confirmRevisitsEmptyEndpoint() {
        RangeConfirmFlow<Integer> flow = new RangeConfirmFlow<>();
        flow.begin(Endpoint.START, 3, 7);
        flow.confirm();
        flow.edit(Endpoint.START, null);
        assertEquals(Step.ADVANCED, flow.confirm());
        assertEquals(Endpoint.START, flow.current());
    }
}
