package io.vmgen.api.models;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * VM Request state machine states with valid transition enforcement.
 */
public enum VmRequestState {
    QUEUED,
    BUILDING,
    EXPORTING,
    READY,
    FAILED,
    ARCHIVED;

    /**
     * Defines valid state transitions. Terminal states (READY, FAILED, ARCHIVED) cannot transition.
     */
    private static final Map<VmRequestState, Set<VmRequestState>> VALID_TRANSITIONS = Map.of(
            QUEUED, EnumSet.of(BUILDING, FAILED),
            BUILDING, EnumSet.of(EXPORTING, FAILED),
            EXPORTING, EnumSet.of(READY, FAILED),
            READY, EnumSet.of(ARCHIVED),
            FAILED, EnumSet.of(ARCHIVED),
            ARCHIVED, EnumSet.noneOf(VmRequestState.class)
    );

    /**
     * Check if transitioning to the target state is valid.
     * @param target The target state to transition to
     * @return true if the transition is valid
     */
    public boolean canTransitionTo(VmRequestState target) {
        Set<VmRequestState> allowed = VALID_TRANSITIONS.get(this);
        return allowed != null && allowed.contains(target);
    }

    /**
     * Get the set of valid target states from this state.
     */
    public Set<VmRequestState> validTransitions() {
        return VALID_TRANSITIONS.getOrDefault(this, EnumSet.noneOf(VmRequestState.class));
    }
}
