//! Separate test process exercises the initial seed, without the unit tests'
//! explicit deterministic seed reset masking platform initialization failures.
use scenemax_runtime_vm_core::pseudo_random_u32;
use std::collections::HashSet;

#[test]
fn random_values_initialize_without_an_explicit_seed() {
    let values: HashSet<_> = (0..64).map(|_| pseudo_random_u32()).collect();
    assert!(
        values.len() > 1,
        "random state must advance after initialization"
    );
}
