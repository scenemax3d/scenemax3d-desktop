//! Build setup dialog orchestration.
use bevy::prelude::*;
use scenemax_ide_services::build_configuration::{Operation, Report, Setup};
use std::collections::VecDeque;
#[derive(Clone, Copy)]
pub(crate) enum Action {
    Open,
    Close,
    Run(Operation),
}
#[derive(Resource, Default)]
pub(crate) struct State {
    pub open: bool,
    pub actions: VecDeque<Action>,
    pub report: Report,
    setup: Setup,
}
pub(crate) fn update(mut state: ResMut<State>) {
    while let Some(action) = state.actions.pop_front() {
        match action {
            Action::Open => {
                state.open = true;
                if !state.report.busy {
                    state.setup.start(Operation::Scan);
                }
            }
            Action::Close => state.open = false,
            Action::Run(operation) => state.setup.start(operation),
        }
    }
    state.report = state.setup.report();
}
