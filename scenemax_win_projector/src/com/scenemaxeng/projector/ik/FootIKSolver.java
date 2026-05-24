package com.scenemaxeng.projector.ik;

public class FootIKSolver implements IKSolver {
    private final TwoBoneIKSolver delegate = new TwoBoneIKSolver();

    @Override
    public void solve(IKContext context) {
        delegate.solve(context);
    }
}
