package com.scenemaxeng.projector.ik;

public class FABRIKIKSolver implements IKSolver {
    @Override
    public void solve(IKContext context) {
        // Initial runtime support keeps FABRIK assets valid; full multi-joint limits can build on this class.
        new TwoBoneIKSolver().solve(context);
    }
}
