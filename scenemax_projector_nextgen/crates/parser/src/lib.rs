use thiserror::Error;

pub mod generated {
    #![allow(clippy::all)]
    #![allow(dead_code)]
    #![allow(non_camel_case_types)]
    #![allow(non_snake_case)]
    #![allow(non_upper_case_globals)]
    #![allow(unused_imports)]
    #![allow(unused_mut)]
    #![allow(unused_parens)]
    #![allow(unused_variables)]

    pub mod scenemaxnextgenlexer;
    pub mod scenemaxnextgenlistener;
    pub mod scenemaxnextgenparser;
    pub mod scenemaxnextgenvisitor;
}

#[derive(Debug, Clone, PartialEq)]
pub struct Program {
    pub statements: Vec<Statement>,
    pub screen_mode: ScreenMode,
}

impl Program {
    pub fn new(statements: Vec<Statement>) -> Self {
        Self {
            statements,
            screen_mode: ScreenMode::Unspecified,
        }
    }
}

#[derive(Debug, Clone, Copy, Default, PartialEq, Eq)]
pub enum ScreenMode {
    #[default]
    Unspecified,
    Window,
    Full,
    Borderless,
}

#[derive(Debug, Clone, PartialEq)]
pub enum Statement {
    ModelDecl {
        name: String,
        resource: String,
        options: EntityOptions,
    },
    LightDecl(LightDeclarationStatement),
    LightProbeAdd(LightProbeAddStatement),
    ObjectPool(ObjectPoolStatement),
    Animate(AnimationStatement),
    SpritePlay(SpritePlayStatement),
    EffekseerPlay(EffekseerPlayStatement),
    CinematicPlay(CinematicPlayStatement),
    AnimationSpeed(AnimationSpeedStatement),
    Visibility {
        target: String,
        visible: bool,
    },
    LookAt {
        target: String,
        subject: String,
    },
    Position(PositionStatement),
    Turn(TurnStatement),
    Move(MoveStatement),
    MoveTo(MoveToStatement),
    CharacterMode(CharacterModeStatement),
    ClearCharacterMode {
        target: String,
    },
    CharacterIgnore(CharacterIgnoreStatement),
    CharacterJump(CharacterJumpStatement),
    PhysicsImpulse(PhysicsImpulseStatement),
    PhysicsStop {
        target: String,
    },
    PhysicsThrowAt(PhysicsThrowAtStatement),
    PoolRelease(PoolReleaseStatement),
    Delete {
        target: String,
    },
    Attach(AttachStatement),
    KeyEvent(KeyEventStatement),
    WhenEvent(WhenEventStatement),
    If(IfStatement),
    GuardDef {
        name: String,
        condition: Condition,
    },
    Guarded {
        condition: Condition,
        actions: Vec<Statement>,
    },
    Repeat {
        times: usize,
        actions: Vec<Statement>,
    },
    DoWhile {
        condition: Condition,
        actions: Vec<Statement>,
    },
    LoopContinue {
        condition: Condition,
        actions: Vec<Statement>,
    },
    Async {
        actions: Vec<Statement>,
    },
    Wait {
        seconds: f32,
    },
    WaitValue {
        value: AssignmentValue,
    },
    WaitUntil {
        condition: Condition,
    },
    Return,
    ReturnValue {
        value: AssignmentValue,
    },
    Assignment(AssignmentStatement),
    SharedAssignment(AssignmentStatement),
    LocalAssignment(AssignmentStatement),
    FightingCamera(FightingCameraStatement),
    ThirdPersonCamera(ThirdPersonCameraStatement),
    CameraSystemSelect {
        name: String,
    },
    CameraChase {
        target: String,
    },
    CameraModifierApply(CameraModifierApplyStatement),
    CameraAttach(CameraAttachStatement),
    CameraAttachStop,
    Logger(LoggerStatement),
    DebugMode {
        enabled: bool,
    },
    FunctionDef(FunctionDefStatement),
    RunFunction {
        name: String,
        args: Vec<String>,
    },
    RunEvery {
        name: String,
        args: Vec<String>,
        interval_seconds: f32,
        interval_value: AssignmentValue,
    },
    CameraPosition(SceneMaxVec3),
    CameraRotation(SceneMaxVec3),
    CameraMove(CameraMoveStatement),
    Audio(AudioStatement),
    SetShader(SetShaderStatement),
    SetEnvironmentShader {
        shader: AssignmentValue,
    },
    Print(PrintStatement),
    WaitForKey {
        key: String,
    },
    SwitchTo {
        scene: String,
    },
    AddCode {
        path: String,
    },
    ChannelDraw(ChannelDrawStatement),
    UiLoad {
        name: String,
    },
    UiShowHide(UiShowHideStatement),
    UiMessage(UiMessageStatement),
    UiEase(UiEaseStatement),
    UiSetProperty(UiSetPropertyStatement),
    Weapon(WeaponStatement),
    AnimationControllerAction(AnimationControllerActionStatement),
    AnimationControllerEvent(AnimationControllerEventStatement),
    ThrowMotionApply(ThrowMotionApplyStatement),
    ThrowMotionEvent(ThrowMotionEventStatement),
    NoOp {
        text: String,
    },
    Unsupported {
        text: String,
    },
}

#[derive(Debug, Clone, Default, PartialEq)]
pub struct EntityOptions {
    pub position: Option<SceneMaxVec3>,
    pub position_value: Option<PositionValue>,
    pub rotation_degrees: Option<SceneMaxVec3>,
    pub scale: Option<SceneMaxVec3>,
    pub size: Option<SceneMaxVec3>,
    pub material: Option<String>,
    pub hidden: bool,
    pub collider: bool,
    pub sprite: bool,
    pub radius: Option<f32>,
    pub radius_top: Option<f32>,
    pub radius_bottom: Option<f32>,
    pub inner_radius_top: Option<f32>,
    pub inner_radius_bottom: Option<f32>,
    pub height: Option<f32>,
    pub steps: Option<usize>,
    pub thickness: Option<f32>,
    pub segments: Option<usize>,
    pub body_kind: Option<SceneMaxBodyKind>,
    pub collision_shape: Option<SceneMaxCollisionShape>,
}

#[derive(Debug, Clone, PartialEq)]
pub struct LightDeclarationStatement {
    pub name: String,
    pub light_type: LightType,
    pub color: Option<String>,
    pub intensity: Option<AssignmentValue>,
    pub intensity_unit: Option<String>,
    pub position: Option<PositionValue>,
    pub direction: Option<Vec<PositionExpr>>,
    pub shadow_mode: Option<String>,
    pub range: Option<AssignmentValue>,
    pub look_at: Option<String>,
    pub angle: Option<AssignmentValue>,
    pub preset: Option<String>,
    pub exposure: Option<AssignmentValue>,
    pub ambient_color: Option<String>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum LightType {
    Directional,
    Point,
    Spot,
    Sky,
    Ambient,
    Probe,
}

#[derive(Debug, Clone, PartialEq)]
pub struct LightProbeAddStatement {
    pub name: String,
    pub position: Option<PositionValue>,
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub enum SceneMaxBodyKind {
    Static,
    Kinematic,
    Dynamic,
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub enum SceneMaxCollisionShape {
    None,
    Box,
    Sphere,
    Capsule,
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct SceneMaxVec3 {
    pub x: f32,
    pub y: f32,
    pub z: f32,
}

#[derive(Debug, Clone, PartialEq)]
pub struct AnimationStatement {
    pub target: String,
    pub clip: String,
    pub speed: f32,
    pub speed_value: AssignmentValue,
    pub looped: bool,
    pub blocking: bool,
}

#[derive(Debug, Clone, PartialEq)]
pub struct SpritePlayStatement {
    pub target: String,
    pub from_frame: usize,
    pub from_frame_value: AssignmentValue,
    pub to_frame: usize,
    pub to_frame_value: AssignmentValue,
    pub duration_seconds: f32,
    pub duration_value: AssignmentValue,
    pub looped: bool,
}

#[derive(Debug, Clone, PartialEq)]
pub struct EffekseerPlayStatement {
    pub target: String,
    pub position: Option<PositionValue>,
    pub looped: bool,
    pub attrs: Vec<(String, AssignmentValue)>,
    pub async_run: bool,
}

#[derive(Debug, Clone, PartialEq)]
pub struct ChannelDrawStatement {
    pub channel: String,
    pub resource: String,
    pub clear: bool,
    pub pos_x: Option<AssignmentValue>,
    pub pos_y: Option<AssignmentValue>,
    pub width: Option<AssignmentValue>,
    pub height: Option<AssignmentValue>,
    pub frame: Option<AssignmentValue>,
    pub stretch: bool,
}

#[derive(Debug, Clone, PartialEq)]
pub struct PrintStatement {
    pub channel: String,
    pub text: UiPropertyValue,
    pub position: Option<PositionValue>,
    pub color: Option<String>,
    pub font_size: Option<AssignmentValue>,
    pub font: Option<String>,
    pub append: bool,
}

#[derive(Debug, Clone, PartialEq)]
pub struct CinematicPlayStatement {
    pub target: String,
    pub look_at: Option<CinematicLookAt>,
    pub duration_seconds: f32,
    pub reverse: bool,
    pub async_run: bool,
}

#[derive(Debug, Clone, PartialEq)]
pub enum CinematicLookAt {
    Entity(String),
    RelativePosition(String),
}

#[derive(Debug, Clone, PartialEq)]
pub struct AnimationSpeedStatement {
    pub target: String,
    pub speed: f32,
    pub speed_value: AssignmentValue,
    pub duration_seconds: Option<f32>,
    pub duration_value: Option<AssignmentValue>,
    pub condition: Option<Condition>,
}

#[derive(Debug, Clone, PartialEq)]
pub struct TurnStatement {
    pub target: String,
    pub degrees: f32,
    pub degrees_value: AssignmentValue,
    pub duration_seconds: f32,
    pub duration_value: AssignmentValue,
    pub loop_condition: Option<Condition>,
    pub async_run: bool,
}

#[derive(Debug, Clone, PartialEq)]
pub struct MoveStatement {
    pub target: String,
    pub direction: MoveDirection,
    pub distance: f32,
    pub distance_value: AssignmentValue,
    pub duration_seconds: f32,
    pub duration_value: AssignmentValue,
    pub loop_condition: Option<Condition>,
    pub async_run: bool,
}

#[derive(Debug, Clone, PartialEq)]
pub struct MoveToStatement {
    pub target: String,
    pub destination: MoveToDestination,
    pub duration_seconds: f32,
    pub duration_value: AssignmentValue,
    pub async_run: bool,
}

#[derive(Debug, Clone, PartialEq)]
pub enum MoveToDestination {
    Position(PositionValue),
    EntityForward {
        entity: String,
        distance: f32,
        distance_value: AssignmentValue,
    },
}

#[derive(Debug, Clone, PartialEq)]
pub struct CharacterModeStatement {
    pub target: String,
    pub gravity: Option<f32>,
    pub gravity_value: Option<AssignmentValue>,
}

#[derive(Debug, Clone, PartialEq)]
pub struct CharacterIgnoreStatement {
    pub target: String,
    pub ignored: String,
}

#[derive(Debug, Clone, PartialEq)]
pub struct CharacterJumpStatement {
    pub target: String,
    pub speed: f32,
    pub speed_value: AssignmentValue,
    pub async_run: bool,
}

#[derive(Debug, Clone, PartialEq)]
pub struct PhysicsImpulseStatement {
    pub target: String,
    pub direction: PhysicsDirection,
    pub strength: f32,
    pub strength_value: AssignmentValue,
}

#[derive(Debug, Clone, PartialEq)]
pub struct PhysicsThrowAtStatement {
    pub target: String,
    pub subject: String,
    pub power: AssignmentValue,
}

#[derive(Debug, Clone, PartialEq)]
pub struct ObjectPoolStatement {
    pub name: String,
    pub factory: String,
    pub size: usize,
}

#[derive(Debug, Clone, PartialEq)]
pub struct CameraMoveStatement {
    pub axis: SceneMaxAxis,
    pub distance: f32,
    pub distance_value: AssignmentValue,
    pub duration_seconds: f32,
    pub duration_value: AssignmentValue,
    pub async_run: bool,
}

#[derive(Debug, Clone, PartialEq)]
pub struct AudioStatement {
    pub action: AudioAction,
    pub sound: Option<String>,
    pub sound_value: Option<AssignmentValue>,
    pub looped: bool,
    pub volume: Option<AssignmentValue>,
}

#[derive(Debug, Clone, PartialEq)]
pub struct SetShaderStatement {
    pub target: String,
    pub shader: AssignmentValue,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum AudioAction {
    Play,
    Stop,
}

#[derive(Debug, Clone, PartialEq)]
pub struct PoolReleaseStatement {
    pub pool: String,
    pub target: String,
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub enum PhysicsDirection {
    Up,
    Down,
    Forward,
    Backward,
    Left,
    Right,
}

#[derive(Debug, Clone, PartialEq)]
pub struct AttachStatement {
    pub target: String,
    pub subject: String,
    pub offset: SceneMaxVec3,
}

#[derive(Debug, Clone, PartialEq)]
pub struct PositionStatement {
    pub target: String,
    pub position: PositionValue,
}

#[derive(Debug, Clone, PartialEq)]
pub enum PositionValue {
    Coordinates(Vec<PositionExpr>),
    Entity(String),
}

#[derive(Debug, Clone, PartialEq)]
pub enum PositionExpr {
    Number(f32),
    Value(AssignmentValue),
    EntityAxis {
        entity: String,
        axis: SceneMaxAxis,
        offset: f32,
    },
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub enum SceneMaxAxis {
    X,
    Y,
    Z,
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub enum MoveDirection {
    Forward,
    Backward,
    Left,
    Right,
    Up,
    Down,
}

#[derive(Debug, Clone, PartialEq)]
pub struct KeyEventStatement {
    pub key: String,
    pub trigger: KeyTrigger,
    pub guard: Option<Condition>,
    pub actions: Vec<Statement>,
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub enum KeyTrigger {
    Pressed,
    PressedOnce,
    Released,
}

#[derive(Debug, Clone, PartialEq)]
pub struct WhenEventStatement {
    pub condition: Condition,
    pub after_condition: Option<Condition>,
    pub guard: Option<Condition>,
    pub actions: Vec<Statement>,
}

#[derive(Debug, Clone, PartialEq)]
pub enum Condition {
    EqualsNumber {
        name: String,
        value: f32,
    },
    NotEqualsNumber {
        name: String,
        value: f32,
    },
    EqualsSymbol {
        name: String,
        value: String,
    },
    NotEqualsSymbol {
        name: String,
        value: String,
    },
    EqualsValue {
        left: AssignmentValue,
        right: AssignmentValue,
    },
    NotEqualsValue {
        left: AssignmentValue,
        right: AssignmentValue,
    },
    Compare {
        name: String,
        operator: ComparisonOperator,
        value: AssignmentValue,
    },
    CompareValue {
        left: AssignmentValue,
        operator: ComparisonOperator,
        right: AssignmentValue,
    },
    Truthy {
        name: String,
    },
    Collision {
        sources: Vec<String>,
        target: String,
    },
    Boolean(bool),
    Not(Box<Condition>),
    Alias(String),
    And(Vec<Condition>),
    Or(Vec<Condition>),
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub enum ComparisonOperator {
    Greater,
    GreaterOrEqual,
    Less,
    LessOrEqual,
}

#[derive(Debug, Clone, PartialEq)]
pub struct AssignmentStatement {
    pub name: String,
    pub value: AssignmentValue,
}

#[derive(Debug, Clone, PartialEq)]
pub enum AssignmentValue {
    Number(f32),
    Symbol(String),
    AnimationController(AnimationControllerValue),
    ThrowMotion(Box<ThrowMotionValue>),
    CameraModifier(CameraModifierValue),
    Condition(Box<Condition>),
    RandomInt {
        max: Box<AssignmentValue>,
    },
    Round {
        value: Box<AssignmentValue>,
    },
    Distance {
        left: String,
        right: String,
    },
    PoolAcquire {
        pool: String,
    },
    Binary {
        left: Box<AssignmentValue>,
        operator: ArithmeticOperator,
        right: Box<AssignmentValue>,
    },
}

#[derive(Debug, Clone, PartialEq)]
pub struct AnimationControllerValue {
    pub target: String,
    pub clip: String,
}

#[derive(Debug, Clone, PartialEq)]
pub struct AnimationControllerActionStatement {
    pub controller: String,
    pub action: AnimationControllerAction,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum AnimationControllerAction {
    Run,
    Stop,
}

#[derive(Debug, Clone, PartialEq)]
pub struct AnimationControllerEventStatement {
    pub controller: String,
    pub animation: String,
    pub percent: AssignmentValue,
    pub actions: Vec<Statement>,
}

#[derive(Debug, Clone, PartialEq)]
pub struct ThrowMotionValue {
    pub asset: ThrowMotionAsset,
    pub target: Option<ThrowMotionTarget>,
}

#[derive(Debug, Clone, PartialEq)]
pub enum ThrowMotionAsset {
    Literal(String),
    Expression(Box<AssignmentValue>),
}

#[derive(Debug, Clone, PartialEq)]
pub enum ThrowMotionTarget {
    Object(String),
    Position([AssignmentValue; 3]),
}

#[derive(Debug, Clone, PartialEq)]
pub struct CameraModifierValue {
    pub modifier_type: String,
    pub duration: f32,
    pub amplitude: f32,
    pub frequency: f32,
    pub x: f32,
    pub y: f32,
    pub z: f32,
    pub rx: f32,
    pub ry: f32,
    pub rz: f32,
    pub fov: f32,
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub enum ArithmeticOperator {
    Add,
    Subtract,
    Multiply,
    Divide,
    Modulo,
}

#[derive(Debug, Clone, PartialEq)]
pub struct FightingCameraStatement {
    pub name: String,
    pub target_a: String,
    pub target_b: String,
    pub depth: f32,
    pub height: f32,
    pub side: f32,
    pub min_distance: f32,
    pub max_distance: f32,
    pub zoom_factor: f32,
    pub damping: f32,
    pub look_ahead: f32,
    pub fov: f32,
    pub max_fov: f32,
}

#[derive(Debug, Clone, PartialEq)]
pub struct ThirdPersonCameraStatement {
    pub name: String,
    pub target: String,
    pub distance: f32,
    pub height: f32,
    pub side: f32,
    pub look_ahead: f32,
    pub damping: f32,
    pub fov: f32,
    pub max_fov: f32,
}

#[derive(Debug, Clone, PartialEq)]
pub struct CameraAttachStatement {
    pub target: String,
    pub offset: SceneMaxVec3,
}

#[derive(Debug, Clone, PartialEq)]
pub struct CameraModifierApplyStatement {
    pub target: String,
    pub modifier: String,
    pub overrides: Vec<(String, AssignmentValue)>,
}

#[derive(Debug, Clone, PartialEq)]
pub struct LoggerStatement {
    pub level: LoggerLevel,
    pub message: LoggerMessage,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum LoggerLevel {
    Info,
    Debug,
    Error,
}

#[derive(Debug, Clone, PartialEq)]
pub enum LoggerMessage {
    Text(String),
    Value(AssignmentValue),
}

#[derive(Debug, Clone, PartialEq)]
pub struct IfStatement {
    pub condition: Condition,
    pub actions: Vec<Statement>,
    pub else_actions: Vec<Statement>,
}

#[derive(Debug, Clone, PartialEq)]
pub struct FunctionDefStatement {
    pub name: String,
    pub params: Vec<String>,
    pub guard: Option<Condition>,
    pub actions: Vec<Statement>,
}

#[derive(Debug, Clone, PartialEq)]
pub struct UiTargetPath {
    pub ui_name: Option<String>,
    pub layer: String,
    pub widget_path: Vec<String>,
}

#[derive(Debug, Clone, PartialEq)]
pub struct UiShowHideStatement {
    pub target: UiTargetPath,
    pub visible: bool,
}

#[derive(Debug, Clone, PartialEq)]
pub struct UiMessageStatement {
    pub target: UiTargetPath,
    pub text: String,
    pub effects: String,
    pub duration_seconds: f32,
    pub async_run: bool,
}

#[derive(Debug, Clone, PartialEq)]
pub struct UiEaseStatement {
    pub target: UiTargetPath,
    pub easing: String,
    pub direction: UiEaseDirection,
    pub duration_seconds: f32,
    pub async_run: bool,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum UiEaseDirection {
    Up,
    Down,
    Left,
    Right,
}

#[derive(Debug, Clone, PartialEq)]
pub struct UiSetPropertyStatement {
    pub target: UiTargetPath,
    pub property: String,
    pub value: UiPropertyValue,
}

#[derive(Debug, Clone, PartialEq)]
pub struct WeaponStatement {
    pub owner: String,
    pub action: WeaponAction,
}

#[derive(Debug, Clone, PartialEq)]
pub enum WeaponAction {
    Equip { weapon: String },
    Posture { posture: String },
    Unequip,
    Detach,
}

#[derive(Debug, Clone, PartialEq)]
pub struct ThrowMotionApplyStatement {
    pub motion: String,
    pub target: String,
    pub target_is_equipped_weapon: bool,
    pub async_run: bool,
}

#[derive(Debug, Clone, PartialEq)]
pub struct ThrowMotionEventStatement {
    pub motion: String,
    pub event: String,
    pub index_percent: Option<AssignmentValue>,
    pub actions: Vec<Statement>,
}

#[derive(Debug, Clone, PartialEq)]
pub enum UiPropertyValue {
    Literal(String),
    Expression(AssignmentValue),
    Concatenation(Vec<UiPropertyValuePart>),
}

#[derive(Debug, Clone, PartialEq)]
pub enum UiPropertyValuePart {
    Literal(String),
    Expression(AssignmentValue),
}

#[derive(Debug, Error, PartialEq)]
pub enum ParseError {
    #[error("invalid number '{0}'")]
    InvalidNumber(String),
}

pub fn parse_program(source: &str) -> Result<Program, ParseError> {
    let logical_lines = logical_lines(source);
    let mut statements = Vec::new();
    let mut screen_mode = ScreenMode::Unspecified;
    let mut index = 0;
    let mut block_depth = 0usize;
    let mut pending_guard = None;
    let mut animation_controller_names = Vec::new();
    let mut throw_motion_names = Vec::new();

    while index < logical_lines.len() {
        let line = logical_lines[index].trim();
        if line.is_empty() {
            index += 1;
            continue;
        }

        if let Some(condition) = parse_condition_guard(line)? {
            pending_guard = Some(condition);
            index += 1;
            continue;
        }

        if block_depth > 0 {
            block_depth = update_block_depth(block_depth, line);
            index += 1;
            continue;
        }

        if let Some(mode) = parse_screen_mode(line) {
            if screen_mode == ScreenMode::Unspecified {
                screen_mode = mode;
            }
            index += 1;
            continue;
        }

        if let Some((mut event, next_index)) = parse_key_event_block(&logical_lines, index)? {
            event.guard = pending_guard.take();
            statements.push(Statement::KeyEvent(event));
            index = next_index;
            continue;
        }

        if let Some((mut event, next_index)) = parse_when_event_block(&logical_lines, index)? {
            event.guard = pending_guard.take();
            statements.push(Statement::WhenEvent(event));
            index = next_index;
            continue;
        }

        if let Some((mut function, next_index)) = parse_function_def_block(&logical_lines, index)? {
            function.guard = pending_guard.take();
            statements.push(Statement::FunctionDef(function));
            index = next_index;
            continue;
        }

        if let Some((event, next_index)) = parse_runtime_object_event_block(
            &logical_lines,
            index,
            &animation_controller_names,
            &throw_motion_names,
        )? {
            statements.push(event);
            pending_guard = None;
            index = next_index;
            continue;
        }

        let lower = line.to_ascii_lowercase();
        if let Some(times) = parse_repeat_header(line) {
            let (nested_actions, next_index) =
                parse_action_block(&logical_lines, index + 1, false)?;
            let repeated = Statement::Repeat {
                times,
                actions: nested_actions,
            };
            if lower.ends_with(" async") {
                statements.push(Statement::Async {
                    actions: vec![repeated],
                });
            } else {
                statements.push(repeated);
            }
            pending_guard = None;
            index = next_index;
            continue;
        }

        if lower == "do async" {
            let (nested_actions, next_index) = parse_action_block(&logical_lines, index + 1, true)?;
            statements.push(Statement::Async {
                actions: nested_actions,
            });
            pending_guard = None;
            index = next_index;
            continue;
        }

        if lower == "do" {
            let (nested_actions, next_index) = parse_action_block(&logical_lines, index + 1, true)?;
            statements.extend(nested_actions);
            pending_guard = None;
            index = next_index;
            continue;
        }

        if opens_runtime_block(line) {
            block_depth = update_block_depth(block_depth, line);
            statements.push(unsupported(line));
            index += 1;
            continue;
        }

        if let Some((name, condition)) = parse_guard_def(line)? {
            statements.push(Statement::GuardDef { name, condition });
            pending_guard = None;
            index += 1;
            continue;
        }

        if starts_with_keyword(line, "add") {
            let mut block = line.to_owned();
            while !has_code_terminator(&block) && index + 1 < logical_lines.len() {
                index += 1;
                block.push(' ');
                block.push_str(logical_lines[index].trim());
            }
            let paths = parse_quoted_strings(&block);
            if paths.is_empty() {
                statements.push(unsupported(line));
            } else {
                statements.extend(paths.into_iter().map(|path| Statement::AddCode { path }));
            }
            index += 1;
            continue;
        }

        if let Some(ui_statement) = parse_ui_statement(line)? {
            statements.push(ui_statement);
            index += 1;
            continue;
        }

        if let Some(weapon) = parse_weapon_statement(line) {
            statements.push(Statement::Weapon(weapon));
            index += 1;
            continue;
        }

        if let Some(action) = parse_animation_controller_action(line) {
            statements.push(Statement::AnimationControllerAction(action));
            index += 1;
            continue;
        }

        if let Some(apply) = parse_throw_motion_apply(line) {
            statements.push(Statement::ThrowMotionApply(apply));
            index += 1;
            continue;
        }

        if let Some(assignments) = parse_assignment_list(line)? {
            record_runtime_object_assignments(
                &assignments,
                &mut animation_controller_names,
                &mut throw_motion_names,
            );
            if is_shared_var_line(line) {
                statements.extend(assignments.into_iter().map(Statement::SharedAssignment));
            } else {
                statements.extend(assignments.into_iter().map(Statement::Assignment));
            }
            index += 1;
            continue;
        }

        statements.push(parse_statement(line)?);
        pending_guard = None;
        index += 1;
    }

    Ok(Program {
        statements,
        screen_mode,
    })
}

fn opens_runtime_block(line: &str) -> bool {
    let lower = line.to_ascii_lowercase();
    lower == "do async"
        || lower == "do"
        || lower.starts_with("do ")
        || (lower.starts_with("when ") && lower.ends_with(" do"))
        || line.ends_with('{')
}

fn parse_key_event_block(
    logical_lines: &[String],
    index: usize,
) -> Result<Option<(KeyEventStatement, usize)>, ParseError> {
    let Some((key, trigger)) = parse_key_event_header(logical_lines[index].trim()) else {
        return Ok(None);
    };

    let mut depth = 1usize;
    let mut actions = Vec::new();
    let mut cursor = index + 1;
    let mut animation_controller_names = Vec::new();
    let mut throw_motion_names = Vec::new();
    while cursor < logical_lines.len() {
        let line = logical_lines[cursor].trim();
        let lower = line.to_ascii_lowercase();

        if lower == "end do" {
            depth = depth.saturating_sub(1);
            cursor += 1;
            if depth == 0 {
                break;
            }
            continue;
        }

        if depth == 1 && is_while_terminator(line) {
            cursor += 1;
            break;
        }

        if depth == 1 && is_close_else_open(line) {
            break;
        }

        if let Some(condition) = parse_condition_guard(line)? {
            if let Some((mut event, next_index)) = parse_key_event_block(logical_lines, cursor + 1)?
            {
                event.guard = Some(condition);
                actions.push(Statement::KeyEvent(event));
                cursor = next_index;
                continue;
            }
            if let Some((mut event, next_index)) =
                parse_when_event_block(logical_lines, cursor + 1)?
            {
                event.guard = Some(condition);
                actions.push(Statement::WhenEvent(event));
                cursor = next_index;
                continue;
            }
            let (guarded_actions, next_index) =
                parse_guarded_actions_after(logical_lines, cursor + 1)?;
            actions.push(Statement::Guarded {
                condition,
                actions: guarded_actions,
            });
            cursor = next_index;
            continue;
        }

        if lower.starts_with('}') {
            depth = depth.saturating_sub(1);
            cursor += 1;
            if depth == 0 {
                break;
            }
            if lower.ends_with('{') {
                depth += 1;
            }
            continue;
        }

        if is_if_header(line) {
            if let Some((statement, next_index)) = parse_if_block(logical_lines, cursor)? {
                actions.push(Statement::If(statement));
                cursor = next_index;
            } else {
                cursor = skip_control_block(logical_lines, cursor);
            }
            continue;
        }

        if let Some(times) = parse_repeat_header(line) {
            let (nested_actions, next_index) =
                parse_action_block(logical_lines, cursor + 1, false)?;
            let repeated = Statement::Repeat {
                times,
                actions: nested_actions,
            };
            if lower.ends_with(" async") {
                actions.push(Statement::Async {
                    actions: vec![repeated],
                });
            } else {
                actions.push(repeated);
            }
            cursor = next_index;
            continue;
        }

        if lower == "do async" {
            let (nested_actions, next_index) = parse_action_block(logical_lines, cursor + 1, true)?;
            actions.push(Statement::Async {
                actions: nested_actions,
            });
            cursor = next_index;
            continue;
        }

        if lower == "do" {
            let (nested_actions, next_index) = parse_action_block(logical_lines, cursor + 1, true)?;
            actions.extend(nested_actions);
            cursor = next_index;
            continue;
        }

        if lower.starts_with("do ") {
            cursor = skip_control_block(logical_lines, cursor);
            continue;
        }

        if let Some((event, next_index)) = parse_key_event_block(logical_lines, cursor)? {
            actions.push(Statement::KeyEvent(event));
            cursor = next_index;
            continue;
        }

        if let Some((event, next_index)) = parse_runtime_object_event_block(
            logical_lines,
            cursor,
            &animation_controller_names,
            &throw_motion_names,
        )? {
            actions.push(event);
            cursor = next_index;
            continue;
        }

        if should_parse_key_action_line(line) {
            let parsed_actions = parse_action_statements(line)?;
            record_runtime_object_statements(
                &parsed_actions,
                &mut animation_controller_names,
                &mut throw_motion_names,
            );
            actions.extend(parsed_actions);
        }

        if opens_runtime_block(line) {
            depth += 1;
        }
        cursor += 1;
    }

    Ok(Some((
        KeyEventStatement {
            key,
            trigger,
            guard: None,
            actions,
        },
        cursor,
    )))
}

fn parse_when_event_block(
    logical_lines: &[String],
    index: usize,
) -> Result<Option<(WhenEventStatement, usize)>, ParseError> {
    let line = logical_lines[index].trim();
    if line.to_ascii_lowercase().starts_with("when key ") {
        return Ok(None);
    }
    let Some((condition, after_condition)) = parse_when_event_header(line)? else {
        return Ok(None);
    };

    let (actions, next_index) = parse_action_block(logical_lines, index + 1, false)?;
    Ok(Some((
        WhenEventStatement {
            condition,
            after_condition,
            guard: None,
            actions,
        },
        next_index,
    )))
}

fn parse_function_def_block(
    logical_lines: &[String],
    index: usize,
) -> Result<Option<(FunctionDefStatement, usize)>, ParseError> {
    let Some((name, params)) = parse_function_def_header(logical_lines[index].trim()) else {
        return Ok(None);
    };
    let (actions, next_index) = parse_action_block(logical_lines, index + 1, false)?;
    Ok(Some((
        FunctionDefStatement {
            name,
            params,
            guard: None,
            actions,
        },
        next_index,
    )))
}

fn parse_function_def_header(line: &str) -> Option<(String, Vec<String>)> {
    let Some((signature, rest)) = line.split_once('=') else {
        return None;
    };
    if rest.trim() != "{" {
        return None;
    }
    let signature = signature.trim();
    let name = signature.split('(').next().unwrap_or_default().trim();
    if is_variable_name(name) {
        Some((name.to_owned(), parse_call_args(signature)))
    } else {
        None
    }
}

fn parse_guard_def(line: &str) -> Result<Option<(String, Condition)>, ParseError> {
    let normalized = strip_var_prefix(line.trim());
    let Some(after_at) = normalized.strip_prefix('@') else {
        return Ok(None);
    };
    let Some((name, expression)) = after_at.split_once('=') else {
        return Ok(None);
    };
    let name = name.trim();
    if !is_variable_name(name) {
        return Ok(None);
    }
    let Some(condition) = parse_condition(expression.trim())? else {
        return Ok(None);
    };
    Ok(Some((name.to_owned(), condition)))
}

fn parse_when_event_header(
    line: &str,
) -> Result<Option<(Condition, Option<Condition>)>, ParseError> {
    let trimmed = line.trim();
    if !starts_with_keyword(trimmed, "when") {
        return Ok(None);
    }
    let after_when = trimmed["when".len()..].trim_start();
    let Some(condition_text) = strip_event_header_suffix(after_when) else {
        return Ok(None);
    };
    let (condition_text, after_text) = split_once_case_insensitive(condition_text, " after ")
        .map(|(condition, after)| (condition.trim(), Some(after.trim())))
        .unwrap_or((condition_text, None));
    let Some(condition) = parse_condition(condition_text)? else {
        return Ok(None);
    };
    let after_condition = if let Some(after_text) = after_text {
        parse_condition(after_text)?
    } else {
        None
    };
    Ok(Some((condition, after_condition)))
}

fn parse_condition(text: &str) -> Result<Option<Condition>, ParseError> {
    let text = strip_wrapping_parens(text.trim());
    if text.is_empty() {
        return Ok(None);
    }
    let or_parts = split_top_level_operator(text, "||");
    if or_parts.len() > 1 {
        let conditions = or_parts
            .into_iter()
            .filter_map(|part| parse_condition(part).transpose())
            .collect::<Result<Vec<_>, _>>()?;
        return Ok((!conditions.is_empty()).then_some(Condition::Or(conditions)));
    }
    let and_parts = split_top_level_operator(text, "&&");
    if and_parts.len() > 1 {
        let conditions = and_parts
            .into_iter()
            .filter_map(|part| parse_condition(part).transpose())
            .collect::<Result<Vec<_>, _>>()?;
        return Ok((!conditions.is_empty()).then_some(Condition::And(conditions)));
    }
    if let Some(rest) = text.strip_prefix('!') {
        if let Some(condition) = parse_condition(rest.trim())? {
            return Ok(Some(Condition::Not(Box::new(condition))));
        }
    }
    if let Some(rest) = strip_keyword_prefix(text, "not") {
        if let Some(condition) = parse_condition(rest.trim())? {
            return Ok(Some(Condition::Not(Box::new(condition))));
        }
    }
    if text.eq_ignore_ascii_case("true") {
        return Ok(Some(Condition::Boolean(true)));
    }
    if text.eq_ignore_ascii_case("false") {
        return Ok(Some(Condition::Boolean(false)));
    }
    if let Some(alias) = text.strip_prefix('@') {
        if is_variable_name(alias.trim()) {
            return Ok(Some(Condition::Alias(alias.trim().to_owned())));
        }
    }
    if let Some(collision) = parse_collision_condition(text) {
        return Ok(Some(collision));
    }
    if let Some((name, value)) = parse_comparison_value(text, "!=")? {
        return Ok(Some(match value {
            AssignmentValue::Number(value) => Condition::NotEqualsNumber { name, value },
            AssignmentValue::Symbol(value) => Condition::NotEqualsSymbol { name, value },
            AssignmentValue::Binary { .. } => return Ok(None),
            AssignmentValue::CameraModifier(_)
            | AssignmentValue::Condition(_)
            | AssignmentValue::AnimationController(_)
            | AssignmentValue::ThrowMotion(_)
            | AssignmentValue::RandomInt { .. }
            | AssignmentValue::Round { .. }
            | AssignmentValue::Distance { .. }
            | AssignmentValue::PoolAcquire { .. } => return Ok(None),
        }));
    }
    if let Some((left, right)) = parse_expression_comparison_values(text, "<>")? {
        return Ok(Some(Condition::NotEqualsValue { left, right }));
    }
    if let Some((left, right)) = parse_expression_comparison_values(text, "!=")? {
        return Ok(Some(Condition::NotEqualsValue { left, right }));
    }
    if let Some((name, value)) = parse_comparison_value(text, "==")? {
        return Ok(Some(match value {
            AssignmentValue::Number(value) => Condition::EqualsNumber { name, value },
            AssignmentValue::Symbol(value) => Condition::EqualsSymbol { name, value },
            AssignmentValue::Binary { .. } => return Ok(None),
            AssignmentValue::CameraModifier(_)
            | AssignmentValue::Condition(_)
            | AssignmentValue::AnimationController(_)
            | AssignmentValue::ThrowMotion(_)
            | AssignmentValue::RandomInt { .. }
            | AssignmentValue::Round { .. }
            | AssignmentValue::Distance { .. }
            | AssignmentValue::PoolAcquire { .. } => return Ok(None),
        }));
    }
    if let Some((left, right)) = parse_expression_comparison_values(text, "==")? {
        return Ok(Some(Condition::EqualsValue { left, right }));
    }
    for (operator_text, operator) in [
        (">=", ComparisonOperator::GreaterOrEqual),
        ("<=", ComparisonOperator::LessOrEqual),
        (">", ComparisonOperator::Greater),
        ("<", ComparisonOperator::Less),
    ] {
        if let Some((name, value)) = parse_comparison_value(text, operator_text)? {
            return Ok(Some(Condition::Compare {
                name,
                operator,
                value,
            }));
        }
        if let Some((left, right)) = parse_expression_comparison_values(text, operator_text)? {
            return Ok(Some(Condition::CompareValue {
                left,
                operator,
                right,
            }));
        }
    }
    if is_variable_path(text) {
        return Ok(Some(Condition::Truthy {
            name: text.to_owned(),
        }));
    }
    Ok(None)
}

fn split_top_level_operator<'a>(text: &'a str, operator: &str) -> Vec<&'a str> {
    let mut parts = Vec::new();
    let mut depth = 0usize;
    let mut start = 0usize;
    let mut index = 0usize;
    while index < text.len() {
        let value = text[index..].chars().next().unwrap_or_default();
        match value {
            '(' => depth += 1,
            ')' => depth = depth.saturating_sub(1),
            _ => {}
        }
        if depth == 0 && text[index..].starts_with(operator) {
            parts.push(text[start..index].trim());
            index += operator.len();
            start = index;
            continue;
        }
        index += value.len_utf8();
    }
    if parts.is_empty() {
        vec![text.trim()]
    } else {
        parts.push(text[start..].trim());
        parts
    }
}

fn strip_wrapping_parens(text: &str) -> &str {
    let mut current = text.trim();
    loop {
        if !current.starts_with('(') || !current.ends_with(')') {
            return current;
        }
        let mut depth = 0usize;
        let mut wraps = true;
        for (index, value) in current.char_indices() {
            match value {
                '(' => depth += 1,
                ')' => {
                    depth = depth.saturating_sub(1);
                    if depth == 0 && index != current.len() - 1 {
                        wraps = false;
                        break;
                    }
                }
                _ => {}
            }
        }
        if wraps {
            current = current[1..current.len() - 1].trim();
        } else {
            return current;
        }
    }
}

fn parse_comparison_value(
    text: &str,
    operator: &str,
) -> Result<Option<(String, AssignmentValue)>, ParseError> {
    let Some((left, right)) = split_once_top_level_operator(text, operator) else {
        return Ok(None);
    };
    let name = left.trim();
    if !is_variable_path(name) {
        return Ok(None);
    }
    let raw_value = right.trim().trim_end_matches('{').trim();
    let Some(value) = parse_assignment_value(raw_value)? else {
        return Ok(None);
    };
    Ok(Some((name.to_owned(), value)))
}

fn parse_expression_comparison_values(
    text: &str,
    operator: &str,
) -> Result<Option<(AssignmentValue, AssignmentValue)>, ParseError> {
    let Some((left, right)) = split_once_top_level_operator(text, operator) else {
        return Ok(None);
    };
    let Some(left) = parse_assignment_value(left.trim())? else {
        return Ok(None);
    };
    let Some(right) = parse_assignment_value(right.trim().trim_end_matches('{').trim())? else {
        return Ok(None);
    };
    Ok(Some((left, right)))
}

fn split_once_top_level_operator<'a>(text: &'a str, operator: &str) -> Option<(&'a str, &'a str)> {
    let mut depth = 0usize;
    let mut index = 0usize;
    while index < text.len() {
        let value = text[index..].chars().next().unwrap_or_default();
        match value {
            '(' => depth += 1,
            ')' => depth = depth.saturating_sub(1),
            _ => {}
        }
        if depth == 0 && text[index..].starts_with(operator) {
            return Some((text[..index].trim(), text[index + operator.len()..].trim()));
        }
        index += value.len_utf8();
    }
    None
}

fn parse_key_event_header(line: &str) -> Option<(String, KeyTrigger)> {
    let words = line.split_whitespace().collect::<Vec<_>>();
    if words.len() < 6
        || !words[0].eq_ignore_ascii_case("when")
        || !words[1].eq_ignore_ascii_case("key")
    {
        return None;
    }
    let key = words[2].to_ascii_lowercase();
    if !words[3].eq_ignore_ascii_case("is") {
        return None;
    }
    let trigger = if words[4].eq_ignore_ascii_case("released") {
        if !words
            .get(5)
            .is_some_and(|word| word.eq_ignore_ascii_case("do"))
        {
            return None;
        }
        KeyTrigger::Released
    } else if words[4].eq_ignore_ascii_case("pressed") {
        if words
            .get(5)
            .is_some_and(|word| word.eq_ignore_ascii_case("once"))
        {
            if !words
                .get(6)
                .is_some_and(|word| word.eq_ignore_ascii_case("do"))
            {
                return None;
            }
            KeyTrigger::PressedOnce
        } else {
            if !words
                .get(5)
                .is_some_and(|word| word.eq_ignore_ascii_case("do"))
            {
                return None;
            }
            KeyTrigger::Pressed
        }
    } else {
        return None;
    };

    Some((key, trigger))
}

fn parse_collision_condition(text: &str) -> Option<Condition> {
    let (sources, target) = text.split_once(" collides with ")?;
    let sources = split_top_level_comma(sources)
        .into_iter()
        .map(normalize_collision_reference)
        .filter(|source| !source.is_empty())
        .collect::<Vec<_>>();
    let target = normalize_collision_reference(target);
    (!sources.is_empty() && !target.is_empty()).then_some(Condition::Collision { sources, target })
}

fn normalize_collision_reference(text: &str) -> String {
    text.trim()
        .split_whitespace()
        .next()
        .unwrap_or_default()
        .trim()
        .trim_matches('"')
        .to_owned()
}

fn parse_action_block(
    logical_lines: &[String],
    cursor: usize,
    allow_while_terminator: bool,
) -> Result<(Vec<Statement>, usize), ParseError> {
    let (actions, cursor, _) =
        parse_action_block_with_stop(logical_lines, cursor, allow_while_terminator)?;
    Ok((actions, cursor))
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum ActionBlockStop {
    Closed,
    Else,
    While,
    Eof,
}

fn parse_action_block_with_stop(
    logical_lines: &[String],
    mut cursor: usize,
    allow_while_terminator: bool,
) -> Result<(Vec<Statement>, usize, ActionBlockStop), ParseError> {
    let mut depth = 1usize;
    let mut actions = Vec::new();
    let mut animation_controller_names = Vec::new();
    let mut throw_motion_names = Vec::new();
    while cursor < logical_lines.len() {
        let line = logical_lines[cursor].trim();
        let lower = line.to_ascii_lowercase();

        if lower == "end do" {
            depth = depth.saturating_sub(1);
            cursor += 1;
            if depth == 0 {
                return Ok((actions, cursor, ActionBlockStop::Closed));
            }
            continue;
        }

        if allow_while_terminator && depth == 1 && is_while_terminator(line) {
            cursor += 1;
            if let Some(condition) = parse_while_terminator(line)? {
                return Ok((
                    vec![Statement::DoWhile { condition, actions }],
                    cursor,
                    ActionBlockStop::While,
                ));
            }
            return Ok((actions, cursor, ActionBlockStop::While));
        }

        if depth == 1 && is_close_else_open(line) {
            return Ok((actions, cursor, ActionBlockStop::Else));
        }

        if let Some(condition) = parse_condition_guard(line)? {
            if let Some((mut event, next_index)) = parse_key_event_block(logical_lines, cursor + 1)?
            {
                event.guard = Some(condition);
                actions.push(Statement::KeyEvent(event));
                cursor = next_index;
                continue;
            }
            if let Some((mut event, next_index)) =
                parse_when_event_block(logical_lines, cursor + 1)?
            {
                event.guard = Some(condition);
                actions.push(Statement::WhenEvent(event));
                cursor = next_index;
                continue;
            }
            let (guarded_actions, next_index) =
                parse_guarded_actions_after(logical_lines, cursor + 1)?;
            actions.push(Statement::Guarded {
                condition,
                actions: guarded_actions,
            });
            cursor = next_index;
            continue;
        }

        if lower.starts_with('}') {
            depth = depth.saturating_sub(1);
            cursor += 1;
            if depth == 0 {
                return Ok((actions, cursor, ActionBlockStop::Closed));
            }
            if lower.ends_with('{') {
                depth += 1;
            }
            continue;
        }

        if is_if_header(line) {
            if let Some((statement, next_index)) = parse_if_block(logical_lines, cursor)? {
                actions.push(Statement::If(statement));
                cursor = next_index;
            } else {
                cursor = skip_control_block(logical_lines, cursor);
            }
            continue;
        }

        if let Some(times) = parse_repeat_header(line) {
            let (nested_actions, next_index) =
                parse_action_block(logical_lines, cursor + 1, false)?;
            let repeated = Statement::Repeat {
                times,
                actions: nested_actions,
            };
            if lower.ends_with(" async") {
                actions.push(Statement::Async {
                    actions: vec![repeated],
                });
            } else {
                actions.push(repeated);
            }
            cursor = next_index;
            continue;
        }

        if lower == "do async" {
            let (nested_actions, next_index) = parse_action_block(logical_lines, cursor + 1, true)?;
            actions.push(Statement::Async {
                actions: nested_actions,
            });
            cursor = next_index;
            continue;
        }

        if lower == "do" {
            let (nested_actions, next_index) = parse_action_block(logical_lines, cursor + 1, true)?;
            actions.extend(nested_actions);
            cursor = next_index;
            continue;
        }

        if lower.starts_with("do ") {
            cursor = skip_control_block(logical_lines, cursor);
            continue;
        }

        if let Some((event, next_index)) = parse_key_event_block(logical_lines, cursor)? {
            actions.push(Statement::KeyEvent(event));
            cursor = next_index;
            continue;
        }

        if let Some((event, next_index)) = parse_runtime_object_event_block(
            logical_lines,
            cursor,
            &animation_controller_names,
            &throw_motion_names,
        )? {
            actions.push(event);
            cursor = next_index;
            continue;
        }

        if should_parse_key_action_line(line) {
            let parsed_actions = parse_action_statements(line)?;
            record_runtime_object_statements(
                &parsed_actions,
                &mut animation_controller_names,
                &mut throw_motion_names,
            );
            actions.extend(parsed_actions);
        }

        if opens_runtime_block(line) {
            depth += 1;
        }
        cursor += 1;
    }

    Ok((actions, cursor, ActionBlockStop::Eof))
}

fn parse_if_block(
    logical_lines: &[String],
    index: usize,
) -> Result<Option<(IfStatement, usize)>, ParseError> {
    let Some(condition) = parse_if_header(logical_lines[index].trim())? else {
        return Ok(None);
    };

    let (actions, mut next_index, stop) =
        parse_action_block_with_stop(logical_lines, index + 1, false)?;
    let mut else_actions = Vec::new();
    if stop == ActionBlockStop::Else
        && next_index < logical_lines.len()
        && is_close_else_open(logical_lines[next_index].trim())
    {
        let (parsed_else_actions, after_else) = parse_else_branch(logical_lines, next_index)?;
        else_actions = parsed_else_actions;
        next_index = after_else;
    }

    Ok(Some((
        IfStatement {
            condition,
            actions,
            else_actions,
        },
        next_index,
    )))
}

fn parse_else_branch(
    logical_lines: &[String],
    index: usize,
) -> Result<(Vec<Statement>, usize), ParseError> {
    let line = logical_lines[index].trim();
    if let Some(condition) = parse_else_if_header(line)? {
        let (actions, mut next_index, stop) =
            parse_action_block_with_stop(logical_lines, index + 1, false)?;
        let mut else_actions = Vec::new();
        if stop == ActionBlockStop::Else
            && next_index < logical_lines.len()
            && is_close_else_open(logical_lines[next_index].trim())
        {
            let (parsed_else_actions, after_else) = parse_else_branch(logical_lines, next_index)?;
            else_actions = parsed_else_actions;
            next_index = after_else;
        }
        return Ok((
            vec![Statement::If(IfStatement {
                condition,
                actions,
                else_actions,
            })],
            next_index,
        ));
    }
    parse_action_block(logical_lines, index + 1, false)
}

fn parse_guarded_actions_after(
    logical_lines: &[String],
    cursor: usize,
) -> Result<(Vec<Statement>, usize), ParseError> {
    let Some(line) = logical_lines.get(cursor).map(|line| line.trim()) else {
        return Ok((Vec::new(), cursor));
    };
    let lower = line.to_ascii_lowercase();
    if lower == "do" {
        return parse_action_block(logical_lines, cursor + 1, false);
    }
    if lower == "do async" {
        let (actions, next_index) = parse_action_block(logical_lines, cursor + 1, true)?;
        return Ok((vec![Statement::Async { actions }], next_index));
    }
    if let Some(times) = parse_repeat_header(line) {
        let (actions, next_index) = parse_action_block(logical_lines, cursor + 1, false)?;
        let repeated = Statement::Repeat { times, actions };
        if lower.ends_with(" async") {
            return Ok((
                vec![Statement::Async {
                    actions: vec![repeated],
                }],
                next_index,
            ));
        }
        return Ok((vec![repeated], next_index));
    }
    if lower.starts_with("do ") {
        return Ok((Vec::new(), skip_control_block(logical_lines, cursor)));
    }
    Ok((parse_action_statements(line)?, cursor + 1))
}

fn parse_action_statements(line: &str) -> Result<Vec<Statement>, ParseError> {
    if let Some(statement) = parse_ui_statement(line)? {
        return Ok(vec![statement]);
    }
    if let Some(statement) = parse_shader_statement(line)? {
        return Ok(vec![statement]);
    }
    if let Some(weapon) = parse_weapon_statement(line) {
        return Ok(vec![Statement::Weapon(weapon)]);
    }
    if let Some(action) = parse_animation_controller_action(line) {
        return Ok(vec![Statement::AnimationControllerAction(action)]);
    }
    if let Some(apply) = parse_throw_motion_apply(line) {
        return Ok(vec![Statement::ThrowMotionApply(apply)]);
    }
    if is_shared_var_line(line) {
        if let Some(assignments) = parse_assignment_list(line)? {
            return Ok(assignments
                .into_iter()
                .map(Statement::SharedAssignment)
                .collect());
        }
    }
    if is_local_assignment_line(line) {
        if let Some(assignments) = parse_assignment_list(line)? {
            return Ok(assignments
                .into_iter()
                .map(Statement::LocalAssignment)
                .collect());
        }
    }
    if let Some(assignments) = parse_assignment_list(line)? {
        return Ok(assignments.into_iter().map(Statement::Assignment).collect());
    }
    Ok(vec![parse_statement(line)?])
}

fn parse_if_header(line: &str) -> Result<Option<Condition>, ParseError> {
    if !is_if_header(line) {
        return Ok(None);
    }
    let without_open_brace = line.trim().trim_end_matches('{').trim();
    let Some(after_if) = without_open_brace
        .strip_prefix("if")
        .or_else(|| without_open_brace.strip_prefix("IF"))
    else {
        return Ok(None);
    };
    let condition_text = after_if
        .trim()
        .strip_prefix('(')
        .and_then(|value| value.strip_suffix(')'))
        .unwrap_or_else(|| after_if.trim())
        .trim();
    parse_condition(condition_text)
}

fn parse_else_if_header(line: &str) -> Result<Option<Condition>, ParseError> {
    let trimmed = line.trim();
    let Some(after_close) = trimmed.strip_prefix('}') else {
        return Ok(None);
    };
    let after_else = after_close.trim_start();
    let Some(after_else) = after_else
        .strip_prefix("else")
        .or_else(|| after_else.strip_prefix("ELSE"))
    else {
        return Ok(None);
    };
    parse_if_header(after_else.trim_start())
}

fn is_if_header(line: &str) -> bool {
    let lower = line.trim().to_ascii_lowercase();
    (lower.starts_with("if ") || lower.starts_with("if(")) && lower.ends_with('{')
}

fn is_close_else_open(line: &str) -> bool {
    let lower = line.trim().to_ascii_lowercase();
    lower.starts_with("} else") && lower.ends_with('{')
}

fn is_while_terminator(line: &str) -> bool {
    line.trim_start().to_ascii_lowercase().starts_with("while ")
}

fn parse_while_terminator(line: &str) -> Result<Option<Condition>, ParseError> {
    let trimmed = line.trim_start();
    let Some(condition_text) = trimmed
        .strip_prefix("while ")
        .or_else(|| trimmed.strip_prefix("WHILE "))
        .or_else(|| trimmed.strip_prefix("While "))
    else {
        return Ok(None);
    };
    parse_condition(condition_text.trim())
}

fn skip_control_block(logical_lines: &[String], index: usize) -> usize {
    let mut depth = 1usize;
    let mut cursor = index + 1;
    while cursor < logical_lines.len() {
        let line = logical_lines[cursor].trim();
        let lower = line.to_ascii_lowercase();

        if lower.starts_with('}') {
            depth = depth.saturating_sub(1);
            cursor += 1;
            if depth == 0 {
                if lower.contains("else") && lower.ends_with('{') {
                    depth = 1;
                    continue;
                }
                break;
            }
            if lower.ends_with('{') {
                depth += 1;
            }
            continue;
        }

        if opens_runtime_block(line) {
            depth += 1;
        }
        cursor += 1;
    }
    cursor
}

fn strip_event_header_suffix(text: &str) -> Option<&str> {
    let trimmed = text.trim_end();
    if let Some(prefix) = trimmed.strip_suffix('{') {
        return Some(prefix.trim_end());
    }
    let lower = trimmed.to_ascii_lowercase();
    if lower.ends_with("do async") {
        let prefix = &trimmed[..trimmed.len() - "do async".len()];
        return Some(prefix.trim_end());
    }
    if lower.ends_with("do") {
        let prefix = &trimmed[..trimmed.len() - "do".len()];
        return Some(prefix.trim_end());
    }
    None
}

fn parse_repeat_header(line: &str) -> Option<usize> {
    let lower = line.trim().to_ascii_lowercase();
    if !lower.starts_with("do ") || !lower.contains(" times") {
        return None;
    }
    let times_text = lower["do ".len()..].split_whitespace().next()?;
    times_text.parse::<usize>().ok().filter(|times| *times > 0)
}

fn should_parse_key_action_line(line: &str) -> bool {
    let trimmed = line.trim();
    if trimmed.is_empty()
        || is_condition_guard(trimmed)
        || trimmed.eq_ignore_ascii_case("do")
        || trimmed.eq_ignore_ascii_case("do async")
        || trimmed.to_ascii_lowercase().starts_with("do ")
        || trimmed.to_ascii_lowercase().starts_with("if ")
        || trimmed.to_ascii_lowercase().starts_with("else")
        || trimmed.ends_with('{')
    {
        return false;
    }
    true
}

fn is_condition_guard(line: &str) -> bool {
    let trimmed = line.trim();
    trimmed.starts_with('[') || trimmed.starts_with("#[")
}

fn parse_condition_guard(line: &str) -> Result<Option<Condition>, ParseError> {
    let trimmed = line.trim();
    let Some(content) = trimmed
        .strip_prefix("#[")
        .and_then(|value| value.strip_suffix(']'))
        .or_else(|| {
            trimmed
                .strip_prefix('[')
                .and_then(|value| value.strip_suffix(']'))
        })
    else {
        return Ok(None);
    };
    parse_condition(content.trim())
}

fn update_block_depth(current: usize, line: &str) -> usize {
    let mut depth = current;
    let lower = line.to_ascii_lowercase();
    if lower == "end do" || lower.starts_with('}') {
        depth = depth.saturating_sub(1);
    }
    if opens_runtime_block(line) {
        depth += 1;
    }
    depth
}

fn parse_statement(line: &str) -> Result<Statement, ParseError> {
    if let Some(statement) = parse_ui_statement(line)? {
        return Ok(statement);
    }

    if let Some(camera) = parse_fighting_camera(line)? {
        return Ok(Statement::FightingCamera(camera));
    }

    if let Some(camera) = parse_third_person_camera(line)? {
        return Ok(Statement::ThirdPersonCamera(camera));
    }

    if let Some(camera_system) = parse_camera_system_select(line) {
        return Ok(Statement::CameraSystemSelect {
            name: camera_system,
        });
    }

    if let Some((name, interval_value, interval_seconds)) = parse_run_every(line)? {
        return Ok(Statement::RunEvery {
            name,
            args: parse_call_args(line),
            interval_seconds,
            interval_value,
        });
    }

    if let Some(logger) = parse_logger_statement(line)? {
        return Ok(Statement::Logger(logger));
    }

    if let Some(debug_mode) = parse_debug_mode_statement(line) {
        return Ok(debug_mode);
    }

    if let Some(weapon) = parse_weapon_statement(line) {
        return Ok(Statement::Weapon(weapon));
    }

    if let Some(light_probe) = parse_light_probe_add(line)? {
        return Ok(Statement::LightProbeAdd(light_probe));
    }

    if let Some(shader) = parse_shader_statement(line)? {
        return Ok(shader);
    }

    if let Some(run_function) = parse_run_function_statement(line) {
        return Ok(run_function);
    }

    if is_shared_var_line(line) {
        if let Some(assignment) = parse_assignment(line)? {
            return Ok(Statement::SharedAssignment(assignment));
        }
    }

    if is_local_assignment_line(line) {
        if let Some(assignment) = parse_assignment(line)? {
            return Ok(Statement::LocalAssignment(assignment));
        }
    }

    if let Some(assignment) = parse_assignment(line)? {
        return Ok(Statement::Assignment(assignment));
    }

    if let Some(delete) = parse_delete(line) {
        return Ok(Statement::Delete { target: delete });
    }

    if let Some(release) = parse_pool_release(line) {
        return Ok(Statement::PoolRelease(release));
    }

    if let Some(scene) = parse_switch(line) {
        return Ok(Statement::SwitchTo { scene });
    }

    if let Some(key) = parse_wait_for_key(line) {
        return Ok(Statement::WaitForKey { key });
    }

    if let Some(condition) = parse_wait_until(line)? {
        return Ok(Statement::WaitUntil { condition });
    }

    if let Some(value) = parse_wait(line)? {
        return Ok(match value {
            AssignmentValue::Number(seconds) => Statement::Wait { seconds },
            value => Statement::WaitValue { value },
        });
    }

    if let Some(return_text) = strip_keyword_prefix(line, "return") {
        if let Some(value) = parse_assignment_value(return_text.trim())? {
            return Ok(Statement::ReturnValue { value });
        }
        return Ok(Statement::Return);
    }

    if let Some(position) = parse_camera_command(line, "camera.pos")? {
        return Ok(Statement::CameraPosition(position));
    }

    if let Some(rotation) = parse_camera_command(line, "camera.rotate")? {
        return Ok(Statement::CameraRotation(rotation));
    }

    if let Some(camera_move) = parse_camera_move(line)? {
        return Ok(Statement::CameraMove(camera_move));
    }

    if let Some(audio) = parse_audio_statement(line)? {
        return Ok(Statement::Audio(audio));
    }

    if let Some(print) = parse_print_statement(line)? {
        return Ok(Statement::Print(print));
    }

    if let Some(chase) = parse_camera_chase(line)? {
        return Ok(chase);
    }

    if let Some(modifier_apply) = parse_camera_modifier_apply(line)? {
        return Ok(Statement::CameraModifierApply(modifier_apply));
    }

    if let Some(attach) = parse_camera_attach(line)? {
        return Ok(attach);
    }

    if let Some(statement) = parse_light_decl(line)? {
        return Ok(statement);
    }

    if let Some(statement) = parse_model_decl(line)? {
        return Ok(statement);
    }

    if let Some(visibility) = parse_visibility(line) {
        return Ok(visibility);
    }

    if let Some(look_at) = parse_look_at(line) {
        return Ok(look_at);
    }

    if let Some(attach) = parse_attach(line)? {
        return Ok(Statement::Attach(attach));
    }

    if let Some(position) = parse_position(line)? {
        return Ok(Statement::Position(position));
    }

    if let Some(turn) = parse_turn(line)? {
        return Ok(Statement::Turn(turn));
    }

    if let Some(move_to) = parse_move_to(line)? {
        return Ok(Statement::MoveTo(move_to));
    }

    if let Some(movement) = parse_move(line)? {
        return Ok(Statement::Move(movement));
    }

    if let Some(character_mode) = parse_character_mode(line)? {
        return Ok(Statement::CharacterMode(character_mode));
    }

    if let Some(target) = parse_clear_character_mode(line) {
        return Ok(Statement::ClearCharacterMode { target });
    }

    if let Some(ignore) = parse_character_ignore(line) {
        return Ok(Statement::CharacterIgnore(ignore));
    }

    if let Some(jump) = parse_character_jump(line)? {
        return Ok(Statement::CharacterJump(jump));
    }

    if let Some(physics) = parse_physics_command(line)? {
        return Ok(physics);
    }

    if let Some(throw_at) = parse_throw_at(line)? {
        return Ok(Statement::PhysicsThrowAt(throw_at));
    }

    if let Some(sprite_play) = parse_sprite_play(line)? {
        return Ok(Statement::SpritePlay(sprite_play));
    }

    if let Some(effekseer_play) = parse_effekseer_play(line)? {
        return Ok(Statement::EffekseerPlay(effekseer_play));
    }

    if let Some(draw) = parse_channel_draw(line)? {
        return Ok(Statement::ChannelDraw(draw));
    }

    if let Some(cinematic_play) = parse_cinematic_play(line)? {
        return Ok(Statement::CinematicPlay(cinematic_play));
    }

    if is_runtime_noop_command(line) {
        return Ok(Statement::NoOp {
            text: line.to_owned(),
        });
    }

    if is_unsupported_dotted_runtime_command(line) {
        return Ok(unsupported(line));
    }

    if let Some(animation_speed) = parse_animation_speed(line)? {
        return Ok(Statement::AnimationSpeed(animation_speed));
    }

    if let Some(animation) = parse_animation(line)? {
        return Ok(Statement::Animate(animation));
    }

    Ok(unsupported(line))
}

fn parse_ui_statement(line: &str) -> Result<Option<Statement>, ParseError> {
    let line = line.trim();
    let lower = line.to_ascii_lowercase();
    if !lower.starts_with("ui.") {
        return Ok(None);
    }

    if starts_with_case_insensitive(line, "UI.load") {
        if let Some(name) = parse_quoted_strings(line).into_iter().next() {
            return Ok(Some(Statement::UiLoad { name }));
        }
        return Ok(None);
    }

    if let Some(before_call) = strip_suffix_case_insensitive(line, ".show") {
        if let Some(target) = parse_ui_target_path(before_call) {
            return Ok(Some(Statement::UiShowHide(UiShowHideStatement {
                target,
                visible: true,
            })));
        }
    }
    if let Some(before_call) = strip_suffix_case_insensitive(line, ".hide") {
        if let Some(target) = parse_ui_target_path(before_call) {
            return Ok(Some(Statement::UiShowHide(UiShowHideStatement {
                target,
                visible: false,
            })));
        }
    }

    if let Some(open_index) = lower.find(".message(") {
        let path_text = &line[..open_index];
        let Some(target) = parse_ui_target_path(path_text) else {
            return Ok(None);
        };
        let call_open_index = open_index + ".message".len();
        let args = parse_call_args(&line[call_open_index..]);
        let Some(text) = args.first().cloned() else {
            return Ok(None);
        };
        let duration_seconds = args
            .last()
            .and_then(|value| value.parse::<f32>().ok())
            .unwrap_or(0.0);
        let effects = args
            .get(1)
            .filter(|value| value.parse::<f32>().is_err())
            .cloned()
            .unwrap_or_default();
        let async_run = contains_keyword_after_matching_call(line, call_open_index, "async");
        return Ok(Some(Statement::UiMessage(UiMessageStatement {
            target,
            text,
            effects,
            duration_seconds,
            async_run,
        })));
    }

    if let Some(open_index) = lower.find(".ease(") {
        let path_text = &line[..open_index];
        let Some(target) = parse_ui_target_path(path_text) else {
            return Ok(None);
        };
        let args = parse_call_args(&line[open_index + ".ease".len()..]);
        let Some(easing) = args.first().cloned() else {
            return Ok(None);
        };
        let direction = args
            .get(1)
            .and_then(|value| parse_ui_ease_direction(value))
            .unwrap_or(UiEaseDirection::Up);
        let duration_seconds = args
            .get(2)
            .and_then(|value| value.parse::<f32>().ok())
            .unwrap_or(0.0);
        let call_open_index = open_index + ".ease".len();
        let async_run = contains_keyword_after_matching_call(line, call_open_index, "async");
        return Ok(Some(Statement::UiEase(UiEaseStatement {
            target,
            easing,
            direction,
            duration_seconds,
            async_run,
        })));
    }

    if let Some((left, right)) = line.split_once('=') {
        let left = left.trim();
        if left.to_ascii_lowercase().starts_with("ui.") {
            let mut parts = left.rsplitn(2, '.');
            let property = parts.next().unwrap_or_default().trim();
            let path_text = parts.next().unwrap_or_default().trim();
            if !property.is_empty()
                && let Some(target) = parse_ui_target_path(path_text)
                && let Some(value) = parse_ui_property_value(right)?
            {
                return Ok(Some(Statement::UiSetProperty(UiSetPropertyStatement {
                    target,
                    property: property.to_owned(),
                    value,
                })));
            }
        }
    }

    Ok(None)
}

fn parse_ui_target_path(text: &str) -> Option<UiTargetPath> {
    let text = text.trim();
    if !starts_with_case_insensitive(text, "UI.") {
        return None;
    }
    let parts = text["UI.".len()..]
        .split('.')
        .map(str::trim)
        .filter(|part| !part.is_empty())
        .collect::<Vec<_>>();
    if parts.is_empty() {
        return None;
    }
    let first = *parts.first()?;
    let (ui_name, layer_index) = if is_probable_ui_layer_name(first) || parts.len() == 1 {
        (None, 0)
    } else {
        (Some(first.to_owned()), 1)
    };
    let layer = parts.get(layer_index)?.to_string();
    let widget_path = parts[layer_index + 1..]
        .iter()
        .map(|part| (*part).to_owned())
        .collect::<Vec<_>>();
    Some(UiTargetPath {
        ui_name,
        layer,
        widget_path,
    })
}

fn is_probable_ui_layer_name(name: &str) -> bool {
    name.to_ascii_lowercase().starts_with("layer")
}

fn parse_ui_ease_direction(text: &str) -> Option<UiEaseDirection> {
    match text.trim().to_ascii_lowercase().as_str() {
        "up" => Some(UiEaseDirection::Up),
        "down" => Some(UiEaseDirection::Down),
        "left" => Some(UiEaseDirection::Left),
        "right" => Some(UiEaseDirection::Right),
        _ => None,
    }
}

fn parse_camera_system_select(line: &str) -> Option<String> {
    let (left, right) = line.split_once('=')?;
    if !left.trim().eq_ignore_ascii_case("camera.system") {
        return None;
    }
    let name = right.trim();
    if is_variable_name(name) {
        Some(name.to_owned())
    } else {
        None
    }
}

fn parse_logger_statement(line: &str) -> Result<Option<LoggerStatement>, ParseError> {
    let line = line.trim();
    let lower = line.to_ascii_lowercase();
    if !lower.starts_with("logger.") {
        return Ok(None);
    }
    let Some((level_text, message_text)) = line["logger.".len()..].split_once(char::is_whitespace)
    else {
        return Ok(None);
    };
    let level = match level_text.to_ascii_lowercase().as_str() {
        "info" => LoggerLevel::Info,
        "debug" => LoggerLevel::Debug,
        "error" => LoggerLevel::Error,
        _ => return Ok(None),
    };
    let message_text = message_text.trim();
    if message_text.is_empty() {
        return Ok(None);
    }
    let message = if let Some(text) = parse_quoted_strings(message_text).into_iter().next() {
        LoggerMessage::Text(text)
    } else {
        let Some(value) = parse_assignment_value(message_text)? else {
            return Ok(None);
        };
        LoggerMessage::Value(value)
    };
    Ok(Some(LoggerStatement { level, message }))
}

fn parse_debug_mode_statement(line: &str) -> Option<Statement> {
    match line.trim().to_ascii_lowercase().as_str() {
        "debug.on" => Some(Statement::DebugMode { enabled: true }),
        "debug.off" => Some(Statement::DebugMode { enabled: false }),
        _ => None,
    }
}

fn parse_screen_mode(line: &str) -> Option<ScreenMode> {
    let mode_text = strip_keyword_prefix(line.trim(), "screen.mode")?.trim();
    if mode_text.is_empty() {
        return None;
    }
    let normalized = mode_text.to_ascii_lowercase();
    if normalized.starts_with("no border") {
        return Some(ScreenMode::Borderless);
    }
    let mode = mode_text
        .split(|value: char| value.is_whitespace() || value == ',' || value == ':')
        .next()
        .unwrap_or_default();
    match mode.to_ascii_lowercase().as_str() {
        "window" => Some(ScreenMode::Window),
        "full" | "fullscreen" => Some(ScreenMode::Full),
        "borderless" | "noborder" | "noborders" => Some(ScreenMode::Borderless),
        _ => None,
    }
}

fn parse_run_function_statement(line: &str) -> Option<Statement> {
    let lower = line.to_ascii_lowercase();
    if lower.contains(" every ") || !starts_with_keyword(line, "run") {
        return None;
    }
    let after_run = line["run".len()..].trim();
    let name = after_run
        .split(|value: char| value.is_whitespace() || value == '(')
        .next()
        .unwrap_or_default()
        .trim();
    if !is_variable_name(name) {
        return None;
    }

    let run = Statement::RunFunction {
        name: name.to_owned(),
        args: parse_call_args(after_run),
    };
    if lower
        .split_whitespace()
        .any(|part| part.eq_ignore_ascii_case("async"))
    {
        Some(Statement::Async { actions: vec![run] })
    } else {
        Some(run)
    }
}

fn parse_run_every(line: &str) -> Result<Option<(String, AssignmentValue, f32)>, ParseError> {
    let lower = line.to_ascii_lowercase();
    if !starts_with_keyword(line, "run") || !lower.contains(" every ") {
        return Ok(None);
    }
    let after_run = line["run".len()..].trim();
    let Some((call_text, every_text)) = split_once_case_insensitive(after_run, " every ") else {
        return Ok(None);
    };
    let name = call_text
        .split(|value: char| value.is_whitespace() || value == '(')
        .next()
        .unwrap_or_default()
        .trim();
    if !is_variable_name(name) {
        return Ok(None);
    }
    let raw_interval = take_until_clause(every_text.trim(), &[" seconds", " second", " async"]);
    if raw_interval.is_empty() {
        return Ok(None);
    }
    let Some(interval_value) = parse_assignment_value(raw_interval)? else {
        return Err(ParseError::InvalidNumber(raw_interval.to_owned()));
    };
    let interval_seconds = static_assignment_number(&interval_value).unwrap_or(0.0);
    Ok(Some((name.to_owned(), interval_value, interval_seconds)))
}

fn parse_call_args(text: &str) -> Vec<String> {
    let Some(open_index) = text.find('(') else {
        return Vec::new();
    };
    let mut args = Vec::new();
    let mut current = String::new();
    let mut quote: Option<char> = None;
    let mut escaped = false;
    let mut depth = 0usize;

    for ch in text[open_index + 1..].chars() {
        if let Some(quote_char) = quote {
            current.push(ch);
            if escaped {
                escaped = false;
            } else if ch == '\\' {
                escaped = true;
            } else if ch == quote_char {
                quote = None;
            }
            continue;
        }

        match ch {
            '"' | '\'' => {
                quote = Some(ch);
                current.push(ch);
            }
            '(' | '[' | '{' => {
                depth += 1;
                current.push(ch);
            }
            ')' if depth == 0 => {
                let arg = clean_call_arg(&current);
                if !arg.is_empty() {
                    args.push(arg.to_owned());
                }
                return args;
            }
            ')' | ']' | '}' => {
                depth = depth.saturating_sub(1);
                current.push(ch);
            }
            ',' if depth == 0 => {
                let arg = clean_call_arg(&current);
                if !arg.is_empty() {
                    args.push(arg.to_owned());
                }
                current.clear();
            }
            _ => current.push(ch),
        }
    }

    Vec::new()
}

fn contains_keyword_after_matching_call(text: &str, open_index: usize, keyword: &str) -> bool {
    let mut quote: Option<char> = None;
    let mut escaped = false;
    let mut depth = 0usize;

    for (offset, ch) in text[open_index + 1..].char_indices() {
        if let Some(quote_char) = quote {
            if escaped {
                escaped = false;
            } else if ch == '\\' {
                escaped = true;
            } else if ch == quote_char {
                quote = None;
            }
            continue;
        }

        match ch {
            '"' | '\'' => quote = Some(ch),
            '(' | '[' | '{' => depth += 1,
            ')' if depth == 0 => {
                let suffix_start = open_index + 1 + offset + ch.len_utf8();
                return contains_keyword(&text[suffix_start..], keyword);
            }
            ')' | ']' | '}' => depth = depth.saturating_sub(1),
            _ => {}
        }
    }

    false
}

fn clean_call_arg(arg: &str) -> &str {
    arg.trim().trim_matches('"').trim_matches('\'').trim()
}

fn parse_ui_property_value(raw_value: &str) -> Result<Option<UiPropertyValue>, ParseError> {
    let raw_value = raw_value.trim().trim_end_matches(';').trim();
    if raw_value.is_empty() {
        return Ok(None);
    }
    if is_quoted(raw_value) {
        return Ok(Some(UiPropertyValue::Literal(unquote_ui_text(raw_value))));
    }
    if let Some(parts) = parse_ui_property_concatenation(raw_value)? {
        return Ok(Some(UiPropertyValue::Concatenation(parts)));
    }
    if let Some(value) = parse_assignment_value(clean_assignment_value(raw_value))? {
        return Ok(Some(UiPropertyValue::Expression(value)));
    }
    Ok(Some(UiPropertyValue::Literal(
        clean_call_arg(raw_value).to_owned(),
    )))
}

fn parse_ui_property_concatenation(
    raw_value: &str,
) -> Result<Option<Vec<UiPropertyValuePart>>, ParseError> {
    let parts = split_top_level_plus(raw_value);
    if parts.len() < 2 || !parts.iter().any(|part| is_quoted(part)) {
        return Ok(None);
    }

    let mut values = Vec::new();
    for part in parts {
        if is_quoted(part) {
            values.push(UiPropertyValuePart::Literal(unquote_ui_text(part)));
        } else if let Some(value) = parse_assignment_value(clean_assignment_value(part))? {
            values.push(UiPropertyValuePart::Expression(value));
        } else {
            values.push(UiPropertyValuePart::Literal(
                clean_call_arg(part).to_owned(),
            ));
        }
    }
    Ok(Some(values))
}

fn split_top_level_plus(text: &str) -> Vec<&str> {
    let mut parts = Vec::new();
    let mut depth = 0usize;
    let mut quote: Option<char> = None;
    let mut escaped = false;
    let mut start = 0usize;

    for (index, value) in text.char_indices() {
        if let Some(quote_char) = quote {
            if escaped {
                escaped = false;
            } else if value == '\\' {
                escaped = true;
            } else if value == quote_char {
                quote = None;
            }
            continue;
        }

        match value {
            '"' | '\'' => quote = Some(value),
            '(' | '[' | '{' => depth += 1,
            ')' | ']' | '}' => depth = depth.saturating_sub(1),
            '+' if depth == 0 => {
                parts.push(text[start..index].trim());
                start = index + value.len_utf8();
            }
            _ => {}
        }
    }

    parts.push(text[start..].trim());
    parts.into_iter().filter(|part| !part.is_empty()).collect()
}

fn is_quoted(text: &str) -> bool {
    let text = text.trim();
    (text.len() >= 2 && text.starts_with('"') && text.ends_with('"'))
        || (text.len() >= 2 && text.starts_with('\'') && text.ends_with('\''))
}

fn unquote_ui_text(text: &str) -> String {
    let text = text.trim();
    if is_quoted(text) {
        text[1..text.len() - 1].to_owned()
    } else {
        text.to_owned()
    }
}

fn split_once_case_insensitive<'a>(text: &'a str, needle: &str) -> Option<(&'a str, &'a str)> {
    let (start, end) = find_case_insensitive_span(text, needle)?;
    Some((&text[..start], &text[end..]))
}

fn split_once_case_insensitive_outside_quotes<'a>(
    text: &'a str,
    needle: &str,
) -> Option<(&'a str, &'a str)> {
    let mut quote: Option<char> = None;
    for (index, value) in text.char_indices() {
        match value {
            '"' | '\'' => {
                if quote == Some(value) {
                    quote = None;
                } else if quote.is_none() {
                    quote = Some(value);
                }
            }
            _ if quote.is_none()
                && text[index..]
                    .get(..needle.len())
                    .is_some_and(|candidate| candidate.eq_ignore_ascii_case(needle)) =>
            {
                return Some((&text[..index], &text[index + needle.len()..]));
            }
            _ => {}
        }
    }
    None
}

fn find_case_insensitive_span(text: &str, needle: &str) -> Option<(usize, usize)> {
    if !needle.chars().any(char::is_whitespace) {
        let index = text
            .to_ascii_lowercase()
            .find(&needle.to_ascii_lowercase())?;
        return Some((index, index + needle.len()));
    }

    let tokens = needle.split_whitespace().collect::<Vec<_>>();
    let first = tokens.first()?;
    let lower = text.to_ascii_lowercase();
    let first_lower = first.to_ascii_lowercase();
    let include_leading_ws = needle.chars().next().is_some_and(char::is_whitespace);
    let include_trailing_ws = needle.chars().last().is_some_and(char::is_whitespace);
    let mut search_from = 0usize;

    while let Some(relative_index) = lower[search_from..].find(&first_lower) {
        let token_start = search_from + relative_index;
        let mut span_start = token_start;

        if include_leading_ws {
            if token_start == 0 {
                search_from = token_start + first.len();
                continue;
            }
            let mut cursor = token_start;
            while let Some((prev_index, prev_char)) = text[..cursor].char_indices().next_back() {
                if !prev_char.is_whitespace() {
                    break;
                }
                cursor = prev_index;
            }
            if cursor == token_start {
                search_from = token_start + first.len();
                continue;
            }
            span_start = cursor;
        }

        let mut cursor = token_start;
        let mut matched = true;
        for (token_index, token) in tokens.iter().enumerate() {
            let token_len = token.len();
            let Some(candidate) = text.get(cursor..cursor + token_len) else {
                matched = false;
                break;
            };
            if !candidate.eq_ignore_ascii_case(token) {
                matched = false;
                break;
            }
            cursor += token_len;

            if token_index + 1 < tokens.len() {
                let next_cursor = consume_required_whitespace(text, cursor);
                if next_cursor == cursor {
                    matched = false;
                    break;
                }
                cursor = next_cursor;
            }
        }

        if !matched {
            search_from = token_start + first.len();
            continue;
        }

        if include_trailing_ws {
            let next_cursor = consume_required_whitespace(text, cursor);
            if next_cursor == cursor {
                search_from = token_start + first.len();
                continue;
            }
            cursor = next_cursor;
        }

        return Some((span_start, cursor));
    }

    None
}

fn consume_required_whitespace(text: &str, cursor: usize) -> usize {
    let mut next_cursor = cursor;
    while let Some(value) = text[next_cursor..].chars().next() {
        if !value.is_whitespace() {
            break;
        }
        next_cursor += value.len_utf8();
    }
    next_cursor
}

fn starts_with_case_insensitive(text: &str, prefix: &str) -> bool {
    text.len() >= prefix.len() && text[..prefix.len()].eq_ignore_ascii_case(prefix)
}

fn strip_suffix_case_insensitive<'a>(text: &'a str, suffix: &str) -> Option<&'a str> {
    (text.len() >= suffix.len() && text[text.len() - suffix.len()..].eq_ignore_ascii_case(suffix))
        .then(|| &text[..text.len() - suffix.len()])
}

fn parse_fighting_camera(line: &str) -> Result<Option<FightingCameraStatement>, ParseError> {
    let Some((name, rest)) = line.split_once('=') else {
        return Ok(None);
    };
    let rest = rest.trim();
    let Some(args_text) = rest
        .strip_prefix("camera.system.fighting")
        .and_then(|after| after.trim().strip_prefix('('))
        .and_then(|after| after.strip_suffix(')'))
    else {
        return Ok(None);
    };

    let args = args_text.split(',').map(str::trim).collect::<Vec<_>>();
    let (Some(target_a), Some(target_b)) = (args.first(), args.get(1)) else {
        return Ok(None);
    };
    if !is_variable_name(name.trim()) || !is_variable_name(target_a) || !is_variable_name(target_b)
    {
        return Ok(None);
    }

    Ok(Some(FightingCameraStatement {
        name: name.trim().to_owned(),
        target_a: (*target_a).to_owned(),
        target_b: (*target_b).to_owned(),
        depth: parse_named_argument(args_text, "depth")?.unwrap_or(18.0),
        height: parse_named_argument(args_text, "height")?.unwrap_or(4.0),
        side: parse_named_argument(args_text, "side")?.unwrap_or(0.0),
        min_distance: parse_named_argument(args_text, "min distance")?.unwrap_or(10.0),
        max_distance: parse_named_argument(args_text, "max distance")?.unwrap_or(24.0),
        zoom_factor: parse_named_argument(args_text, "zoom factor")?
            .or(parse_named_argument(args_text, "zoom_factor")?)
            .or(parse_named_argument(args_text, "zoomfactor")?)
            .unwrap_or(1.15),
        damping: parse_named_argument(args_text, "damping")?.unwrap_or(7.5),
        look_ahead: parse_named_argument(args_text, "look ahead")?.unwrap_or(0.0),
        fov: parse_named_argument(args_text, "fov")?.unwrap_or(50.0),
        max_fov: parse_named_argument(args_text, "max fov")?.unwrap_or(62.0),
    }))
}

fn parse_third_person_camera(line: &str) -> Result<Option<ThirdPersonCameraStatement>, ParseError> {
    let Some((name, rest)) = line.split_once('=') else {
        return Ok(None);
    };
    let rest = rest.trim();
    let Some(args_text) = rest
        .strip_prefix("camera.system.third_person")
        .and_then(|after| after.trim().strip_prefix('('))
        .and_then(|after| after.strip_suffix(')'))
    else {
        return Ok(None);
    };

    let args = args_text.split(',').map(str::trim).collect::<Vec<_>>();
    let Some(target) = args.first() else {
        return Ok(None);
    };
    if !is_variable_name(name.trim()) || !is_variable_name(target) {
        return Ok(None);
    }

    Ok(Some(ThirdPersonCameraStatement {
        name: name.trim().to_owned(),
        target: (*target).to_owned(),
        distance: parse_named_argument(args_text, "distance")?.unwrap_or(12.0),
        height: parse_named_argument(args_text, "height")?.unwrap_or(3.0),
        side: parse_named_argument(args_text, "side")?.unwrap_or(0.0),
        look_ahead: parse_named_argument(args_text, "look ahead")?.unwrap_or(0.0),
        damping: parse_named_argument(args_text, "damping")?.unwrap_or(8.0),
        fov: parse_named_argument(args_text, "fov")?.unwrap_or(60.0),
        max_fov: parse_named_argument(args_text, "max fov")?.unwrap_or(60.0),
    }))
}

fn parse_named_argument(text: &str, name: &str) -> Result<Option<f32>, ParseError> {
    let lower = text.to_ascii_lowercase();
    let Some(index) = lower.find(&name.to_ascii_lowercase()) else {
        return Ok(None);
    };
    let raw = text[index + name.len()..]
        .trim()
        .split(|value: char| value.is_whitespace() || value == ',')
        .find(|part| !part.is_empty())
        .unwrap_or_default();
    if raw.is_empty() {
        return Ok(None);
    }
    Ok(raw.parse::<f32>().ok())
}

fn parse_assignment(line: &str) -> Result<Option<AssignmentStatement>, ParseError> {
    let Some(mut assignments) = parse_assignment_list(line)? else {
        return Ok(None);
    };
    if assignments.len() == 1 {
        Ok(assignments.pop())
    } else {
        Ok(None)
    }
}

fn is_local_assignment_line(line: &str) -> bool {
    let line = line.trim();
    starts_with_keyword(line, "var") && !strip_var_prefix(line).trim_start().starts_with('@')
}

fn is_shared_var_line(line: &str) -> bool {
    starts_with_two_keywords(line.trim(), "shared", "var")
}

fn parse_assignment_list(line: &str) -> Result<Option<Vec<AssignmentStatement>>, ParseError> {
    let line = line.trim();
    if line.contains("=>") || line.starts_with('[') {
        return Ok(None);
    }

    let normalized = strip_var_prefix(line);
    if normalized.starts_with('@') || normalized.to_ascii_lowercase().starts_with("camera.system") {
        return Ok(None);
    }

    let mut assignments = Vec::new();
    for segment in split_assignment_segments(normalized) {
        let Some((name, raw_value)) = segment.split_once('=') else {
            continue;
        };
        let name = name.trim();
        if name.starts_with('@') || !is_variable_path(name) {
            continue;
        }
        let raw_value = clean_assignment_value(raw_value);
        let Some(value) = parse_assignment_value(raw_value)? else {
            continue;
        };
        assignments.push(AssignmentStatement {
            name: name.to_owned(),
            value,
        });
    }

    Ok((!assignments.is_empty()).then_some(assignments))
}

fn parse_weapon_statement(line: &str) -> Option<WeaponStatement> {
    let line = line.trim().trim_end_matches(';').trim();
    let lower = line.to_ascii_lowercase();
    let index = lower.find(".weapon")?;
    let owner = line[..index].trim();
    if !is_variable_path(owner) {
        return None;
    }
    let rest = line[index + ".weapon".len()..].trim();
    if let Some(raw_value) = rest.strip_prefix('=') {
        let raw_value = clean_assignment_value(raw_value);
        if raw_value.eq_ignore_ascii_case("empty") {
            return Some(WeaponStatement {
                owner: owner.to_owned(),
                action: WeaponAction::Unequip,
            });
        }
        let weapon = if is_quoted(raw_value) {
            unquote_ui_text(raw_value)
        } else {
            clean_call_arg(raw_value).to_owned()
        };
        return (!weapon.is_empty()).then(|| WeaponStatement {
            owner: owner.to_owned(),
            action: WeaponAction::Equip { weapon },
        });
    }
    let rest_lower = rest.to_ascii_lowercase();
    if rest_lower.starts_with(".posture") {
        let after_posture = rest[".posture".len()..].trim();
        if let Some(raw_value) = after_posture.strip_prefix('=') {
            let raw_value = clean_assignment_value(raw_value);
            let posture = if is_quoted(raw_value) {
                unquote_ui_text(raw_value)
            } else {
                clean_call_arg(raw_value).to_owned()
            };
            return (!posture.is_empty()).then(|| WeaponStatement {
                owner: owner.to_owned(),
                action: WeaponAction::Posture { posture },
            });
        }
    }
    if rest.eq_ignore_ascii_case(".detach") || rest.eq_ignore_ascii_case(".detach()") {
        return Some(WeaponStatement {
            owner: owner.to_owned(),
            action: WeaponAction::Detach,
        });
    }
    None
}

fn parse_runtime_object_event_block(
    logical_lines: &[String],
    index: usize,
    animation_controller_names: &[String],
    throw_motion_names: &[String],
) -> Result<Option<(Statement, usize)>, ParseError> {
    let line = logical_lines[index].trim();
    let Some(name) = parse_runtime_event_object_name(line) else {
        return Ok(None);
    };
    if contains_runtime_object_name(animation_controller_names, &name) {
        return Ok(
            parse_animation_controller_event_block(logical_lines, index)?.map(
                |(event, next_index)| (Statement::AnimationControllerEvent(event), next_index),
            ),
        );
    }
    if contains_runtime_object_name(throw_motion_names, &name) {
        return Ok(parse_throw_motion_event_block(logical_lines, index)?
            .map(|(event, next_index)| (Statement::ThrowMotionEvent(event), next_index)));
    }
    Ok(parse_throw_motion_event_block(logical_lines, index)?
        .map(|(event, next_index)| (Statement::ThrowMotionEvent(event), next_index)))
}

fn parse_runtime_event_object_name(line: &str) -> Option<String> {
    let (name, rest) = split_dot_command_rest(line)?;
    let rest = rest.trim_start();
    rest.to_ascii_lowercase()
        .starts_with("event(")
        .then(|| name)
}

fn contains_runtime_object_name(names: &[String], name: &str) -> bool {
    names.iter().any(|existing| existing == name)
}

fn record_runtime_object_statements(
    statements: &[Statement],
    animation_controller_names: &mut Vec<String>,
    throw_motion_names: &mut Vec<String>,
) {
    for statement in statements {
        match statement {
            Statement::Assignment(assignment)
            | Statement::SharedAssignment(assignment)
            | Statement::LocalAssignment(assignment) => record_runtime_object_assignment(
                assignment,
                animation_controller_names,
                throw_motion_names,
            ),
            _ => {}
        }
    }
}

fn record_runtime_object_assignments(
    assignments: &[AssignmentStatement],
    animation_controller_names: &mut Vec<String>,
    throw_motion_names: &mut Vec<String>,
) {
    for assignment in assignments {
        record_runtime_object_assignment(
            assignment,
            animation_controller_names,
            throw_motion_names,
        );
    }
}

fn record_runtime_object_assignment(
    assignment: &AssignmentStatement,
    animation_controller_names: &mut Vec<String>,
    throw_motion_names: &mut Vec<String>,
) {
    match assignment.value {
        AssignmentValue::AnimationController(_) => {
            remember_runtime_object_name(animation_controller_names, &assignment.name);
        }
        AssignmentValue::ThrowMotion(_) => {
            remember_runtime_object_name(throw_motion_names, &assignment.name);
        }
        _ => {}
    }
}

fn remember_runtime_object_name(names: &mut Vec<String>, name: &str) {
    if !contains_runtime_object_name(names, name) {
        names.push(name.to_owned());
    }
}

fn parse_animation_controller_action(line: &str) -> Option<AnimationControllerActionStatement> {
    let line = line.trim().trim_end_matches(';').trim();
    let Some((controller, rest)) = split_dot_command_rest(line) else {
        return None;
    };
    if !is_variable_path(&controller) {
        return None;
    }
    let action = match rest.trim().to_ascii_lowercase().as_str() {
        "run" | "run()" => AnimationControllerAction::Run,
        "stop" | "stop()" => AnimationControllerAction::Stop,
        _ => return None,
    };
    Some(AnimationControllerActionStatement { controller, action })
}

fn parse_animation_controller_event_block(
    logical_lines: &[String],
    index: usize,
) -> Result<Option<(AnimationControllerEventStatement, usize)>, ParseError> {
    let line = logical_lines[index].trim();
    let Some(mut event) = parse_animation_controller_event_header(line, Vec::new())? else {
        return Ok(None);
    };
    if line.contains("{ }") || line.contains("{}") || !line.ends_with('{') {
        return Ok(Some((event, index + 1)));
    }
    let (actions, next_index) = parse_action_block(logical_lines, index + 1, false)?;
    event.actions = actions;
    Ok(Some((event, next_index)))
}

fn parse_animation_controller_event_header(
    line: &str,
    actions: Vec<Statement>,
) -> Result<Option<AnimationControllerEventStatement>, ParseError> {
    let line = line.trim().trim_end_matches(';').trim();
    let Some((controller, rest)) = split_dot_command_rest(line) else {
        return Ok(None);
    };
    if !is_variable_path(&controller)
        || !rest.trim_start().to_ascii_lowercase().starts_with("event(")
    {
        return Ok(None);
    }
    let args = parse_call_args(rest);
    let (Some(animation), Some(percent)) = (args.first(), args.get(1)) else {
        return Ok(None);
    };
    let Some(percent) = parse_assignment_value(percent)? else {
        return Err(ParseError::InvalidNumber(percent.to_owned()));
    };
    Ok(Some(AnimationControllerEventStatement {
        controller,
        animation: animation.to_owned(),
        percent,
        actions,
    }))
}

fn parse_throw_motion_apply(line: &str) -> Option<ThrowMotionApplyStatement> {
    let line = line.trim().trim_end_matches(';').trim();
    let Some((motion, rest)) = split_dot_command_rest(line) else {
        return None;
    };
    if !is_variable_path(&motion) {
        return None;
    }
    let rest = rest.trim();
    if !starts_with_keyword(rest, "apply") {
        return None;
    }
    let mut target = rest["apply".len()..].trim();
    let async_run = target
        .split_whitespace()
        .last()
        .is_some_and(|word| word.eq_ignore_ascii_case("async"));
    if async_run {
        target = target[..target.rfind(char::is_whitespace).unwrap_or(target.len())].trim();
    }
    let target = target.trim_end_matches(';').trim();
    let lower_target = target.to_ascii_lowercase();
    if lower_target.ends_with(".weapon") {
        let owner = target[..target.len() - ".weapon".len()].trim();
        return is_variable_path(owner).then(|| ThrowMotionApplyStatement {
            motion,
            target: owner.to_owned(),
            target_is_equipped_weapon: true,
            async_run,
        });
    }
    is_variable_path(target).then(|| ThrowMotionApplyStatement {
        motion,
        target: target.to_owned(),
        target_is_equipped_weapon: false,
        async_run,
    })
}

fn parse_throw_motion_event_block(
    logical_lines: &[String],
    index: usize,
) -> Result<Option<(ThrowMotionEventStatement, usize)>, ParseError> {
    let line = logical_lines[index].trim();
    let Some(mut event) = parse_throw_motion_event_header(line, Vec::new())? else {
        return Ok(None);
    };
    if line.contains("{ }") || line.contains("{}") || !line.ends_with('{') {
        return Ok(Some((event, index + 1)));
    }
    let (actions, next_index) = parse_action_block(logical_lines, index + 1, false)?;
    event.actions = actions;
    Ok(Some((event, next_index)))
}

fn parse_throw_motion_event_header(
    line: &str,
    actions: Vec<Statement>,
) -> Result<Option<ThrowMotionEventStatement>, ParseError> {
    let line = line.trim().trim_end_matches(';').trim();
    let Some((motion, rest)) = split_dot_command_rest(line) else {
        return Ok(None);
    };
    if !is_variable_path(&motion) || !rest.trim_start().to_ascii_lowercase().starts_with("event(") {
        return Ok(None);
    }
    let args = parse_call_args(rest);
    let Some(event) = args.first().filter(|event| !event.is_empty()) else {
        return Ok(None);
    };
    let index_percent = args
        .get(1)
        .map(|raw| {
            parse_assignment_value(raw)?.ok_or_else(|| ParseError::InvalidNumber(raw.to_owned()))
        })
        .transpose()?;
    Ok(Some(ThrowMotionEventStatement {
        motion,
        event: event.to_owned(),
        index_percent,
        actions,
    }))
}

fn split_assignment_segments(text: &str) -> Vec<&str> {
    let mut segments = Vec::new();
    let mut depth = 0usize;
    let mut start = 0usize;
    for (index, value) in text.char_indices() {
        match value {
            '(' => depth += 1,
            ')' => depth = depth.saturating_sub(1),
            ',' if depth == 0 => {
                let segment = text[start..index].trim();
                if !segment.is_empty() {
                    segments.push(segment);
                }
                start = index + value.len_utf8();
            }
            _ => {}
        }
    }
    let segment = text[start..].trim();
    if !segment.is_empty() {
        segments.push(segment);
    }
    segments
}

fn clean_assignment_value(raw_value: &str) -> &str {
    raw_value
        .split("//")
        .next()
        .unwrap_or_default()
        .split('[')
        .next()
        .unwrap_or_default()
        .trim()
        .trim_end_matches(';')
        .trim_end_matches(',')
        .trim()
}

fn parse_assignment_value(raw_value: &str) -> Result<Option<AssignmentValue>, ParseError> {
    let raw_value = strip_wrapping_parens(raw_value.trim());
    if raw_value.is_empty() {
        return Ok(None);
    }
    if let Ok(value) = raw_value.parse::<f32>() {
        return Ok(Some(AssignmentValue::Number(value)));
    }
    if let Some(max) = parse_function_assignment_arg(raw_value, "rnd") {
        let Some(max) = parse_assignment_value(max)? else {
            return Ok(None);
        };
        return Ok(Some(AssignmentValue::RandomInt { max: Box::new(max) }));
    }
    if let Some(raw_value) = parse_function_assignment_arg(raw_value, "round") {
        let Some(value) = parse_assignment_value(raw_value)? else {
            return Ok(None);
        };
        return Ok(Some(AssignmentValue::Round {
            value: Box::new(value),
        }));
    }
    if let Some(args) = parse_function_assignment_args(raw_value, "distance") {
        if args.len() == 2 && is_variable_path(args[0]) && is_variable_path(args[1]) {
            return Ok(Some(AssignmentValue::Distance {
                left: args[0].to_owned(),
                right: args[1].to_owned(),
            }));
        }
    }
    if let Some(value) = parse_animation_controller_value(raw_value) {
        return Ok(Some(AssignmentValue::AnimationController(value)));
    }
    if let Some(value) = parse_throw_motion_value(raw_value)? {
        return Ok(Some(AssignmentValue::ThrowMotion(Box::new(value))));
    }
    if let Some(pool) = raw_value.strip_suffix(".acquire") {
        let pool = pool.trim();
        if is_variable_path(pool) {
            return Ok(Some(AssignmentValue::PoolAcquire {
                pool: pool.to_owned(),
            }));
        }
    }
    if let Some(modifier) = parse_camera_modifier_value(raw_value) {
        return Ok(Some(AssignmentValue::CameraModifier(modifier)));
    }
    if let Some((left, operator, right)) = split_assignment_binary(raw_value) {
        let Some(left) = parse_assignment_value(left)? else {
            return Ok(None);
        };
        let Some(right) = parse_assignment_value(right)? else {
            return Ok(None);
        };
        return Ok(Some(AssignmentValue::Binary {
            left: Box::new(left),
            operator,
            right: Box::new(right),
        }));
    }
    if raw_value.eq_ignore_ascii_case("true") {
        return Ok(Some(AssignmentValue::Condition(Box::new(
            Condition::Boolean(true),
        ))));
    }
    if raw_value.eq_ignore_ascii_case("false") {
        return Ok(Some(AssignmentValue::Condition(Box::new(
            Condition::Boolean(false),
        ))));
    }
    if is_variable_path(raw_value) {
        return Ok(Some(AssignmentValue::Symbol(raw_value.to_owned())));
    }
    if let Some(condition) = parse_condition(raw_value)? {
        return Ok(Some(AssignmentValue::Condition(Box::new(condition))));
    }
    Ok(None)
}

fn parse_animation_controller_value(raw_value: &str) -> Option<AnimationControllerValue> {
    let after_animation = strip_keyword_prefix(raw_value, "animation")?.trim();
    let (target, clip) = split_animation_controller_reference(after_animation)?;
    Some(AnimationControllerValue { target, clip })
}

fn split_animation_controller_reference(text: &str) -> Option<(String, String)> {
    let text = text.trim();
    if text.is_empty() {
        return None;
    }
    if let Some(quote_start) = text.find('"') {
        let target = text[..quote_start].trim().trim_end_matches('.').trim();
        let after_quote = &text[quote_start + 1..];
        let quote_end = after_quote.find('"')?;
        let clip = after_quote[..quote_end].trim();
        return (!target.is_empty() && !clip.is_empty() && is_variable_path(target))
            .then(|| (target.to_owned(), clip.to_owned()));
    }
    let (target, clip) = text.rsplit_once('.')?;
    let target = target.trim();
    let clip = clip.trim();
    (!target.is_empty() && !clip.is_empty() && is_variable_path(target) && is_variable_name(clip))
        .then(|| (target.to_owned(), clip.to_owned()))
}

fn parse_throw_motion_value(raw_value: &str) -> Result<Option<ThrowMotionValue>, ParseError> {
    let Some(args) = parse_function_assignment_args(raw_value, "system.motion") else {
        return Ok(None);
    };
    let Some(asset_arg) = args
        .first()
        .map(|arg| arg.trim())
        .filter(|arg| !arg.is_empty())
    else {
        return Ok(None);
    };
    let asset = if is_quoted(asset_arg) {
        ThrowMotionAsset::Literal(unquote_ui_text(asset_arg))
    } else {
        let Some(value) = parse_assignment_value(asset_arg)? else {
            return Ok(None);
        };
        ThrowMotionAsset::Expression(Box::new(value))
    };
    let target = args
        .iter()
        .skip(1)
        .find_map(|arg| parse_throw_motion_target_arg(arg).transpose())
        .transpose()?;
    Ok(Some(ThrowMotionValue { asset, target }))
}

fn parse_throw_motion_target_arg(raw_arg: &str) -> Result<Option<ThrowMotionTarget>, ParseError> {
    let arg = raw_arg.trim();
    let lower = arg.to_ascii_lowercase();
    if !lower.starts_with("target") {
        return Ok(None);
    }
    let value = arg["target".len()..]
        .trim_start()
        .strip_prefix('=')
        .unwrap_or(arg["target".len()..].trim_start())
        .trim();
    if value.starts_with('(') && value.ends_with(')') {
        let parts = split_top_level_comma(&value[1..value.len() - 1]);
        if parts.len() != 3 {
            return Ok(None);
        }
        let x = parse_throw_motion_target_value(parts[0])?;
        let y = parse_throw_motion_target_value(parts[1])?;
        let z = parse_throw_motion_target_value(parts[2])?;
        return Ok(Some(ThrowMotionTarget::Position([x, y, z])));
    }
    Ok(is_variable_path(value).then(|| ThrowMotionTarget::Object(value.to_owned())))
}

fn parse_throw_motion_target_value(raw_value: &str) -> Result<AssignmentValue, ParseError> {
    parse_assignment_value(raw_value.trim())?
        .ok_or_else(|| ParseError::InvalidNumber(raw_value.trim().to_owned()))
}

fn parse_camera_modifier_value(raw_value: &str) -> Option<CameraModifierValue> {
    let modifier_type = raw_value
        .strip_prefix("camera.system.modifiers.")
        .or_else(|| raw_value.strip_prefix("Camera.System.Modifiers."))?
        .trim()
        .to_ascii_lowercase();
    create_camera_modifier_preset(&modifier_type)
}

fn create_camera_modifier_preset(modifier_type: &str) -> Option<CameraModifierValue> {
    let mut value = CameraModifierValue {
        modifier_type: modifier_type.to_owned(),
        duration: 0.3,
        amplitude: 1.0,
        frequency: 12.0,
        x: 0.08,
        y: 0.08,
        z: 0.08,
        rx: 0.8,
        ry: 0.8,
        rz: 0.8,
        fov: 0.4,
    };
    match modifier_type {
        "hit_modifier" => {
            value.duration = 0.24;
            value.amplitude = 1.0;
            value.frequency = 16.0;
            value.x = 0.18;
            value.y = 0.08;
            value.z = 0.12;
            value.rx = 1.8;
            value.ry = 2.8;
            value.rz = 1.0;
            value.fov = 0.6;
        }
        "fall_modifier" => {
            value.duration = 0.65;
            value.amplitude = 0.55;
            value.frequency = 5.0;
            value.x = 0.04;
            value.y = 0.16;
            value.z = 0.06;
            value.rx = 0.6;
            value.ry = 0.4;
            value.rz = 0.5;
            value.fov = 0.1;
        }
        "shooting_modifier" => {
            value.duration = 0.18;
            value.amplitude = 0.4;
            value.frequency = 28.0;
            value.x = 0.03;
            value.y = 0.03;
            value.z = 0.09;
            value.rx = 1.2;
            value.ry = 0.4;
            value.rz = 0.25;
            value.fov = 0.3;
        }
        "accelerating_modifier" => {
            value.duration = 0.35;
            value.amplitude = 0.5;
            value.frequency = 10.0;
            value.x = 0.04;
            value.y = 0.03;
            value.z = 0.18;
            value.rx = 0.5;
            value.ry = 0.2;
            value.rz = 0.15;
            value.fov = 1.0;
        }
        "decelerating_modifier" => {
            value.duration = 0.32;
            value.amplitude = 0.45;
            value.frequency = 9.0;
            value.x = 0.03;
            value.y = 0.04;
            value.z = 0.14;
            value.rx = 0.65;
            value.ry = 0.2;
            value.rz = 0.15;
            value.fov = 0.5;
        }
        "bump_modifier" => {
            value.duration = 0.28;
            value.amplitude = 0.75;
            value.frequency = 14.0;
            value.x = 0.08;
            value.y = 0.22;
            value.z = 0.05;
            value.rx = 1.0;
            value.ry = 0.5;
            value.rz = 0.7;
            value.fov = 0.2;
        }
        "landing_modifier" => {
            value.duration = 0.32;
            value.amplitude = 0.9;
            value.frequency = 13.0;
            value.x = 0.06;
            value.y = 0.3;
            value.z = 0.08;
            value.rx = 1.5;
            value.ry = 0.4;
            value.rz = 0.6;
            value.fov = 0.4;
        }
        "earthquake_modifier" => {
            value.duration = 1.5;
            value.amplitude = 1.2;
            value.frequency = 7.0;
            value.x = 0.35;
            value.y = 0.3;
            value.z = 0.35;
            value.rx = 1.8;
            value.ry = 1.5;
            value.rz = 1.6;
            value.fov = 1.5;
        }
        "explosion_modifier" => {
            value.duration = 0.55;
            value.amplitude = 1.1;
            value.frequency = 18.0;
            value.x = 0.28;
            value.y = 0.22;
            value.z = 0.24;
            value.rx = 1.9;
            value.ry = 1.6;
            value.rz = 1.2;
            value.fov = 1.2;
        }
        "near_miss_modifier" => {
            value.duration = 0.22;
            value.amplitude = 0.8;
            value.frequency = 20.0;
            value.x = 0.32;
            value.y = 0.06;
            value.z = 0.08;
            value.rx = 0.5;
            value.ry = 1.8;
            value.rz = 1.0;
            value.fov = 0.4;
        }
        _ => return None,
    }
    Some(value)
}

pub fn parse_runtime_value(raw_value: &str) -> Result<Option<AssignmentValue>, ParseError> {
    parse_assignment_value(raw_value)
}

fn parse_function_assignment_arg<'a>(text: &'a str, name: &str) -> Option<&'a str> {
    let args = parse_function_assignment_args(text, name)?;
    (args.len() == 1).then_some(args[0])
}

fn parse_function_assignment_args<'a>(text: &'a str, name: &str) -> Option<Vec<&'a str>> {
    let trimmed = strip_wrapping_parens(text.trim());
    let open_index = trimmed.find('(')?;
    if !trimmed[..open_index].trim().eq_ignore_ascii_case(name) {
        return None;
    }
    let inner = trimmed[open_index + 1..].strip_suffix(')')?.trim();
    Some(split_top_level_comma(inner))
}

fn split_assignment_binary(text: &str) -> Option<(&str, ArithmeticOperator, &str)> {
    for operators in [&['+', '-'][..], &['*', '/', '%'][..]] {
        let mut depth = 0usize;
        for (index, value) in text.char_indices().rev() {
            match value {
                ')' => depth += 1,
                '(' => depth = depth.saturating_sub(1),
                _ => {}
            }
            if depth == 0 && operators.contains(&value) && index > 0 {
                let left = text[..index].trim();
                let right = text[index + value.len_utf8()..].trim();
                if left.is_empty() || right.is_empty() {
                    continue;
                }
                let operator = match value {
                    '+' => ArithmeticOperator::Add,
                    '-' => ArithmeticOperator::Subtract,
                    '*' => ArithmeticOperator::Multiply,
                    '/' => ArithmeticOperator::Divide,
                    '%' => ArithmeticOperator::Modulo,
                    _ => unreachable!(),
                };
                return Some((left, operator, right));
            }
        }
    }
    None
}

fn static_assignment_number(value: &AssignmentValue) -> Option<f32> {
    match value {
        AssignmentValue::Number(value) => Some(*value),
        AssignmentValue::Binary {
            left,
            operator,
            right,
        } => {
            let left = static_assignment_number(left)?;
            let right = static_assignment_number(right)?;
            Some(match operator {
                ArithmeticOperator::Add => left + right,
                ArithmeticOperator::Subtract => left - right,
                ArithmeticOperator::Multiply => left * right,
                ArithmeticOperator::Divide if right.abs() > f32::EPSILON => left / right,
                ArithmeticOperator::Modulo if right.abs() > f32::EPSILON => left % right,
                ArithmeticOperator::Divide | ArithmeticOperator::Modulo => return None,
            })
        }
        _ => None,
    }
}

fn is_variable_name(name: &str) -> bool {
    let mut chars = name.chars();
    chars
        .next()
        .is_some_and(|value| value.is_ascii_alphabetic() || value == '_')
        && chars.all(|value| value.is_ascii_alphanumeric() || value == '_')
}

fn is_variable_path(name: &str) -> bool {
    !name.is_empty() && name.split('.').all(is_variable_name)
}

fn parse_switch(line: &str) -> Option<String> {
    let lower = line.to_ascii_lowercase();
    if !lower.starts_with("switch to ") {
        return None;
    }
    parse_quoted_strings(line).into_iter().next()
}

fn parse_wait_for_key(line: &str) -> Option<String> {
    let lower = line.to_ascii_lowercase();
    if !lower.starts_with("wait for key ") || !lower.contains(" to be pressed") {
        return None;
    }
    let key = line["wait for key ".len()..]
        .split_whitespace()
        .next()
        .unwrap_or_default();
    if key.is_empty() {
        None
    } else {
        Some(key.to_ascii_lowercase())
    }
}

fn parse_wait_until(line: &str) -> Result<Option<Condition>, ParseError> {
    let lower = line.to_ascii_lowercase();
    if !lower.starts_with("wait for ") || lower.starts_with("wait for key ") {
        return Ok(None);
    }
    parse_condition(line["wait for ".len()..].trim())
}

fn parse_wait(line: &str) -> Result<Option<AssignmentValue>, ParseError> {
    let lower = line.to_ascii_lowercase();
    if !lower.starts_with("wait ") {
        return Ok(None);
    }
    let Some(raw_seconds) = line["wait ".len()..].split_whitespace().next() else {
        return Ok(None);
    };
    parse_assignment_value(raw_seconds)
}

fn parse_camera_command(line: &str, command: &str) -> Result<Option<SceneMaxVec3>, ParseError> {
    if !line.to_ascii_lowercase().starts_with(command) {
        return Ok(None);
    }
    parse_vec3_after(line, command).map(Some)
}

fn parse_camera_move(line: &str) -> Result<Option<CameraMoveStatement>, ParseError> {
    let line = line.trim();
    if !line.to_ascii_lowercase().starts_with("camera.move") {
        return Ok(None);
    }
    let Some(raw_destination) = values_inside_first_parens(line) else {
        return Ok(None);
    };
    let Some((axis, distance_value)) = parse_camera_axis_delta(raw_destination)? else {
        return Ok(None);
    };
    let (duration_value, duration_seconds) =
        parse_directional_move_duration_value(line)?.unwrap_or((AssignmentValue::Number(0.0), 0.0));
    let distance = static_assignment_number(&distance_value).unwrap_or(0.0);

    Ok(Some(CameraMoveStatement {
        axis,
        distance,
        distance_value,
        duration_seconds,
        duration_value,
        async_run: contains_keyword(line, "async"),
    }))
}

fn parse_camera_axis_delta(
    text: &str,
) -> Result<Option<(SceneMaxAxis, AssignmentValue)>, ParseError> {
    let raw = text.trim();
    let Some(axis_char) = raw.chars().next() else {
        return Ok(None);
    };
    let axis = match axis_char.to_ascii_lowercase() {
        'x' => SceneMaxAxis::X,
        'y' => SceneMaxAxis::Y,
        'z' => SceneMaxAxis::Z,
        _ => return Ok(None),
    };
    let after_axis = raw[axis_char.len_utf8()..].trim();
    let Some(sign) = after_axis.chars().next() else {
        return Ok(None);
    };
    if sign != '+' && sign != '-' {
        return Ok(None);
    }
    let raw_distance = after_axis[sign.len_utf8()..].trim();
    if raw_distance.is_empty() {
        return Err(ParseError::InvalidNumber(raw_distance.to_owned()));
    }
    let Some(value) = parse_assignment_value(raw_distance)? else {
        return Err(ParseError::InvalidNumber(raw_distance.to_owned()));
    };
    let value = if sign == '-' {
        negate_assignment_value(value)
    } else {
        value
    };
    Ok(Some((axis, value)))
}

fn parse_camera_attach(line: &str) -> Result<Option<Statement>, ParseError> {
    let line = line.trim();
    if line.eq_ignore_ascii_case("camera.attach stop") {
        return Ok(Some(Statement::CameraAttachStop));
    }

    let lower = line.to_ascii_lowercase();
    if !lower.starts_with("camera.attach to ") {
        return Ok(None);
    }
    let rest = &line["camera.attach to ".len()..];
    let (target, options_text) = split_once_case_insensitive(rest, ":")
        .map(|(target, options)| (target.trim(), options.trim()))
        .unwrap_or((rest.trim(), ""));
    let target = normalize_entity_reference(target);
    if !is_variable_path(&target) {
        return Ok(None);
    }

    let offset = parse_vec3_after(options_text, "pos").unwrap_or(SceneMaxVec3 {
        x: 0.0,
        y: 0.0,
        z: 0.0,
    });
    Ok(Some(Statement::CameraAttach(CameraAttachStatement {
        target,
        offset,
    })))
}

fn parse_camera_chase(line: &str) -> Result<Option<Statement>, ParseError> {
    let line = line.trim();
    if line.eq_ignore_ascii_case("camera.chase stop") {
        return Ok(Some(Statement::CameraAttachStop));
    }

    let lower = line.to_ascii_lowercase();
    if !lower.starts_with("camera.chase ") {
        return Ok(None);
    }
    let target = normalize_entity_reference(&line["camera.chase ".len()..]);
    if !is_variable_path(&target) {
        return Ok(None);
    }
    Ok(Some(Statement::CameraChase { target }))
}

fn parse_camera_modifier_apply(
    line: &str,
) -> Result<Option<CameraModifierApplyStatement>, ParseError> {
    let Some((target, rest)) = split_dot_command_rest(line) else {
        return Ok(None);
    };
    let rest = rest.trim();
    if !starts_with_keyword(rest, "apply") {
        return Ok(None);
    }
    let after_apply = rest["apply".len()..].trim_start();
    let (modifier_text, options_text) = split_once_case_insensitive(after_apply, ":")
        .map(|(modifier, options)| (modifier.trim(), options.trim()))
        .unwrap_or((after_apply.trim(), ""));
    let modifier = modifier_text
        .split(|value: char| value.is_whitespace() || value == ',')
        .next()
        .unwrap_or_default()
        .trim();
    if !is_variable_path(&target) || !is_variable_path(modifier) {
        return Ok(None);
    }

    let mut overrides = Vec::new();
    for option in split_top_level_comma(options_text) {
        let option = option.trim();
        if option.is_empty() {
            continue;
        }
        let mut parts = option.splitn(2, char::is_whitespace);
        let name = parts.next().unwrap_or_default().trim().to_ascii_lowercase();
        let raw_value = parts.next().unwrap_or_default().trim();
        if !is_camera_modifier_override_name(&name) || raw_value.is_empty() {
            continue;
        }
        let Some(value) = parse_assignment_value(raw_value)? else {
            return Err(ParseError::InvalidNumber(raw_value.to_owned()));
        };
        overrides.push((name, value));
    }

    Ok(Some(CameraModifierApplyStatement {
        target,
        modifier: modifier.to_owned(),
        overrides,
    }))
}

fn is_camera_modifier_override_name(name: &str) -> bool {
    matches!(
        name,
        "duration" | "amplitude" | "frequency" | "x" | "y" | "z" | "rx" | "ry" | "rz" | "fov"
    )
}

fn parse_audio_statement(line: &str) -> Result<Option<AudioStatement>, ParseError> {
    let line = line.trim();
    let lower = line.to_ascii_lowercase();
    if lower.starts_with("audio.stop ") {
        let rest = line["audio.stop ".len()..].trim();
        let sound = parse_quoted_strings(rest)
            .into_iter()
            .next()
            .or_else(|| (!rest.is_empty()).then(|| clean_assignment_value(rest).to_owned()));
        return Ok(Some(AudioStatement {
            action: AudioAction::Stop,
            sound,
            sound_value: None,
            looped: false,
            volume: None,
        }));
    }

    if lower.starts_with("audio.play ") {
        return parse_audio_play(line["audio.play ".len()..].trim());
    }

    if lower.starts_with("play sound ") {
        let rest = line["play sound ".len()..].trim();
        let sound_text = take_until_clause(rest, &[" loop ", " loop"]).trim();
        let sound = (!sound_text.is_empty()).then(|| sound_text.to_owned());
        return Ok(Some(AudioStatement {
            action: AudioAction::Play,
            sound,
            sound_value: None,
            looped: contains_keyword(rest, "loop"),
            volume: None,
        }));
    }

    Ok(None)
}

fn parse_audio_play(rest: &str) -> Result<Option<AudioStatement>, ParseError> {
    let sound_text = take_until_clause(rest, &[" having ", " loop ", " loop"]).trim();
    let (sound, sound_value) =
        if let Some(sound) = parse_quoted_strings(sound_text).into_iter().next() {
            (Some(sound), None)
        } else {
            let Some(value) = parse_assignment_value(clean_assignment_value(sound_text))? else {
                return Ok(None);
            };
            (None, Some(value))
        };
    Ok(Some(AudioStatement {
        action: AudioAction::Play,
        sound,
        sound_value,
        looped: contains_keyword(rest, "loop"),
        volume: parse_audio_volume_value(rest)?,
    }))
}

fn parse_audio_volume_value(rest: &str) -> Result<Option<AssignmentValue>, ParseError> {
    let Some(index) = find_keyword_index(rest, "having") else {
        return Ok(None);
    };
    let options = take_until_clause(&rest[index + "having".len()..], &[" loop ", " loop"]).trim();
    let lower_options = options.to_ascii_lowercase();
    let Some(volume_index) = lower_options.find("volume") else {
        return Ok(None);
    };
    let after_volume = options[volume_index + "volume".len()..]
        .trim_start()
        .trim_start_matches('=')
        .trim_start();
    let raw_volume = take_until_clause(after_volume, &[" and ", ",", " loop ", " loop"]).trim();
    if raw_volume.is_empty() {
        return Ok(None);
    }
    parse_assignment_value(clean_assignment_value(raw_volume))
}

fn find_keyword_index(text: &str, keyword: &str) -> Option<usize> {
    let lower = text.to_ascii_lowercase();
    let keyword = keyword.to_ascii_lowercase();
    let mut start = 0;
    while let Some(relative_index) = lower[start..].find(&keyword) {
        let index = start + relative_index;
        let before = lower[..index]
            .chars()
            .next_back()
            .is_none_or(|ch| !ch.is_ascii_alphanumeric() && ch != '_');
        let after_index = index + keyword.len();
        let after = lower[after_index..]
            .chars()
            .next()
            .is_none_or(|ch| !ch.is_ascii_alphanumeric() && ch != '_');
        if before && after {
            return Some(index);
        }
        start = after_index;
    }
    None
}

fn parse_print_statement(line: &str) -> Result<Option<PrintStatement>, ParseError> {
    let Some((channel, rest)) = split_dot_command_rest(line) else {
        return Ok(None);
    };
    if !is_variable_name(&channel) {
        return Ok(None);
    }
    let Some(after_print) = strip_keyword_prefix(rest.trim(), "print") else {
        return Ok(None);
    };
    let (text, attrs) = split_print_text_and_attrs(after_print.trim());
    let Some(text) = parse_ui_property_value(text)? else {
        return Ok(None);
    };
    let mut statement = PrintStatement {
        channel,
        text,
        position: None,
        color: None,
        font_size: None,
        font: None,
        append: false,
    };
    parse_print_attrs(attrs, &mut statement)?;
    Ok(Some(statement))
}

fn split_print_text_and_attrs(text: &str) -> (&str, &str) {
    if let Some((before, after)) = split_once_case_insensitive_outside_quotes(text, " having ") {
        return (before.trim(), after.trim());
    }
    if let Some((before, after)) = split_once_case_insensitive_outside_quotes(text, ":") {
        return (before.trim(), after.trim());
    }
    (text.trim(), "")
}

fn parse_print_attrs(attrs: &str, statement: &mut PrintStatement) -> Result<(), ParseError> {
    for attr in split_print_attrs(attrs) {
        let attr = attr.trim().trim_start_matches(':').trim();
        if attr.is_empty() {
            continue;
        }
        if attr.eq_ignore_ascii_case("append") {
            statement.append = true;
            continue;
        }
        if starts_with_keyword(attr, "pos") {
            statement.position = parse_print_position(attr)?;
            continue;
        }
        if starts_with_keyword(attr, "color") {
            statement.color = parse_print_named_value(attr, "color")
                .map(|value| value.trim_matches('"').to_owned());
            continue;
        }
        if starts_with_keyword(attr, "size") {
            if let Some(value) = parse_print_named_value(attr, "size") {
                statement.font_size = parse_assignment_value(clean_assignment_value(value))?;
            }
            continue;
        }
        if starts_with_keyword(attr, "font") {
            statement.font = parse_quoted_strings(attr).into_iter().next().or_else(|| {
                parse_print_named_value(attr, "font")
                    .map(clean_call_arg)
                    .map(str::to_owned)
            });
        }
    }
    Ok(())
}

fn split_print_attrs(attrs: &str) -> Vec<&str> {
    let mut values = Vec::new();
    let mut remaining = attrs.trim();
    while let Some((left, right)) = split_once_case_insensitive_outside_quotes(remaining, " and ") {
        values.push(left.trim());
        remaining = right.trim();
    }
    values.extend(
        split_top_level_comma(remaining)
            .into_iter()
            .map(str::trim)
            .filter(|value| !value.is_empty()),
    );
    values
}

fn parse_print_position(attr: &str) -> Result<Option<PositionValue>, ParseError> {
    let Some(raw_values) = values_inside_first_parens(attr) else {
        return Ok(None);
    };
    let parts = split_top_level_comma(raw_values);
    if parts.len() != 3 {
        return Ok(None);
    }
    Ok(Some(PositionValue::Coordinates(
        parts
            .into_iter()
            .map(parse_position_expr)
            .collect::<Result<Vec<_>, _>>()?,
    )))
}

fn parse_print_named_value<'a>(attr: &'a str, name: &str) -> Option<&'a str> {
    let after_name = attr.trim_start()[name.len()..].trim();
    Some(after_name.trim_start_matches('=').trim()).filter(|value| !value.is_empty())
}

fn parse_shader_statement(line: &str) -> Result<Option<Statement>, ParseError> {
    let Some((left, right)) = line.trim().split_once('=') else {
        return Ok(None);
    };
    let target = left.trim();
    let lower_target = target.to_ascii_lowercase();
    if lower_target == "scene.environment.shader" || lower_target == "environment.shader" {
        return Ok(Some(Statement::SetEnvironmentShader {
            shader: parse_shader_value(right)?,
        }));
    }
    if !lower_target.ends_with(".shader") {
        return Ok(None);
    }
    let target_name = target[..target.len() - ".shader".len()].trim();
    if target_name.is_empty() || !is_variable_path(target_name) {
        return Ok(None);
    }
    Ok(Some(Statement::SetShader(SetShaderStatement {
        target: target_name.to_owned(),
        shader: parse_shader_value(right)?,
    })))
}

fn parse_shader_value(raw_value: &str) -> Result<AssignmentValue, ParseError> {
    let raw_value = clean_assignment_value(raw_value);
    if is_quoted(raw_value) {
        return Ok(AssignmentValue::Symbol(unquote_ui_text(raw_value)));
    }
    Ok(parse_assignment_value(raw_value)?
        .unwrap_or_else(|| AssignmentValue::Symbol(clean_call_arg(raw_value).to_owned())))
}

fn parse_model_decl(line: &str) -> Result<Option<Statement>, ParseError> {
    if let Some((name, rest)) = line.split_once("=>") {
        if let Some(pool) = parse_object_pool(name.trim(), rest)? {
            return Ok(Some(Statement::ObjectPool(pool)));
        }
        let (resource, options_text) = split_resource_and_options(rest);
        let resource = normalize_resource(resource);
        if is_deferred_or_non_model_resource(rest, &resource) {
            return Ok(Some(unsupported(line)));
        }
        return Ok(Some(Statement::ModelDecl {
            name: name.trim().to_owned(),
            resource,
            options: parse_entity_options(rest, options_text)?,
        }));
    }

    let lower = line.to_ascii_lowercase();
    if let Some(index) = lower.find(" is a ") {
        let name = line[..index].trim();
        let rest = &line[index + " is a ".len()..];
        let (resource, options_text) = split_resource_and_options(rest);
        let resource = normalize_resource(resource);
        if is_deferred_or_non_model_resource(rest, &resource) {
            return Ok(Some(unsupported(line)));
        }
        return Ok(Some(Statement::ModelDecl {
            name: name.to_owned(),
            resource,
            options: parse_entity_options(rest, options_text)?,
        }));
    }

    Ok(None)
}

fn parse_light_decl(line: &str) -> Result<Option<Statement>, ParseError> {
    if let Some((name, rest)) = line.split_once("=>") {
        return parse_light_decl_parts(name.trim(), rest);
    }

    let lower = line.to_ascii_lowercase();
    if let Some(index) = lower.find(" is a ") {
        let name = line[..index].trim();
        let rest = &line[index + " is a ".len()..];
        return parse_light_decl_parts(name, rest);
    }

    Ok(None)
}

fn parse_light_decl_parts(name: &str, rest: &str) -> Result<Option<Statement>, ParseError> {
    let (resource, options_text) = split_resource_and_options(rest);
    let Some(light_type) = parse_light_resource_type(resource.trim()) else {
        return Ok(None);
    };
    Ok(Some(Statement::LightDecl(LightDeclarationStatement {
        name: name.to_owned(),
        light_type,
        color: parse_light_color_attr(options_text),
        intensity: parse_light_value_attr(options_text, "intensity")?.map(|(value, _)| value),
        intensity_unit: parse_light_value_attr(options_text, "intensity")?
            .and_then(|(_, unit)| unit),
        position: parse_position_value_after(options_text, "pos")?.or_else(|| {
            parse_vec3_after(options_text, "pos")
                .ok()
                .map(position_value_from_vec3)
        }),
        direction: parse_light_direction_attr(options_text)?,
        shadow_mode: parse_light_word_attr(options_text, "shadow"),
        range: parse_light_value_attr(options_text, "range")?.map(|(value, _)| value),
        look_at: parse_light_look_at_attr(options_text),
        angle: parse_light_value_attr(options_text, "angle")?.map(|(value, _)| value),
        preset: parse_light_string_attr(options_text, "preset"),
        exposure: parse_light_value_attr(options_text, "exposure")?.map(|(value, _)| value),
        ambient_color: parse_light_color_named_attr(options_text, "ambient"),
    })))
}

fn parse_light_probe_add(line: &str) -> Result<Option<LightProbeAddStatement>, ParseError> {
    let trimmed = line.trim();
    let lower = trimmed.to_ascii_lowercase();
    let Some(after_prefix_len) = lower.strip_prefix("lights.add").map(|_| "lights.add".len())
    else {
        return Ok(None);
    };
    let original_rest = trimmed[after_prefix_len..].trim_start_matches('.').trim();
    let Some(after_probe) = strip_keyword_prefix(original_rest, "probe") else {
        return Ok(None);
    };
    let name = parse_quoted_strings(after_probe)
        .into_iter()
        .next()
        .unwrap_or_else(|| {
            first_light_value_token(after_probe)
                .unwrap_or("1")
                .to_owned()
        });
    Ok(Some(LightProbeAddStatement {
        name,
        position: parse_position_value_after(after_probe, "pos")?.or_else(|| {
            parse_vec3_after(after_probe, "pos")
                .ok()
                .map(position_value_from_vec3)
        }),
    }))
}

fn parse_light_resource_type(resource: &str) -> Option<LightType> {
    let lower = resource.trim().to_ascii_lowercase();
    let light_type = lower.strip_prefix("lights.")?.trim();
    match light_type {
        "directional" => Some(LightType::Directional),
        "point" => Some(LightType::Point),
        "spot" => Some(LightType::Spot),
        "sky" => Some(LightType::Sky),
        "ambient" => Some(LightType::Ambient),
        "probe" => Some(LightType::Probe),
        _ => None,
    }
}

fn parse_light_color_attr(text: &str) -> Option<String> {
    parse_light_color_named_attr(text, "color")
}

fn parse_light_color_named_attr(text: &str, name: &str) -> Option<String> {
    let attr = light_attr_text(text, name)?;
    parse_quoted_strings(attr)
        .into_iter()
        .next()
        .or_else(|| first_light_value_token(attr).map(str::to_owned))
}

fn parse_light_string_attr(text: &str, name: &str) -> Option<String> {
    let attr = light_attr_text(text, name)?;
    parse_quoted_strings(attr)
        .into_iter()
        .next()
        .or_else(|| first_light_value_token(attr).map(str::to_owned))
}

fn parse_light_word_attr(text: &str, name: &str) -> Option<String> {
    let attr = light_attr_text(text, name)?;
    let attr = strip_keyword_prefix(attr, "mode").unwrap_or(attr).trim();
    first_light_value_token(attr).map(|value| value.to_ascii_lowercase())
}

fn parse_light_value_attr(
    text: &str,
    name: &str,
) -> Result<Option<(AssignmentValue, Option<String>)>, ParseError> {
    let Some(attr) = light_attr_text(text, name) else {
        return Ok(None);
    };
    let mut value_text = attr.trim();
    let mut unit = None;
    let lower = value_text.to_ascii_lowercase();
    if lower.ends_with(" lumens") {
        value_text = value_text[..value_text.len() - " lumens".len()].trim_end();
        unit = Some("lumens".to_owned());
    } else if lower.ends_with(" lumen") {
        value_text = value_text[..value_text.len() - " lumen".len()].trim_end();
        unit = Some("lumens".to_owned());
    }
    let Some(value) = parse_assignment_value(value_text)? else {
        return Ok(None);
    };
    Ok(Some((value, unit)))
}

fn parse_light_direction_attr(text: &str) -> Result<Option<Vec<PositionExpr>>, ParseError> {
    let Some(attr) = light_attr_text(text, "direction") else {
        return Ok(None);
    };
    let Some(values) = values_inside_first_parens(attr) else {
        return Ok(None);
    };
    let parts = split_top_level_comma(values);
    if parts.len() != 3 {
        return Ok(None);
    }
    Ok(Some(
        parts
            .into_iter()
            .map(parse_position_expr)
            .collect::<Result<Vec<_>, _>>()?,
    ))
}

fn parse_light_look_at_attr(text: &str) -> Option<String> {
    let attr = light_attr_text(text, "look")?;
    let attr = strip_keyword_prefix(attr, "at").unwrap_or(attr).trim();
    first_light_value_token(attr).map(str::to_owned)
}

fn light_attr_text<'a>(text: &'a str, name: &str) -> Option<&'a str> {
    split_top_level_options(text).into_iter().find_map(|part| {
        let after_name = strip_keyword_prefix(part, name)?.trim();
        let after_name = after_name.strip_prefix('=').unwrap_or(after_name).trim();
        if after_name.is_empty() {
            None
        } else {
            Some(after_name)
        }
    })
}

fn first_light_value_token(text: &str) -> Option<&str> {
    text.trim()
        .trim_matches('"')
        .split(|value: char| value.is_whitespace() || value == ',' || value == ':')
        .find(|part| !part.is_empty())
}

fn position_value_from_vec3(value: SceneMaxVec3) -> PositionValue {
    PositionValue::Coordinates(vec![
        PositionExpr::Number(value.x),
        PositionExpr::Number(value.y),
        PositionExpr::Number(value.z),
    ])
}

fn parse_object_pool(name: &str, rest: &str) -> Result<Option<ObjectPoolStatement>, ParseError> {
    if !is_variable_path(name) {
        return Ok(None);
    }
    let rest = rest.trim();
    let lower = rest.to_ascii_lowercase();
    if !lower.starts_with("object.pool") {
        return Ok(None);
    }
    let Some(args_text) = values_inside_first_parens(rest) else {
        return Ok(None);
    };
    let parts = split_top_level_comma(args_text);
    let factory = parts.first().copied().unwrap_or_default().trim().to_owned();
    if !is_variable_name(&factory) {
        return Ok(None);
    }
    let size = parts
        .iter()
        .find_map(|part| {
            let part = part.trim();
            let (_, value) = part.split_once("size")?;
            value.trim().parse::<usize>().ok()
        })
        .unwrap_or(1);
    Ok(Some(ObjectPoolStatement {
        name: name.to_owned(),
        factory,
        size,
    }))
}

fn parse_pool_release(line: &str) -> Option<PoolReleaseStatement> {
    let (pool, rest) = split_dot_command_rest(line)?;
    let rest = rest.trim();
    let rest_lower = rest.to_ascii_lowercase();
    let target = if rest_lower.starts_with("release") {
        rest.get("release".len()..)?.trim()
    } else if rest_lower.starts_with("free") {
        rest.get("free".len()..)?.trim()
    } else {
        return None;
    };
    if pool.is_empty() || !is_variable_path(&pool) || target.is_empty() || !is_variable_path(target)
    {
        return None;
    }
    Some(PoolReleaseStatement {
        pool,
        target: target.to_owned(),
    })
}

fn parse_delete(line: &str) -> Option<String> {
    let (target, rest) = split_dot_command_rest(line)?;
    (rest.trim().eq_ignore_ascii_case("delete") && !target.is_empty() && is_variable_path(&target))
        .then_some(target)
}

fn parse_visibility(line: &str) -> Option<Statement> {
    let (target, command) = split_dot_command(line)?;
    match command.to_ascii_lowercase().as_str() {
        "show" => Some(Statement::Visibility {
            target,
            visible: true,
        }),
        "hide" => Some(Statement::Visibility {
            target,
            visible: false,
        }),
        _ => None,
    }
}

fn parse_look_at(line: &str) -> Option<Statement> {
    let (target, rest) = line.split_once(".look at ")?;
    let target = target.trim();
    if target.is_empty() {
        return None;
    }
    let subject = rest
        .trim()
        .strip_prefix('(')
        .and_then(|value| value.strip_suffix(')'))
        .unwrap_or_else(|| rest.trim())
        .trim();
    if subject.is_empty() {
        None
    } else {
        Some(Statement::LookAt {
            target: target.to_owned(),
            subject: subject.to_owned(),
        })
    }
}

fn parse_attach(line: &str) -> Result<Option<AttachStatement>, ParseError> {
    let Some((target, rest)) = line.split_once(".attach to ") else {
        return Ok(None);
    };
    let target = target.trim();
    if target.is_empty() {
        return Ok(None);
    }

    let lower_rest = rest.to_ascii_lowercase();
    let (subject, options_text) = lower_rest
        .find(": pos")
        .map(|index| (&rest[..index], &rest[index + 1..]))
        .unwrap_or((rest, ""));
    let subject = subject.trim();
    if subject.is_empty() {
        return Ok(None);
    }

    let offset = parse_vec3_after(options_text, "pos").unwrap_or(SceneMaxVec3 {
        x: 0.0,
        y: 0.0,
        z: 0.0,
    });
    Ok(Some(AttachStatement {
        target: target.to_owned(),
        subject: subject.to_owned(),
        offset,
    }))
}

fn parse_position(line: &str) -> Result<Option<PositionStatement>, ParseError> {
    let Some((target, rest)) = split_dot_command_rest(line) else {
        return Ok(None);
    };
    let rest = rest.trim();
    if !rest.to_ascii_lowercase().starts_with("pos") {
        return Ok(None);
    }
    let Some(raw_values) = values_inside_first_parens(rest) else {
        return Ok(None);
    };
    let parts = split_top_level_comma(raw_values);
    let position = if parts.len() == 3 {
        PositionValue::Coordinates(
            parts
                .into_iter()
                .map(parse_position_expr)
                .collect::<Result<Vec<_>, _>>()?,
        )
    } else if let Some(entity) = parse_quoted_entity_reference(raw_values.trim()) {
        PositionValue::Entity(entity)
    } else {
        let entity = normalize_entity_reference(raw_values.trim());
        if !is_variable_path(&entity) {
            return Ok(None);
        }
        PositionValue::Entity(entity)
    };
    Ok(Some(PositionStatement { target, position }))
}

fn values_inside_first_parens(text: &str) -> Option<&str> {
    let open_index = text.find('(')?;
    let after_open = &text[open_index + 1..];
    let close_index = matching_close_paren_index(after_open)?;
    Some(after_open[..close_index].trim())
}

fn split_top_level_comma(text: &str) -> Vec<&str> {
    let mut parts = Vec::new();
    let mut depth = 0usize;
    let mut start = 0usize;
    for (index, value) in text.char_indices() {
        match value {
            '(' => depth += 1,
            ')' => depth = depth.saturating_sub(1),
            ',' if depth == 0 => {
                parts.push(text[start..index].trim());
                start = index + value.len_utf8();
            }
            _ => {}
        }
    }
    parts.push(text[start..].trim());
    parts.into_iter().filter(|part| !part.is_empty()).collect()
}

fn split_top_level_options(text: &str) -> Vec<&str> {
    let mut parts = Vec::new();
    let mut paren_depth = 0usize;
    let mut bracket_depth = 0usize;
    let mut quote = false;
    let mut escaped = false;
    let mut start = 0usize;

    for (index, value) in text.char_indices() {
        if quote {
            if escaped {
                escaped = false;
            } else if value == '\\' {
                escaped = true;
            } else if value == '"' {
                quote = false;
            }
            continue;
        }

        match value {
            '"' => quote = true,
            '(' => paren_depth += 1,
            ')' => paren_depth = paren_depth.saturating_sub(1),
            '[' => bracket_depth += 1,
            ']' => bracket_depth = bracket_depth.saturating_sub(1),
            ',' if paren_depth == 0 && bracket_depth == 0 => {
                parts.push(text[start..index].trim());
                start = index + value.len_utf8();
            }
            _ => {}
        }
    }

    parts.push(text[start..].trim());
    parts.into_iter().filter(|part| !part.is_empty()).collect()
}

fn matching_close_bracket_index(text: &str) -> Option<usize> {
    let mut depth = 0usize;
    let mut quote = false;
    let mut escaped = false;
    for (index, value) in text.char_indices() {
        if quote {
            if escaped {
                escaped = false;
            } else if value == '\\' {
                escaped = true;
            } else if value == '"' {
                quote = false;
            }
            continue;
        }

        match value {
            '"' => quote = true,
            '[' => depth += 1,
            ']' if depth == 0 => return Some(index),
            ']' => depth = depth.saturating_sub(1),
            _ => {}
        }
    }
    None
}

fn parse_position_expr(text: &str) -> Result<PositionExpr, ParseError> {
    let text = text.trim();
    if let Ok(value) = text.parse::<f32>() {
        return Ok(PositionExpr::Number(value));
    }
    let Some((reference, axis)) = parse_entity_axis_reference(text) else {
        if let Some(value) = parse_assignment_value(text)? {
            return Ok(PositionExpr::Value(value));
        }
        return Err(ParseError::InvalidNumber(text.to_owned()));
    };
    Ok(PositionExpr::EntityAxis {
        entity: reference.entity,
        axis,
        offset: reference.offset,
    })
}

struct EntityAxisReference {
    entity: String,
    offset: f32,
}

fn parse_entity_axis_reference(text: &str) -> Option<(EntityAxisReference, SceneMaxAxis)> {
    let (base, offset) = split_reference_offset(text);
    for (suffix, axis) in [
        (".x", SceneMaxAxis::X),
        (".y", SceneMaxAxis::Y),
        (".z", SceneMaxAxis::Z),
    ] {
        if let Some(entity) = base.strip_suffix(suffix) {
            let entity = normalize_entity_reference(entity);
            if is_variable_path(&entity) {
                return Some((EntityAxisReference { entity, offset }, axis));
            }
        }
    }
    None
}

fn split_reference_offset(text: &str) -> (&str, f32) {
    for operator in ["+", "-"] {
        if let Some(index) = text.rfind(operator) {
            let right = text[index + operator.len()..].trim();
            if let Ok(mut offset) = right.parse::<f32>() {
                if operator == "-" {
                    offset = -offset;
                }
                return (text[..index].trim(), offset);
            }
        }
    }
    (text.trim(), 0.0)
}

fn normalize_entity_reference(text: &str) -> String {
    let text = text.trim();
    text.split('.')
        .next()
        .unwrap_or_default()
        .trim()
        .trim_matches('"')
        .to_owned()
}

fn parse_quoted_entity_reference(text: &str) -> Option<String> {
    let text = text.trim();
    let quote_start = text.find('"')?;
    let owner = text[..quote_start].trim().trim_end_matches('.').trim();
    let after_quote = &text[quote_start + 1..];
    let quote_end = after_quote.find('"')?;
    let bone = after_quote[..quote_end].trim();
    let trailing = after_quote[quote_end + 1..].trim();
    if !trailing.is_empty() || !is_variable_path(owner) || bone.is_empty() {
        return None;
    }
    Some(format!("{owner}.\"{bone}\""))
}

fn parse_turn(line: &str) -> Result<Option<TurnStatement>, ParseError> {
    if let Some(turn) = parse_rotate_turn(line)? {
        return Ok(Some(turn));
    }

    let Some((target, rest)) = line.split_once(".turn") else {
        return Ok(None);
    };
    let target = target.trim();
    if target.is_empty() {
        return Ok(None);
    }

    let mut degree_text = take_until_clause(
        rest.trim(),
        &[" in ", " for ", " loop ", " while ", " async"],
    )
    .trim();
    if degree_text.is_empty() {
        return Ok(None);
    }
    let turn_right = degree_text
        .split_whitespace()
        .next()
        .is_some_and(|part| part.eq_ignore_ascii_case("right"));
    if degree_text
        .split_whitespace()
        .next()
        .is_some_and(|part| part.eq_ignore_ascii_case("left") || part.eq_ignore_ascii_case("right"))
    {
        degree_text = degree_text
            .split_once(char::is_whitespace)
            .map(|(_, value)| value.trim())
            .unwrap_or_default();
    }
    if degree_text.is_empty() {
        return Ok(None);
    }
    let Some(mut degrees_value) = parse_assignment_value(degree_text)? else {
        return Err(ParseError::InvalidNumber(degree_text.to_owned()));
    };
    if turn_right {
        degrees_value = negate_assignment_value(degrees_value);
    }
    let degrees = static_assignment_number(&degrees_value).unwrap_or(0.0);

    let (duration_value, duration_seconds) =
        parse_directional_move_duration_value(rest)?.unwrap_or((AssignmentValue::Number(0.0), 0.0));
    let loop_condition = parse_loop_while_condition(rest)?;
    Ok(Some(TurnStatement {
        target: target.to_owned(),
        degrees,
        degrees_value,
        duration_seconds,
        duration_value,
        loop_condition,
        async_run: contains_keyword(rest, "async"),
    }))
}

fn parse_rotate_turn(line: &str) -> Result<Option<TurnStatement>, ParseError> {
    let Some((target, rest)) = line.split_once(".rotate(") else {
        return Ok(None);
    };
    let target = target.trim();
    if target.is_empty() {
        return Ok(None);
    }
    let Some((inner, after_rotate)) = rest.split_once(')') else {
        return Ok(None);
    };
    let Some(degrees_value) = parse_relative_y_rotation(inner)? else {
        return Ok(None);
    };
    let degrees = static_assignment_number(&degrees_value).unwrap_or(0.0);
    let (duration_value, duration_seconds) = parse_directional_move_duration_value(after_rotate)?
        .unwrap_or((AssignmentValue::Number(0.0), 0.0));
    let loop_condition = parse_loop_while_condition(after_rotate)?;
    Ok(Some(TurnStatement {
        target: target.to_owned(),
        degrees,
        degrees_value,
        duration_seconds,
        duration_value,
        loop_condition,
        async_run: contains_keyword(after_rotate, "async"),
    }))
}

fn parse_loop_while_condition(text: &str) -> Result<Option<Condition>, ParseError> {
    let Some((_, condition_text)) = split_once_case_insensitive(text, "loop while") else {
        return Ok(None);
    };
    parse_condition(condition_text.trim())
}

fn parse_relative_y_rotation(text: &str) -> Result<Option<AssignmentValue>, ParseError> {
    let normalized = text.trim().replace(' ', "");
    if let Some(raw_degrees) = normalized
        .strip_prefix("y+")
        .or_else(|| normalized.strip_prefix("Y+"))
    {
        let Some(value) = parse_assignment_value(raw_degrees)? else {
            return Err(ParseError::InvalidNumber(raw_degrees.to_owned()));
        };
        return Ok(Some(value));
    }
    if let Some(raw_degrees) = normalized
        .strip_prefix("y-")
        .or_else(|| normalized.strip_prefix("Y-"))
    {
        let Some(value) = parse_assignment_value(raw_degrees)? else {
            return Err(ParseError::InvalidNumber(raw_degrees.to_owned()));
        };
        return Ok(Some(negate_assignment_value(value)));
    }
    {
        return Ok(None);
    }
}

fn parse_directional_move_distance_text<'a>(
    rest: &'a str,
    direction_text: &str,
) -> Result<&'a str, ParseError> {
    let after_move = rest
        .trim_start()
        .get("move".len()..)
        .unwrap_or_default()
        .trim_start();
    let Some(after_direction) = after_move.strip_prefix(direction_text) else {
        return Ok("");
    };
    let raw_distance = take_until_move_clause(after_direction.trim_start()).trim();
    if raw_distance.is_empty() {
        return Err(ParseError::InvalidNumber(raw_distance.to_owned()));
    }
    Ok(raw_distance)
}

fn take_until_move_clause(text: &str) -> &str {
    take_until_clause(text, &[" in ", " for ", " loop ", " while ", " async"])
}

fn take_until_clause<'a>(text: &'a str, needles: &[&str]) -> &'a str {
    let end = needles
        .iter()
        .filter_map(|needle| find_case_insensitive_span(text, needle).map(|(start, _)| start))
        .min()
        .unwrap_or(text.len());
    &text[..end]
}

fn negate_assignment_value(value: AssignmentValue) -> AssignmentValue {
    match value {
        AssignmentValue::Number(value) => AssignmentValue::Number(-value),
        value => AssignmentValue::Binary {
            left: Box::new(AssignmentValue::Number(0.0)),
            operator: ArithmeticOperator::Subtract,
            right: Box::new(value),
        },
    }
}

fn parse_move(line: &str) -> Result<Option<MoveStatement>, ParseError> {
    let Some((target, rest)) = split_dot_command_rest(line) else {
        return Ok(None);
    };
    let rest = rest.trim();
    let lower = rest.to_ascii_lowercase();
    if !lower.starts_with("move ") {
        return Ok(None);
    }

    let parts = rest.split_whitespace().collect::<Vec<_>>();
    let Some(direction_text) = parts.get(1) else {
        return Ok(None);
    };
    let direction = match direction_text.to_ascii_lowercase().as_str() {
        "forward" => MoveDirection::Forward,
        "backward" | "back" => MoveDirection::Backward,
        "left" => MoveDirection::Left,
        "right" => MoveDirection::Right,
        "up" => MoveDirection::Up,
        "down" => MoveDirection::Down,
        _ => return Ok(None),
    };
    let raw_distance = parse_directional_move_distance_text(rest, direction_text)?;
    let Some(distance_value) = parse_assignment_value(raw_distance)? else {
        return Err(ParseError::InvalidNumber(raw_distance.to_owned()));
    };
    let distance = static_assignment_number(&distance_value).unwrap_or(0.0);
    let (duration_value, duration_seconds) =
        parse_directional_move_duration_value(rest)?.unwrap_or((AssignmentValue::Number(0.0), 0.0));
    let loop_condition = parse_loop_while_condition(rest)?;

    Ok(Some(MoveStatement {
        target,
        direction,
        distance,
        distance_value,
        duration_seconds,
        duration_value,
        loop_condition,
        async_run: contains_keyword(rest, "async"),
    }))
}

fn parse_move_to(line: &str) -> Result<Option<MoveToStatement>, ParseError> {
    let Some((target, rest)) = split_dot_command_rest(line) else {
        return Ok(None);
    };
    let rest = rest.trim();
    if !rest.to_ascii_lowercase().starts_with("move to") {
        return Ok(None);
    }
    let Some(raw_destination) = values_inside_first_parens(rest) else {
        return Ok(None);
    };
    let destination = parse_move_to_destination(raw_destination)?;
    let (duration_value, duration_seconds) =
        parse_directional_move_duration_value(rest)?.unwrap_or((AssignmentValue::Number(0.0), 0.0));

    Ok(Some(MoveToStatement {
        target,
        destination,
        duration_seconds,
        duration_value,
        async_run: contains_keyword(rest, "async"),
    }))
}

fn parse_move_to_destination(text: &str) -> Result<MoveToDestination, ParseError> {
    let parts = split_top_level_comma(text);
    if parts.len() == 3 {
        return Ok(MoveToDestination::Position(PositionValue::Coordinates(
            parts
                .into_iter()
                .map(parse_position_expr)
                .collect::<Result<Vec<_>, _>>()?,
        )));
    }

    let tokens = text.split_whitespace().collect::<Vec<_>>();
    if tokens.len() >= 3 && tokens[1].eq_ignore_ascii_case("forward") {
        let raw_distance = text
            .split_once(tokens[1])
            .map(|(_, value)| value.trim())
            .unwrap_or_default();
        let Some(distance_value) = parse_assignment_value(raw_distance)? else {
            return Err(ParseError::InvalidNumber(raw_distance.to_owned()));
        };
        let distance = static_assignment_number(&distance_value).unwrap_or(0.0);
        let entity = normalize_entity_reference(tokens[0]);
        if is_variable_path(&entity) {
            return Ok(MoveToDestination::EntityForward {
                entity,
                distance,
                distance_value,
            });
        }
    }

    if let Some(entity) = parse_quoted_entity_reference(text) {
        return Ok(MoveToDestination::Position(PositionValue::Entity(entity)));
    }

    let entity = normalize_entity_reference(text);
    if !is_variable_path(&entity) {
        return Err(ParseError::InvalidNumber(text.to_owned()));
    }
    Ok(MoveToDestination::Position(PositionValue::Entity(entity)))
}

fn parse_character_mode(line: &str) -> Result<Option<CharacterModeStatement>, ParseError> {
    let Some((target, rest)) = split_dot_command_rest(line) else {
        return Ok(None);
    };
    let rest = rest.trim();
    if !rest
        .to_ascii_lowercase()
        .starts_with("switch to character mode")
    {
        return Ok(None);
    }

    let (gravity, gravity_value) =
        if let Some((_, after_gravity)) = split_once_case_insensitive(rest, "gravity") {
            let after_gravity = after_gravity.trim_start_matches(|value: char| {
                value.is_whitespace() || value == ':' || value == '='
            });
            let raw_gravity = take_until_clause(after_gravity, &[",", " async"]).trim();
            if raw_gravity.is_empty() {
                (None, None)
            } else {
                let Some(value) = parse_assignment_value(raw_gravity)? else {
                    return Err(ParseError::InvalidNumber(raw_gravity.to_owned()));
                };
                (static_assignment_number(&value), Some(value))
            }
        } else {
            (None, None)
        };

    Ok(Some(CharacterModeStatement {
        target,
        gravity,
        gravity_value,
    }))
}

fn parse_clear_character_mode(line: &str) -> Option<String> {
    let (target, rest) = split_dot_command_rest(line)?;
    let rest = rest.trim();
    if rest.eq_ignore_ascii_case("clear character mode") {
        Some(target)
    } else {
        None
    }
}

fn parse_character_ignore(line: &str) -> Option<CharacterIgnoreStatement> {
    let (target, rest) = split_dot_command_rest(line)?;
    let rest = rest.trim();
    let after_ignore = rest.strip_prefix("character.ignore")?.trim();
    let ignored = after_ignore
        .split("//")
        .next()
        .unwrap_or_default()
        .trim()
        .split_whitespace()
        .next()
        .unwrap_or_default();
    (!ignored.is_empty()).then(|| CharacterIgnoreStatement {
        target,
        ignored: ignored.to_owned(),
    })
}

fn parse_character_jump(line: &str) -> Result<Option<CharacterJumpStatement>, ParseError> {
    let Some((target, rest)) = line.split_once(".character.jump") else {
        return Ok(None);
    };
    let target = target.trim();
    if target.is_empty() {
        return Ok(None);
    }
    let lower = rest.to_ascii_lowercase();
    let Some(speed_index) = lower.find("speed of").or_else(|| lower.find("speed")) else {
        return Ok(None);
    };
    let speed_prefix = if lower[speed_index..].starts_with("speed of") {
        "speed of"
    } else {
        "speed"
    };
    let raw_speed = take_until_clause(
        rest[speed_index + speed_prefix.len()..].trim(),
        &[",", ":", " async"],
    )
    .trim();
    if raw_speed.is_empty() {
        return Ok(None);
    }
    let Some(speed_value) = parse_assignment_value(raw_speed)? else {
        return Err(ParseError::InvalidNumber(raw_speed.to_owned()));
    };
    let speed = static_assignment_number(&speed_value).unwrap_or(0.0);
    Ok(Some(CharacterJumpStatement {
        target: target.to_owned(),
        speed,
        speed_value,
        async_run: contains_keyword(rest, "async"),
    }))
}

fn parse_physics_command(line: &str) -> Result<Option<Statement>, ParseError> {
    let Some((target, rest)) = split_dot_command_rest(line) else {
        return Ok(None);
    };
    let rest = rest.trim();
    if !rest.to_ascii_lowercase().starts_with("physics") {
        return Ok(None);
    }
    let parts = rest.split_whitespace().collect::<Vec<_>>();
    if parts.len() >= 2 && parts[1].eq_ignore_ascii_case("stop") {
        return Ok(Some(Statement::PhysicsStop { target }));
    }
    if parts.len() < 4 || !parts[1].eq_ignore_ascii_case("impulse") {
        return Ok(None);
    }
    let Some(direction) = parse_physics_direction(parts[2]) else {
        return Ok(None);
    };
    let raw_strength = take_until_clause(
        rest.split_once(parts[2])
            .map(|(_, value)| value.trim())
            .unwrap_or_default(),
        &[" for ", " in ", " async"],
    )
    .trim();
    let Some(strength_value) = parse_assignment_value(raw_strength)? else {
        return Err(ParseError::InvalidNumber(raw_strength.to_owned()));
    };
    let strength = static_assignment_number(&strength_value).unwrap_or(0.0);
    Ok(Some(Statement::PhysicsImpulse(PhysicsImpulseStatement {
        target,
        direction,
        strength,
        strength_value,
    })))
}

fn parse_physics_direction(text: &str) -> Option<PhysicsDirection> {
    match text.to_ascii_lowercase().as_str() {
        "up" => Some(PhysicsDirection::Up),
        "down" => Some(PhysicsDirection::Down),
        "forward" => Some(PhysicsDirection::Forward),
        "backward" | "back" => Some(PhysicsDirection::Backward),
        "left" => Some(PhysicsDirection::Left),
        "right" => Some(PhysicsDirection::Right),
        _ => None,
    }
}

fn parse_throw_at(line: &str) -> Result<Option<PhysicsThrowAtStatement>, ParseError> {
    let Some((target, rest)) = split_dot_command_rest(line) else {
        return Ok(None);
    };
    let rest = rest.trim();
    if !rest.to_ascii_lowercase().starts_with("throw at ") {
        return Ok(None);
    }
    let after_throw = rest["throw at ".len()..].trim();
    let Some((subject, power_text)) = split_once_case_insensitive(after_throw, " power ") else {
        return Ok(None);
    };
    let subject = normalize_entity_reference(subject);
    if !is_variable_path(&subject) {
        return Ok(None);
    }
    let Some(power) = parse_assignment_value(power_text.trim())? else {
        return Ok(None);
    };
    Ok(Some(PhysicsThrowAtStatement {
        target,
        subject,
        power,
    }))
}

fn parse_animation(line: &str) -> Result<Option<AnimationStatement>, ParseError> {
    let Some((target, rest)) = split_dot_command_rest(line) else {
        return Ok(None);
    };
    if target.is_empty()
        || target.eq_ignore_ascii_case("camera")
        || target.eq_ignore_ascii_case("scene")
        || target.eq_ignore_ascii_case("ui")
        || target.eq_ignore_ascii_case("screen")
        || target.eq_ignore_ascii_case("audio")
        || target.eq_ignore_ascii_case("lights")
        || target.eq_ignore_ascii_case("skybox")
        || target.contains(' ')
    {
        return Ok(None);
    }

    let rest = rest.trim();
    if rest.is_empty() || rest.starts_with('=') {
        return Ok(None);
    }

    let (clip, after_clip) = if let Some(stripped) = rest.strip_prefix('"') {
        let Some(end) = stripped.find('"') else {
            return Ok(None);
        };
        (stripped[..end].to_owned(), &stripped[end + 1..])
    } else {
        let end = rest
            .find(|value: char| value.is_whitespace() || value == ':' || value == '(')
            .unwrap_or(rest.len());
        (rest[..end].to_owned(), &rest[end..])
    };

    if clip.is_empty() {
        return Ok(None);
    }

    if clip.contains('.') || after_clip.contains('=') {
        return Ok(None);
    }

    let looped = contains_keyword(after_clip, "loop");
    let async_animation = contains_keyword(after_clip, "async");

    let speed_value = parse_speed_value(after_clip)?;
    let speed = static_assignment_number(&speed_value).unwrap_or(1.0);
    Ok(Some(AnimationStatement {
        target: target.to_owned(),
        clip,
        speed,
        speed_value,
        looped,
        blocking: !looped && !async_animation,
    }))
}

fn parse_sprite_play(line: &str) -> Result<Option<SpritePlayStatement>, ParseError> {
    let Some((target, rest)) = split_dot_command_rest(line) else {
        return Ok(None);
    };
    let rest = rest.trim();
    let lower = rest.to_ascii_lowercase();
    if !lower.starts_with("play") {
        return Ok(None);
    }
    let Some(frame_index) = lower.find("frame") else {
        return Ok(None);
    };
    let frame_text = &rest[frame_index + "frame".len()..];
    let Some((from_text, after_from)) = split_once_case_insensitive(frame_text, " to ") else {
        return Ok(None);
    };
    let Some(from_frame_value) = parse_assignment_value(from_text.trim())? else {
        return Err(ParseError::InvalidNumber(from_text.trim().to_owned()));
    };
    let from_frame = static_assignment_number(&from_frame_value)
        .unwrap_or(0.0)
        .max(0.0) as usize;
    let to_raw =
        take_until_clause(after_from.trim(), &[" in ", " for ", " loop ", ")", ","]).trim();
    if to_raw.is_empty() {
        return Ok(None);
    }
    let Some(to_frame_value) = parse_assignment_value(to_raw)? else {
        return Err(ParseError::InvalidNumber(to_raw.to_owned()));
    };
    let to_frame = static_assignment_number(&to_frame_value)
        .unwrap_or(0.0)
        .max(0.0) as usize;
    let (duration_value, duration_seconds) =
        parse_directional_move_duration_value(rest)?.unwrap_or((AssignmentValue::Number(1.0), 1.0));
    let duration_seconds = duration_seconds.max(0.001);
    Ok(Some(SpritePlayStatement {
        target: target.to_owned(),
        from_frame,
        from_frame_value,
        to_frame,
        to_frame_value,
        duration_seconds,
        duration_value,
        looped: contains_keyword(rest, "loop"),
    }))
}

fn parse_effekseer_play(line: &str) -> Result<Option<EffekseerPlayStatement>, ParseError> {
    let Some((target, rest)) = split_dot_command_rest(line) else {
        return Ok(None);
    };
    let rest = rest.trim();
    if !starts_with_keyword(rest, "play") {
        return Ok(None);
    }
    let options_text = rest["play".len()..].trim();
    let option_parts = split_top_level_options(options_text);
    let mut position = None;
    let mut looped = false;
    let mut attrs = Vec::new();
    let async_run = contains_keyword(options_text, "async");
    let mut recognized_effekseer_option = false;

    for option in option_parts {
        let option = option.trim();
        if option.is_empty() {
            continue;
        }
        if starts_with_keyword(option, "pos") {
            position = parse_effekseer_position_option(option)?;
            recognized_effekseer_option = true;
        } else if option.eq_ignore_ascii_case("loop") {
            looped = true;
            recognized_effekseer_option = true;
        } else if option.to_ascii_lowercase().starts_with("attr") {
            attrs.extend(parse_effekseer_attr_list(option)?);
            recognized_effekseer_option = true;
        }
    }
    if !recognized_effekseer_option {
        return Ok(None);
    }

    if let Some((_, value)) = attrs
        .iter()
        .find(|(key, _)| key.eq_ignore_ascii_case("loop"))
        && assignment_value_is_truthy_literal(value)
    {
        looped = true;
    }

    Ok(Some(EffekseerPlayStatement {
        target,
        position,
        looped,
        attrs,
        async_run,
    }))
}

fn parse_effekseer_position_option(option: &str) -> Result<Option<PositionValue>, ParseError> {
    let Some(raw_values) = values_inside_first_parens(option) else {
        return Ok(None);
    };
    let parts = split_top_level_comma(raw_values);
    if parts.len() == 3 {
        return Ok(Some(PositionValue::Coordinates(
            parts
                .into_iter()
                .map(parse_position_expr)
                .collect::<Result<Vec<_>, _>>()?,
        )));
    }
    let subject = raw_values.trim();
    Ok((!subject.is_empty()).then(|| PositionValue::Entity(subject.to_owned())))
}

fn parse_effekseer_attr_list(option: &str) -> Result<Vec<(String, AssignmentValue)>, ParseError> {
    let Some(open_index) = option.find('[') else {
        return Ok(Vec::new());
    };
    let after_open = &option[open_index + 1..];
    let Some(close_index) = matching_close_bracket_index(after_open) else {
        return Ok(Vec::new());
    };
    split_top_level_options(&after_open[..close_index])
        .into_iter()
        .filter(|part| !part.trim().is_empty())
        .map(parse_effekseer_attr)
        .collect()
}

fn parse_effekseer_attr(part: &str) -> Result<(String, AssignmentValue), ParseError> {
    let part = part.trim();
    let (key, value_text) = if let Some(after_open) = part.strip_prefix('"') {
        let Some(close_index) = after_open.find('"') else {
            return Err(ParseError::InvalidNumber(part.to_owned()));
        };
        (
            after_open[..close_index].to_owned(),
            after_open[close_index + 1..].trim(),
        )
    } else {
        let mut pieces = part.splitn(2, char::is_whitespace);
        (
            pieces.next().unwrap_or_default().trim().to_owned(),
            pieces.next().unwrap_or_default().trim(),
        )
    };
    if key.is_empty() || value_text.is_empty() {
        return Err(ParseError::InvalidNumber(part.to_owned()));
    }
    let Some(value) = parse_assignment_value(value_text.trim_start_matches('=').trim())? else {
        return Err(ParseError::InvalidNumber(value_text.to_owned()));
    };
    Ok((key.to_ascii_lowercase(), value))
}

fn assignment_value_is_truthy_literal(value: &AssignmentValue) -> bool {
    match value {
        AssignmentValue::Number(value) => *value != 0.0,
        AssignmentValue::Symbol(value) => value.eq_ignore_ascii_case("true"),
        _ => false,
    }
}

fn parse_channel_draw(line: &str) -> Result<Option<ChannelDrawStatement>, ParseError> {
    let Some((channel, rest)) = split_dot_command_rest(line) else {
        return Ok(None);
    };
    let rest = rest.trim();
    if !starts_with_keyword(rest, "draw") {
        return Ok(None);
    }
    let after_draw = rest["draw".len()..].trim();
    if after_draw.is_empty() {
        return Ok(None);
    }
    let (resource_text, attrs_text) = split_channel_draw_resource_and_attrs(after_draw);
    let resource = resource_text.trim();
    if !is_variable_name(resource) && !resource.eq_ignore_ascii_case("clear") {
        return Ok(None);
    }

    let mut statement = ChannelDrawStatement {
        channel,
        resource: resource.to_owned(),
        clear: resource.eq_ignore_ascii_case("clear"),
        pos_x: None,
        pos_y: None,
        width: None,
        height: None,
        frame: None,
        stretch: false,
    };
    parse_channel_draw_attrs(attrs_text, &mut statement)?;
    Ok(Some(statement))
}

fn split_channel_draw_resource_and_attrs(text: &str) -> (&str, &str) {
    let mut resource_end = text.len();
    if let Some(index) = text.find(':') {
        resource_end = resource_end.min(index);
    }
    if let Some((before, _)) = split_once_case_insensitive(text, " having ") {
        resource_end = resource_end.min(before.len());
    }
    let resource = text[..resource_end].trim();
    let attrs = text[resource_end..]
        .trim()
        .trim_start_matches(':')
        .trim()
        .strip_prefix("having ")
        .or_else(|| text[resource_end..].trim().strip_prefix("Having "))
        .unwrap_or_else(|| text[resource_end..].trim())
        .trim();
    (resource, attrs)
}

fn parse_channel_draw_attrs(
    attrs_text: &str,
    statement: &mut ChannelDrawStatement,
) -> Result<(), ParseError> {
    if attrs_text.is_empty() {
        return Ok(());
    }
    for attr in split_channel_draw_attrs(attrs_text) {
        let attr = attr.trim().trim_start_matches(':').trim();
        if attr.is_empty() {
            continue;
        }
        if attr.eq_ignore_ascii_case("stretch") {
            statement.stretch = true;
            continue;
        }
        if starts_with_keyword(attr, "stretch") {
            statement.stretch = true;
            continue;
        }
        if starts_with_keyword(attr, "frames") || starts_with_keyword(attr, "frame") {
            let value = attr
                .split_once(char::is_whitespace)
                .map(|(_, value)| value.trim())
                .unwrap_or_default();
            if let Some(frame) = parse_assignment_value(clean_assignment_value(value))? {
                statement.frame = Some(frame);
            }
            continue;
        }
        if starts_with_keyword(attr, "pos") {
            if let Some((x, y)) = parse_channel_draw_pair(attr, "pos")? {
                statement.pos_x = Some(x);
                statement.pos_y = Some(y);
            }
            continue;
        }
        if starts_with_keyword(attr, "size") {
            if let Some((width, height)) = parse_channel_draw_pair(attr, "size")? {
                statement.width = Some(width);
                statement.height = Some(height);
            }
        }
    }
    Ok(())
}

fn split_channel_draw_attrs(text: &str) -> Vec<&str> {
    split_top_level_comma(text)
        .into_iter()
        .flat_map(split_top_level_and)
        .collect()
}

fn split_top_level_and(text: &str) -> Vec<&str> {
    let mut parts = Vec::new();
    let mut remaining = text.trim();
    while let Some((left, right)) = split_once_case_insensitive(remaining, " and ") {
        let left = left.trim();
        if !left.is_empty() {
            parts.push(left);
        }
        remaining = right.trim();
    }
    if !remaining.is_empty() {
        parts.push(remaining);
    }
    parts
}

fn parse_channel_draw_pair(
    attr: &str,
    keyword: &str,
) -> Result<Option<(AssignmentValue, AssignmentValue)>, ParseError> {
    let Some(open_index) = attr.find('(') else {
        return Ok(None);
    };
    if !attr[..open_index].trim().eq_ignore_ascii_case(keyword) {
        return Ok(None);
    }
    let Some(close_index) = attr.rfind(')') else {
        return Ok(None);
    };
    let parts = split_top_level_comma(&attr[open_index + 1..close_index]);
    let (Some(x), Some(y)) = (parts.first(), parts.get(1)) else {
        return Ok(None);
    };
    let Some(x) = parse_assignment_value(clean_assignment_value(x))? else {
        return Ok(None);
    };
    let Some(y) = parse_assignment_value(clean_assignment_value(y))? else {
        return Ok(None);
    };
    Ok(Some((x, y)))
}

fn parse_cinematic_play(line: &str) -> Result<Option<CinematicPlayStatement>, ParseError> {
    let Some((target, rest)) = split_dot_command_rest(line) else {
        return Ok(None);
    };
    let rest = rest.trim();
    let lower = rest.to_ascii_lowercase();
    if !lower.starts_with("play") || lower.contains("frame") {
        return Ok(None);
    }

    let options_text = rest
        .split_once(':')
        .map(|(_, options)| options.trim())
        .or_else(|| {
            split_once_case_insensitive(rest, " having ").map(|(_, options)| options.trim())
        })
        .unwrap_or("");
    if options_text.is_empty() {
        return Ok(None);
    }

    let mut look_at = None;
    let mut duration_seconds = 10.0;
    let mut reverse = false;
    let mut has_cinematic_option = false;
    for option in split_cinematic_play_options(options_text) {
        let option = option.trim();
        if option.is_empty() {
            continue;
        }
        if option.eq_ignore_ascii_case("reverse") {
            reverse = true;
            has_cinematic_option = true;
            continue;
        }
        if let Some(value) = option_value_after_keyword(option, "duration") {
            let raw_duration = value.split_whitespace().next().unwrap_or(value);
            duration_seconds = raw_duration
                .parse::<f32>()
                .map_err(|_| ParseError::InvalidNumber(raw_duration.to_owned()))?
                .max(0.1);
            has_cinematic_option = true;
            continue;
        }
        if let Some(value) = option_value_after_keyword(option, "target") {
            let value = value.trim();
            if value.starts_with('(') && value.ends_with(')') {
                look_at = Some(CinematicLookAt::RelativePosition(
                    value[1..value.len() - 1].trim().to_owned(),
                ));
            } else {
                let entity = normalize_cinematic_target_reference(value);
                if is_variable_path(&entity) {
                    look_at = Some(CinematicLookAt::Entity(entity));
                }
            }
        }
    }
    if !has_cinematic_option {
        return Ok(None);
    }

    Ok(Some(CinematicPlayStatement {
        target,
        look_at,
        duration_seconds,
        reverse,
        async_run: contains_keyword(rest, "async"),
    }))
}

fn normalize_cinematic_target_reference(text: &str) -> String {
    text.trim()
        .trim_matches(|value| value == '(' || value == ')')
        .split_whitespace()
        .next()
        .unwrap_or_default()
        .to_owned()
}

fn split_cinematic_play_options(text: &str) -> Vec<&str> {
    split_top_level_comma(text)
        .into_iter()
        .flat_map(|part| {
            split_once_case_insensitive(part, " and ")
                .map_or(vec![part], |(left, right)| vec![left, right])
        })
        .collect()
}

fn option_value_after_keyword<'a>(text: &'a str, keyword: &str) -> Option<&'a str> {
    if !starts_with_keyword(text, keyword) {
        return None;
    }
    let rest = text[keyword.len()..].trim_start();
    Some(rest.strip_prefix('=').unwrap_or(rest).trim())
}

fn parse_animation_speed(line: &str) -> Result<Option<AnimationSpeedStatement>, ParseError> {
    let Some((target, rest)) = split_dot_command_rest(line) else {
        return Ok(None);
    };
    let rest = rest.trim();
    let lower = rest.to_ascii_lowercase();
    if !lower.starts_with("animation speed") {
        return Ok(None);
    }
    let raw_speed = take_until_clause(
        rest["animation speed".len()..].trim(),
        &[",", ":", " for ", " when ", " async"],
    )
    .trim();
    if raw_speed.is_empty() {
        return Ok(None);
    }
    let Some(speed_value) = parse_assignment_value(raw_speed)? else {
        return Err(ParseError::InvalidNumber(raw_speed.to_owned()));
    };
    let speed = static_assignment_number(&speed_value).unwrap_or(0.0);
    let duration = parse_duration_assignment_value(rest, " for ")?;
    let (duration_value, duration_seconds) = match duration {
        Some((value, seconds)) => (Some(value), Some(seconds)),
        None => (None, None),
    };
    let condition = split_once_case_insensitive(rest, " when ")
        .map(|(_, condition_text)| parse_condition(condition_text.trim()))
        .transpose()?
        .flatten();
    Ok(Some(AnimationSpeedStatement {
        target: target.to_owned(),
        speed,
        speed_value,
        duration_seconds,
        duration_value,
        condition,
    }))
}

fn is_unsupported_dotted_runtime_command(line: &str) -> bool {
    let Some((_target, rest)) = split_dot_command_rest(line) else {
        return false;
    };
    let lower = rest.trim().to_ascii_lowercase();
    lower.starts_with("switch ")
        || lower.starts_with("play ")
        || lower.starts_with("stop ")
        || lower.starts_with("clear ")
}

fn is_runtime_noop_command(line: &str) -> bool {
    let line = line.trim();
    let lower = line.to_ascii_lowercase();
    if lower.starts_with("camera.chase ") || lower == "camera.chase stop" {
        return true;
    }
    if lower.starts_with("anim.event(") {
        return true;
    }

    let Some((_target, rest)) = split_dot_command_rest(line) else {
        return false;
    };
    let rest = rest.trim().to_ascii_lowercase();
    rest.starts_with("play pos")
        || rest.starts_with("play : target")
        || rest.starts_with("apply ")
        || rest == "stop"
        || rest.starts_with("stop ")
        || rest == "clear"
        || rest.starts_with("clear ")
}

fn parse_speed_value(text: &str) -> Result<AssignmentValue, ParseError> {
    let lower = text.to_ascii_lowercase();
    let Some(index) = lower.find("speed of") else {
        return Ok(AssignmentValue::Number(1.0));
    };
    let raw = take_until_clause(
        text[index + "speed of".len()..].trim(),
        &[
            ",", ":", " loop ", " loop", " async", " when ", " for ", " in ",
        ],
    )
    .trim();
    if raw.is_empty() {
        return Ok(AssignmentValue::Number(1.0));
    }
    let Some(value) = parse_assignment_value(raw)? else {
        return Err(ParseError::InvalidNumber(raw.to_owned()));
    };
    Ok(value)
}

fn parse_directional_move_duration_value(
    text: &str,
) -> Result<Option<(AssignmentValue, f32)>, ParseError> {
    match parse_duration_assignment_value(text, " for ")? {
        Some(value) => Ok(Some(value)),
        None => parse_duration_assignment_value(text, " in "),
    }
}

fn parse_duration_assignment_value(
    text: &str,
    marker: &str,
) -> Result<Option<(AssignmentValue, f32)>, ParseError> {
    let Some((_, marker_end)) = find_case_insensitive_span(text, marker) else {
        return Ok(None);
    };
    let after = text[marker_end..].trim_start();
    let end = [" seconds", " second", " loop ", " while ", " async"]
        .into_iter()
        .filter_map(|needle| find_case_insensitive_span(after, needle).map(|(start, _)| start))
        .min()
        .unwrap_or(after.len());
    let raw = after[..end].trim();
    if raw.is_empty() {
        return Ok(None);
    }
    let Some(value) = parse_assignment_value(raw)? else {
        return Err(ParseError::InvalidNumber(raw.to_owned()));
    };
    let seconds = static_assignment_number(&value).unwrap_or(0.0);
    Ok(Some((value, seconds)))
}

fn split_resource_and_options(rest: &str) -> (&str, &str) {
    if let Some((resource, options)) = rest.split_once(':') {
        (resource.trim(), options.trim())
    } else {
        let lower = rest.to_ascii_lowercase();
        let option_start = [" pos ", " scale ", " rotate", " hidden", " and "]
            .into_iter()
            .filter_map(|needle| lower.find(needle))
            .min();
        if let Some(index) = option_start {
            (rest[..index].trim(), rest[index..].trim())
        } else {
            (rest.trim(), "")
        }
    }
}

fn normalize_resource(resource: &str) -> String {
    let parts = resource
        .split_whitespace()
        .filter(|part| {
            !part.eq_ignore_ascii_case("dynamic")
                && !part.eq_ignore_ascii_case("static")
                && !part.eq_ignore_ascii_case("collider")
        })
        .collect::<Vec<_>>();
    let normalized = parts.join(" ");
    if is_java_primitive_resource(&normalized) {
        return normalized;
    }
    parts.first().unwrap_or(&resource.trim()).trim().to_owned()
}

fn is_java_primitive_resource(resource: &str) -> bool {
    matches!(
        resource.to_ascii_lowercase().as_str(),
        "sphere"
            | "box"
            | "cylinder"
            | "hollow cylinder"
            | "quad"
            | "wedge"
            | "cone"
            | "stairs"
            | "arch"
    )
}

fn is_deferred_or_non_model_resource(_raw_resource: &str, resource: &str) -> bool {
    let resource_lower = resource.to_ascii_lowercase();
    resource_lower.starts_with("object.pool")
}

fn parse_size_after(text: &str) -> Result<Option<SceneMaxVec3>, ParseError> {
    let Some(values) = parse_float_list_after(text, "size")? else {
        return Ok(None);
    };
    match values.as_slice() {
        [x, y] => Ok(Some(SceneMaxVec3 {
            x: *x,
            y: *y,
            z: 1.0,
        })),
        [x, y, z] => Ok(Some(SceneMaxVec3 {
            x: *x,
            y: *y,
            z: *z,
        })),
        _ => Err(ParseError::InvalidNumber(
            values
                .iter()
                .map(|value| value.to_string())
                .collect::<Vec<_>>()
                .join(","),
        )),
    }
}

fn parse_float_pair_after(text: &str, name: &str) -> Result<Option<(f32, f32)>, ParseError> {
    let Some(values) = parse_float_list_after(text, name)? else {
        return Ok(None);
    };
    match values.as_slice() {
        [left, right] => Ok(Some((*left, *right))),
        _ => Err(ParseError::InvalidNumber(
            values
                .iter()
                .map(|value| value.to_string())
                .collect::<Vec<_>>()
                .join(","),
        )),
    }
}

fn parse_float_list_after(text: &str, name: &str) -> Result<Option<Vec<f32>>, ParseError> {
    let lower = text.to_ascii_lowercase();
    let needle = name.to_ascii_lowercase();
    let Some(after_open) = lower.match_indices(&needle).find_map(|(index, _)| {
        let after_name = &text[index + name.len()..];
        after_name.trim_start().strip_prefix('(')
    }) else {
        return Ok(None);
    };
    let Some(close_index) = after_open.find(')') else {
        return Err(ParseError::InvalidNumber(name.to_owned()));
    };
    let raw_values = &after_open[..close_index];
    raw_values
        .split(',')
        .map(|raw| {
            let value = raw.trim();
            value
                .parse::<f32>()
                .map_err(|_| ParseError::InvalidNumber(value.to_owned()))
        })
        .collect::<Result<Vec<_>, _>>()
        .map(Some)
}

fn parse_usize_after(text: &str, name: &str) -> Option<usize> {
    parse_scalar_after(text, name)
        .ok()
        .flatten()
        .map(|value| value.round().max(1.0) as usize)
}

fn parse_inner_radius_pair_after(text: &str) -> Result<Option<(f32, f32)>, ParseError> {
    let lower = text.to_ascii_lowercase();
    let Some(index) = lower.find("inner radius") else {
        return Ok(None);
    };
    parse_float_pair_after(&text[index + "inner ".len()..], "radius")
}

fn parse_outer_radius_pair_after(text: &str) -> Result<Option<(f32, f32)>, ParseError> {
    let lower = text.to_ascii_lowercase();
    let Some(index) = lower.find("radius") else {
        return Ok(None);
    };
    if lower[..index].ends_with("inner ") {
        return Ok(None);
    }
    parse_float_pair_after(&text[index..], "radius")
}

fn parse_entity_options(raw: &str, text: &str) -> Result<EntityOptions, ParseError> {
    let scale = parse_scalar_after(text, "scale")?.map(|value| SceneMaxVec3 {
        x: value,
        y: value,
        z: value,
    });
    let outer_radius = parse_outer_radius_pair_after(text)?;
    let inner_radius = parse_inner_radius_pair_after(text)?;
    let position = parse_vec3_after(text, "pos").ok();
    let position_value = parse_position_value_after(text, "pos")?;
    Ok(EntityOptions {
        position,
        position_value,
        rotation_degrees: parse_vec3_after(text, "rotate").ok(),
        scale,
        size: parse_size_after(text)?,
        material: parse_quoted_value_after(text, "material"),
        hidden: contains_keyword(text, "hidden"),
        collider: contains_keyword(raw, "collider"),
        radius: parse_scalar_after(text, "radius")?,
        radius_top: outer_radius.map(|value| value.0),
        radius_bottom: outer_radius.map(|value| value.1),
        inner_radius_top: inner_radius.map(|value| value.0),
        inner_radius_bottom: inner_radius.map(|value| value.1),
        height: parse_scalar_after(text, "height")?,
        steps: parse_usize_after(text, "steps"),
        thickness: parse_scalar_after(text, "thickness")?,
        segments: parse_usize_after(text, "segments"),
        body_kind: parse_body_kind(raw),
        collision_shape: parse_collision_shape(raw),
        sprite: contains_keyword(raw, "sprite"),
    })
}

fn parse_quoted_value_after(text: &str, name: &str) -> Option<String> {
    let lower = text.to_ascii_lowercase();
    let name = name.to_ascii_lowercase();
    let index = lower.find(&name)?;
    let after_name = text[index + name.len()..].trim_start();
    parse_quoted_strings(after_name).into_iter().next()
}

fn parse_position_value_after(text: &str, name: &str) -> Result<Option<PositionValue>, ParseError> {
    let lower = text.to_ascii_lowercase();
    let Some(after_open) =
        lower
            .match_indices(&name.to_ascii_lowercase())
            .find_map(|(index, _)| {
                let after_name = &text[index + name.len()..];
                after_name.trim_start().strip_prefix('(')
            })
    else {
        return Ok(None);
    };
    let Some(close_index) = matching_close_paren_index(after_open) else {
        return Ok(None);
    };
    let raw_values = &after_open[..close_index];
    let parts = split_top_level_comma(raw_values);
    if parts.len() != 3 {
        return Ok(None);
    }
    let values = parts
        .into_iter()
        .map(parse_position_expr)
        .collect::<Result<Vec<_>, _>>()?;
    if values
        .iter()
        .all(|value| matches!(value, PositionExpr::Number(_)))
    {
        return Ok(None);
    }
    Ok(Some(PositionValue::Coordinates(values)))
}

fn matching_close_paren_index(text_after_open: &str) -> Option<usize> {
    let mut depth = 0usize;
    for (index, value) in text_after_open.char_indices() {
        match value {
            '(' => depth += 1,
            ')' if depth == 0 => return Some(index),
            ')' => depth = depth.saturating_sub(1),
            _ => {}
        }
    }
    None
}

fn parse_body_kind(text: &str) -> Option<SceneMaxBodyKind> {
    let lower = text.to_ascii_lowercase();
    if contains_keyword(&lower, "static") {
        Some(SceneMaxBodyKind::Static)
    } else if contains_keyword(&lower, "dynamic") {
        Some(SceneMaxBodyKind::Kinematic)
    } else if contains_keyword(&lower, "mass") {
        Some(SceneMaxBodyKind::Dynamic)
    } else {
        None
    }
}

fn parse_collision_shape(text: &str) -> Option<SceneMaxCollisionShape> {
    let lower = text.to_ascii_lowercase();
    if contains_keyword(&lower, "collider") {
        if contains_keyword(&lower, "sphere") {
            return Some(SceneMaxCollisionShape::Sphere);
        }
        if contains_keyword(&lower, "capsule") {
            return Some(SceneMaxCollisionShape::Capsule);
        }
        return Some(SceneMaxCollisionShape::Box);
    }
    let Some(index) = lower.find("collision shape") else {
        return None;
    };
    let shape = lower[index + "collision shape".len()..]
        .split(|value: char| value.is_whitespace() || value == ',' || value == ':')
        .find(|part| !part.is_empty())
        .unwrap_or_default();
    match shape {
        "none" => Some(SceneMaxCollisionShape::None),
        "sphere" => Some(SceneMaxCollisionShape::Sphere),
        "capsule" => Some(SceneMaxCollisionShape::Capsule),
        "box" | "" => Some(SceneMaxCollisionShape::Box),
        _ => Some(SceneMaxCollisionShape::Box),
    }
}

fn parse_vec3_after(text: &str, name: &str) -> Result<SceneMaxVec3, ParseError> {
    let lower = text.to_ascii_lowercase();
    let needle = name.to_ascii_lowercase();
    let Some(after_open) = lower.match_indices(&needle).find_map(|(index, _)| {
        let after_name = &text[index + name.len()..];
        after_name.trim_start().strip_prefix('(')
    }) else {
        return Err(ParseError::InvalidNumber(name.to_owned()));
    };
    let Some(close_index) = after_open.find(')') else {
        return Err(ParseError::InvalidNumber(name.to_owned()));
    };
    let raw_values = &after_open[..close_index];
    let values = raw_values
        .split(',')
        .map(|raw| {
            let value = raw.trim();
            value
                .parse::<f32>()
                .map_err(|_| ParseError::InvalidNumber(value.to_owned()))
        })
        .collect::<Result<Vec<_>, _>>()?;

    if values.len() != 3 {
        return Err(ParseError::InvalidNumber(raw_values.to_owned()));
    }

    Ok(SceneMaxVec3 {
        x: values[0],
        y: values[1],
        z: values[2],
    })
}

fn parse_scalar_after(text: &str, name: &str) -> Result<Option<f32>, ParseError> {
    let lower = text.to_ascii_lowercase();
    let Some(index) = lower.find(&name.to_ascii_lowercase()) else {
        return Ok(None);
    };
    let after = text[index + name.len()..].trim_start();
    if after.starts_with('(') {
        return Ok(None);
    }
    let raw = after
        .split(|value: char| value.is_whitespace() || value == ',' || value == ':' || value == ')')
        .next()
        .unwrap_or_default();
    if raw.is_empty() {
        return Ok(None);
    }
    Ok(raw.parse::<f32>().ok())
}

fn logical_lines(source: &str) -> Vec<String> {
    let mut result: Vec<String> = Vec::new();
    for raw_line in source.lines() {
        let line = strip_comment(raw_line).trim();
        if line.is_empty() {
            continue;
        }

        if starts_continuation_line(line)
            || result.last().is_some_and(|previous| is_open_add(previous))
            || result
                .last()
                .is_some_and(|previous| is_open_assignment_list(previous))
            || result
                .last()
                .is_some_and(|previous| is_open_guard_definition(previous))
            || result
                .last()
                .is_some_and(|previous| is_open_multiline_statement(previous))
        {
            if let Some(previous) = result.last_mut() {
                previous.push(' ');
                previous.push_str(line);
            }
        } else {
            result.push(line.to_owned());
        }
    }
    result
}

fn starts_continuation_line(line: &str) -> bool {
    let trimmed = line.trim_start();
    trimmed.starts_with(',')
        || trimmed.starts_with('.')
        || trimmed.starts_with("&&")
        || trimmed.starts_with("||")
        || trimmed.starts_with("==")
        || trimmed.starts_with("!=")
        || trimmed.starts_with("<=")
        || trimmed.starts_with(">=")
        || trimmed.starts_with('+')
        || trimmed.starts_with('*')
        || trimmed.starts_with('/')
}

fn is_open_multiline_statement(line: &str) -> bool {
    let trimmed = line.trim_end();
    if trimmed.ends_with('{') || trimmed.eq_ignore_ascii_case("do") {
        return false;
    }
    unclosed_paren_count(trimmed) > 0
        || unclosed_bracket_count(trimmed) > 0
        || trimmed.ends_with('.')
        || trimmed.ends_with(',')
        || trimmed.ends_with("&&")
        || trimmed.ends_with("||")
        || trimmed.ends_with("==")
        || trimmed.ends_with("!=")
        || trimmed.ends_with("<=")
        || trimmed.ends_with(">=")
        || trimmed.ends_with('+')
        || trimmed.ends_with('-')
        || trimmed.ends_with('*')
        || trimmed.ends_with('/')
}

fn is_open_assignment_list(line: &str) -> bool {
    let trimmed = line.trim();
    (starts_with_keyword(trimmed, "var") || starts_with_two_keywords(trimmed, "shared", "var"))
        && trimmed.ends_with(',')
}

fn is_open_guard_definition(line: &str) -> bool {
    let trimmed = line.trim();
    strip_var_prefix(trimmed).starts_with('@')
        && (unclosed_paren_count(trimmed) > 0 || trimmed.ends_with("&&") || trimmed.ends_with("||"))
}

fn unclosed_paren_count(text: &str) -> usize {
    let mut depth = 0usize;
    for value in text.chars() {
        match value {
            '(' => depth += 1,
            ')' => depth = depth.saturating_sub(1),
            _ => {}
        }
    }
    depth
}

fn unclosed_bracket_count(text: &str) -> usize {
    let mut depth = 0usize;
    for value in text.chars() {
        match value {
            '[' => depth += 1,
            ']' => depth = depth.saturating_sub(1),
            _ => {}
        }
    }
    depth
}

fn is_open_add(line: &str) -> bool {
    starts_with_keyword(line, "add") && !has_code_terminator(line)
}

fn has_code_terminator(line: &str) -> bool {
    line.split(|value: char| value.is_whitespace() || value == ',')
        .any(|part| part.eq_ignore_ascii_case("code"))
}

fn strip_comment(line: &str) -> &str {
    let mut in_quote = false;
    for (index, value) in line.char_indices() {
        if value == '"' {
            in_quote = !in_quote;
        }
        if !in_quote && line[index..].starts_with("//") {
            return &line[..index];
        }
    }
    line
}

fn parse_quoted_strings(text: &str) -> Vec<String> {
    let mut values = Vec::new();
    let mut chars = text.char_indices().peekable();
    while let Some((_, value)) = chars.next() {
        if value != '"' {
            continue;
        }

        let mut raw = String::new();
        while let Some((_, value)) = chars.next() {
            if value == '"' {
                break;
            }
            if value == '\\' {
                if let Some((_, escaped)) = chars.peek().copied() {
                    raw.push(escaped);
                    chars.next();
                    continue;
                }
            }
            raw.push(value);
        }
        values.push(raw);
    }
    values
}

fn starts_with_keyword(text: &str, keyword: &str) -> bool {
    let text = text.trim_start();
    text.len() >= keyword.len()
        && text[..keyword.len()].eq_ignore_ascii_case(keyword)
        && text[keyword.len()..]
            .chars()
            .next()
            .is_none_or(|value| value.is_whitespace() || value == '"' || value == '(')
}

fn strip_keyword_prefix<'a>(text: &'a str, keyword: &str) -> Option<&'a str> {
    starts_with_keyword(text, keyword).then(|| text.trim_start()[keyword.len()..].trim_start())
}

fn starts_with_two_keywords(text: &str, first: &str, second: &str) -> bool {
    let text = text.trim_start();
    if !starts_with_keyword(text, first) {
        return false;
    }
    starts_with_keyword(text[first.len()..].trim_start(), second)
}

fn strip_var_prefix(text: &str) -> &str {
    let text = text.trim();
    if starts_with_two_keywords(text, "shared", "var") {
        return text["shared".len()..].trim_start()["var".len()..].trim_start();
    }
    if starts_with_keyword(text, "var") {
        return text["var".len()..].trim_start();
    }
    text
}

fn split_dot_command(line: &str) -> Option<(String, String)> {
    let (target, rest) = split_dot_command_rest(line)?;
    let command = rest
        .split(|value: char| value.is_whitespace() || value == ':' || value == '(')
        .next()
        .unwrap_or_default();
    if command.is_empty() {
        None
    } else {
        Some((target, command.to_owned()))
    }
}

fn split_dot_command_rest(line: &str) -> Option<(String, &str)> {
    let (target, rest) = line.split_once('.')?;
    Some((target.trim().to_owned(), rest.trim()))
}

fn contains_keyword(text: &str, keyword: &str) -> bool {
    text.split(|value: char| !value.is_ascii_alphanumeric() && value != '_')
        .any(|part| part.eq_ignore_ascii_case(keyword))
}

fn unsupported(line: &str) -> Statement {
    Statement::Unsupported {
        text: line.to_owned(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_model_shader_assignment() {
        let program = parse_program("hero => avatar\nhero.shader = \"damage_flash\"").unwrap();

        assert_eq!(
            program.statements[1],
            Statement::SetShader(SetShaderStatement {
                target: "hero".to_owned(),
                shader: AssignmentValue::Symbol("damage_flash".to_owned()),
            })
        );
    }

    #[test]
    fn parses_environment_shader_assignment() {
        let program = parse_program("Scene.environment.shader = \"rainy_evening\"").unwrap();

        assert_eq!(
            program.statements[0],
            Statement::SetEnvironmentShader {
                shader: AssignmentValue::Symbol("rainy_evening".to_owned()),
            }
        );
    }

    #[test]
    fn parses_model_and_looping_animation() {
        let program = parse_program("d=>dragon\nd.fly loop").unwrap();

        assert_eq!(
            program,
            Program::new(vec![
                Statement::ModelDecl {
                    name: "d".to_owned(),
                    resource: "dragon".to_owned(),
                    options: EntityOptions::default(),
                },
                Statement::Animate(AnimationStatement {
                    target: "d".to_owned(),
                    clip: "fly".to_owned(),
                    speed: 1.0,
                    speed_value: AssignmentValue::Number(1.0),
                    looped: true,
                    blocking: false,
                }),
            ])
        );
    }

    #[test]
    fn parses_screen_mode_metadata() {
        let program = parse_program("screen.mode full\nscreen.mode window\nd=>dragon").unwrap();

        assert_eq!(program.screen_mode, ScreenMode::Full);
        assert!(matches!(
            program.statements.first(),
            Some(Statement::ModelDecl { name, .. }) if name == "d"
        ));
    }

    #[test]
    fn parses_borderless_screen_mode_metadata() {
        let program = parse_program("Screen.mode borderless").unwrap();
        let no_borders_program = parse_program("screen.mode no borders").unwrap();

        assert_eq!(program.screen_mode, ScreenMode::Borderless);
        assert_eq!(no_borders_program.screen_mode, ScreenMode::Borderless);
        assert!(program.statements.is_empty());
    }

    #[test]
    fn parses_light_declarations_and_probe_actions() {
        let program = parse_program(
            "sun => Lights.directional : color \"#fff3d2\", intensity 3.0, direction (-0.3,-0.8,-0.4), shadow high\n\
             lamp => Lights.point : pos (2,4,1), color warm, intensity 900 lumens, range 12, shadow medium\n\
             stageSpot => Lights.spot : pos (0,6,-4), look at player1, angle 35, intensity 2500, shadow on\n\
             environment => Lights.sky : preset \"Night Neon\", exposure 0.2, ambient \"#223344\"\n\
             Lights.Add Probe \"3\" Having pos (2,1,0)",
        )
        .expect("program parses");

        assert!(matches!(
            &program.statements[0],
            Statement::LightDecl(LightDeclarationStatement {
                name,
                light_type: LightType::Directional,
                color: Some(color),
                ..
            }) if name == "sun" && color == "#fff3d2"
        ));
        assert!(matches!(
            &program.statements[1],
            Statement::LightDecl(LightDeclarationStatement {
                name,
                light_type: LightType::Point,
                intensity_unit: Some(unit),
                shadow_mode: Some(shadow),
                ..
            }) if name == "lamp" && unit == "lumens" && shadow == "medium"
        ));
        assert!(matches!(
            &program.statements[2],
            Statement::LightDecl(LightDeclarationStatement {
                name,
                light_type: LightType::Spot,
                look_at: Some(target),
                ..
            }) if name == "stageSpot" && target == "player1"
        ));
        assert!(matches!(
            &program.statements[3],
            Statement::LightDecl(LightDeclarationStatement {
                name,
                light_type: LightType::Sky,
                preset: Some(preset),
                ambient_color: Some(ambient),
                ..
            }) if name == "environment" && preset == "Night Neon" && ambient == "#223344"
        ));
        assert!(matches!(
            &program.statements[4],
            Statement::LightProbeAdd(LightProbeAddStatement {
                name,
                position: Some(PositionValue::Coordinates(_)),
            }) if name == "3"
        ));
    }

    #[test]
    fn parses_sprite_play_frame_range() {
        let program =
            parse_program("b=>bird sprite\nb.play (frame 0 to 13 in 1 seconds) loop").unwrap();

        assert_eq!(
            program.statements,
            vec![
                Statement::ModelDecl {
                    name: "b".to_owned(),
                    resource: "bird".to_owned(),
                    options: EntityOptions {
                        sprite: true,
                        ..Default::default()
                    },
                },
                Statement::SpritePlay(SpritePlayStatement {
                    target: "b".to_owned(),
                    from_frame: 0,
                    from_frame_value: AssignmentValue::Number(0 as f32),
                    to_frame: 13,
                    to_frame_value: AssignmentValue::Number(13 as f32),
                    duration_seconds: 1.0,
                    duration_value: AssignmentValue::Number(1.0),
                    looped: true,
                }),
            ]
        );
    }

    #[test]
    fn parses_sprite_play_default_duration_as_one_second() {
        let program = parse_program("b=>bird sprite\nb.play (frame 0 to 15)").unwrap();

        assert!(matches!(
            &program.statements[1],
            Statement::SpritePlay(SpritePlayStatement {
                target,
                from_frame: 0,
                to_frame: 15,
                duration_seconds,
                duration_value: AssignmentValue::Number(1.0),
                looped: false,
                ..
            }) if target == "b" && (*duration_seconds - 1.0).abs() < f32::EPSILON
        ));
    }

    #[test]
    fn parses_cinematic_camera_play_options() {
        let program = parse_program(
            "cam=>cinematic.camera.cinematic1\ncam.play : target axe, duration 1.5, reverse",
        )
        .unwrap();

        assert_eq!(
            program.statements[1],
            Statement::CinematicPlay(CinematicPlayStatement {
                target: "cam".to_owned(),
                look_at: Some(CinematicLookAt::Entity("axe".to_owned())),
                duration_seconds: 1.5,
                reverse: true,
                async_run: false,
            })
        );
    }

    #[test]
    fn parses_cinematic_camera_equipped_weapon_target() {
        let program =
            parse_program("axe_throw_cam.play : target player1.weapon, duration 0.85").unwrap();

        assert_eq!(
            program.statements[0],
            Statement::CinematicPlay(CinematicPlayStatement {
                target: "axe_throw_cam".to_owned(),
                look_at: Some(CinematicLookAt::Entity("player1.weapon".to_owned())),
                duration_seconds: 0.85,
                reverse: false,
                async_run: false,
            })
        );
    }

    #[test]
    fn parses_cinematic_camera_relative_target() {
        let program =
            parse_program("cincam1.play : target (player1 forward 1 up 3), duration 2.8").unwrap();

        assert_eq!(
            program.statements[0],
            Statement::CinematicPlay(CinematicPlayStatement {
                target: "cincam1".to_owned(),
                look_at: Some(CinematicLookAt::RelativePosition(
                    "player1 forward 1 up 3".to_owned()
                )),
                duration_seconds: 2.8,
                reverse: false,
                async_run: false,
            })
        );
    }

    #[test]
    fn parses_cinematic_camera_async_suffix_after_duration() {
        let program =
            parse_program("intro_camera.play : target (model_1 up 30), duration 10 Async").unwrap();

        assert_eq!(
            program.statements[0],
            Statement::CinematicPlay(CinematicPlayStatement {
                target: "intro_camera".to_owned(),
                look_at: Some(CinematicLookAt::RelativePosition(
                    "model_1 up 30".to_owned()
                )),
                duration_seconds: 10.0,
                reverse: false,
                async_run: true,
            })
        );
    }

    #[test]
    fn parses_animation_speed_and_quoted_clip() {
        let program = parse_program("d.\"Fly Forward\" at speed of 0.5 loop").unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::Animate(AnimationStatement {
                target: "d".to_owned(),
                clip: "Fly Forward".to_owned(),
                speed: 0.5,
                speed_value: AssignmentValue::Number(0.5),
                looped: true,
                blocking: false,
            })]
        );
    }

    #[test]
    fn parses_channel_draw_clear_out_of_animation_parser() {
        let program = parse_program("intro.draw clear").unwrap();

        assert!(matches!(
            program.statements.as_slice(),
            [Statement::ChannelDraw(ChannelDrawStatement { channel, clear, .. })]
                if channel == "intro" && *clear
        ));
    }

    #[test]
    fn parses_channel_draw_stretched_splash_screen() {
        let program = parse_program("intro.draw intro_page: stretch").unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::ChannelDraw(ChannelDrawStatement {
                channel: "intro".to_owned(),
                resource: "intro_page".to_owned(),
                clear: false,
                pos_x: None,
                pos_y: None,
                width: None,
                height: None,
                frame: None,
                stretch: true,
            })]
        );
    }

    #[test]
    fn parses_channel_draw_frame_position_and_size() {
        let program = parse_program(
            "intro.draw intro_page having pos (10, 20) and frames frame_index and size (320, 180)",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::ChannelDraw(ChannelDrawStatement {
                channel: "intro".to_owned(),
                resource: "intro_page".to_owned(),
                clear: false,
                pos_x: Some(AssignmentValue::Number(10.0)),
                pos_y: Some(AssignmentValue::Number(20.0)),
                width: Some(AssignmentValue::Number(320.0)),
                height: Some(AssignmentValue::Number(180.0)),
                frame: Some(AssignmentValue::Symbol("frame_index".to_owned())),
                stretch: false,
            })]
        );
    }

    #[test]
    fn parses_character_switch_command() {
        let program = parse_program("player1.switch to character mode : gravity 60").unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::CharacterMode(CharacterModeStatement {
                target: "player1".to_owned(),
                gravity: Some(60.0),
                gravity_value: Some(AssignmentValue::Number(60.0)),
            })]
        );
    }

    #[test]
    fn parses_clear_character_mode_command() {
        let program = parse_program("player1.clear character mode").unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::ClearCharacterMode {
                target: "player1".to_owned(),
            }]
        );
    }

    #[test]
    fn parses_character_ignore_command() {
        let program =
            parse_program("player1.character.ignore player2.joints // avoid joints").unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::CharacterIgnore(CharacterIgnoreStatement {
                target: "player1".to_owned(),
                ignored: "player2.joints".to_owned(),
            })]
        );
    }

    #[test]
    fn parses_animation_speed_with_colon_suffix() {
        let program = parse_program("player1.right_death1 at speed of 1.2: protected").unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::Animate(AnimationStatement {
                target: "player1".to_owned(),
                clip: "right_death1".to_owned(),
                speed: 1.2,
                speed_value: AssignmentValue::Number(1.2),
                looped: false,
                blocking: true,
            })]
        );
    }

    #[test]
    fn parses_animation_speed_command_with_numeric_duration() {
        let program =
            parse_program("player2.animation speed 3 for 0.2 seconds when frames > 10").unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::AnimationSpeed(AnimationSpeedStatement {
                target: "player2".to_owned(),
                speed: 3.0,
                speed_value: AssignmentValue::Number(3.0),
                duration_seconds: Some(0.2),
                duration_value: Some(AssignmentValue::Number(0.2)),
                condition: Some(Condition::Compare {
                    name: "frames".to_owned(),
                    operator: ComparisonOperator::Greater,
                    value: AssignmentValue::Number(10.0),
                }),
            })]
        );
    }

    #[test]
    fn parses_animation_speed_command_with_symbolic_duration() {
        let program = parse_program("player1.animation speed 0.01 for tm seconds").unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::AnimationSpeed(AnimationSpeedStatement {
                target: "player1".to_owned(),
                speed: 0.01,
                speed_value: AssignmentValue::Number(0.01),
                duration_seconds: Some(0.0),
                duration_value: Some(AssignmentValue::Symbol("tm".to_owned())),
                condition: None,
            })]
        );
    }

    #[test]
    fn parses_animation_speed_command_with_tabbed_duration_clause() {
        let program =
            parse_program("player2.animation speed 0.01\tfor tm seconds when frames > frame_count")
                .unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::AnimationSpeed(AnimationSpeedStatement {
                target: "player2".to_owned(),
                speed: 0.01,
                speed_value: AssignmentValue::Number(0.01),
                duration_seconds: Some(0.0),
                duration_value: Some(AssignmentValue::Symbol("tm".to_owned())),
                condition: Some(Condition::Compare {
                    name: "frames".to_owned(),
                    operator: ComparisonOperator::Greater,
                    value: AssignmentValue::Symbol("frame_count".to_owned()),
                }),
            })]
        );
    }

    #[test]
    fn parses_function_with_tabbed_animation_speed_before_following_when() {
        let program = parse_program(
            "player1_slow_motion (tm, frame_count) = {\n\
             player2.animation speed 0.01\tfor tm seconds\n\
             player1.animation speed 0.01\tfor tm seconds when frames > frame_count\n\
             }\n\
             when player1_ko == 1 do\n\
             player1_ko=0\n\
             end do",
        )
        .unwrap();

        assert_eq!(program.statements.len(), 2);
        assert!(matches!(
            &program.statements[0],
            Statement::FunctionDef(function) if function.name == "player1_slow_motion"
        ));
        assert!(matches!(
            &program.statements[1],
            Statement::WhenEvent(when_event)
                if matches!(
                    &when_event.condition,
                    Condition::EqualsNumber { name, value }
                        if name == "player1_ko" && (*value - 1.0).abs() < f32::EPSILON
                )
        ));
    }

    #[test]
    fn parses_character_jump_command() {
        let program = parse_program("player1.character.jump at speed of 35 async").unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::CharacterJump(CharacterJumpStatement {
                target: "player1".to_owned(),
                speed: 35.0,
                speed_value: AssignmentValue::Number(35.0),
                async_run: true,
            })]
        );
    }

    #[test]
    fn tolerates_symbolic_model_scale_option() {
        let program =
            parse_program("rock1 => meshy_rock1_native : pos (1,2,3), scale rock_scale").unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::ModelDecl {
                name: "rock1".to_owned(),
                resource: "meshy_rock1_native".to_owned(),
                options: EntityOptions {
                    position: Some(SceneMaxVec3 {
                        x: 1.0,
                        y: 2.0,
                        z: 3.0,
                    }),
                    ..Default::default()
                },
            }]
        );
    }

    #[test]
    fn parses_add_code_statement_for_runtime_jit_parsing() {
        let program = parse_program("Add \"levels/intro.code\" Code").unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::AddCode {
                path: "levels/intro.code".to_owned(),
            }]
        );
    }

    #[test]
    fn parses_multifile_add_code_statement() {
        let program =
            parse_program("add \"game_init.code\",\n    \"/game_input/input\" code").unwrap();

        assert_eq!(
            program.statements,
            vec![
                Statement::AddCode {
                    path: "game_init.code".to_owned(),
                },
                Statement::AddCode {
                    path: "/game_input/input".to_owned(),
                },
            ]
        );
    }

    #[test]
    fn parses_designer_model_declaration_options() {
        let program = parse_program(
            "player1 => dynamic fighter1_native : hidden, pos (40.0,-89.25,30.0), scale 3.0, rotate(0.0,173.0,-0.0)",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::ModelDecl {
                name: "player1".to_owned(),
                resource: "fighter1_native".to_owned(),
                options: EntityOptions {
                    position: Some(SceneMaxVec3 {
                        x: 40.0,
                        y: -89.25,
                        z: 30.0,
                    }),
                    rotation_degrees: Some(SceneMaxVec3 {
                        x: 0.0,
                        y: 173.0,
                        z: -0.0,
                    }),
                    scale: Some(SceneMaxVec3 {
                        x: 3.0,
                        y: 3.0,
                        z: 3.0,
                    }),
                    size: None,
                    hidden: true,
                    collider: false,
                    sprite: false,
                    radius: None,
                    body_kind: Some(SceneMaxBodyKind::Kinematic),
                    collision_shape: None,
                    ..Default::default()
                },
            }]
        );
    }

    #[test]
    fn parses_physics_body_and_collision_shape_hints() {
        let program = parse_program(
            "floor => static box : size (100.0,1.0,100.0), collision shape box\nfx => dynamic fighter : collision shape none",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![
                Statement::ModelDecl {
                    name: "floor".to_owned(),
                    resource: "box".to_owned(),
                    options: EntityOptions {
                        position: None,
                        rotation_degrees: None,
                        scale: None,
                        size: Some(SceneMaxVec3 {
                            x: 100.0,
                            y: 1.0,
                            z: 100.0,
                        }),
                        hidden: false,
                        collider: false,
                        sprite: false,
                        radius: None,
                        body_kind: Some(SceneMaxBodyKind::Static),
                        collision_shape: Some(SceneMaxCollisionShape::Box),
                        ..Default::default()
                    },
                },
                Statement::ModelDecl {
                    name: "fx".to_owned(),
                    resource: "fighter".to_owned(),
                    options: EntityOptions {
                        body_kind: Some(SceneMaxBodyKind::Kinematic),
                        collision_shape: Some(SceneMaxCollisionShape::None),
                        ..Default::default()
                    },
                },
            ]
        );
    }

    #[test]
    fn parses_collider_declaration_and_attach_statement() {
        let program = parse_program(
            "player1_head_collider => collider sphere : pos (2.5,0.5,0.0), radius 0.5\nplayer1_head_collider.attach to player1.\"mixamorig:Head\": pos (0,0.5,0)",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![
                Statement::ModelDecl {
                    name: "player1_head_collider".to_owned(),
                    resource: "sphere".to_owned(),
                    options: EntityOptions {
                        position: Some(SceneMaxVec3 {
                            x: 2.5,
                            y: 0.5,
                            z: 0.0,
                        }),
                        radius: Some(0.5),
                        collider: true,
                        collision_shape: Some(SceneMaxCollisionShape::Sphere),
                        ..Default::default()
                    },
                },
                Statement::Attach(AttachStatement {
                    target: "player1_head_collider".to_owned(),
                    subject: "player1.\"mixamorig:Head\"".to_owned(),
                    offset: SceneMaxVec3 {
                        x: 0.0,
                        y: 0.5,
                        z: 0.0,
                    },
                }),
            ]
        );
    }

    #[test]
    fn parses_switch_and_camera_commands() {
        let program = parse_program(
            "switch to \"game_intro\"\ncamera.pos(0.0,2.0,10.0)\ncamera.rotate(0,180,0)",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![
                Statement::SwitchTo {
                    scene: "game_intro".to_owned(),
                },
                Statement::CameraPosition(SceneMaxVec3 {
                    x: 0.0,
                    y: 2.0,
                    z: 10.0,
                }),
                Statement::CameraRotation(SceneMaxVec3 {
                    x: 0.0,
                    y: 180.0,
                    z: 0.0,
                }),
            ]
        );
    }

    #[test]
    fn parses_camera_relative_move() {
        let program = parse_program("camera.move (z+5) in 5 seconds").unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::CameraMove(CameraMoveStatement {
                axis: SceneMaxAxis::Z,
                distance: 5.0,
                distance_value: AssignmentValue::Number(5.0),
                duration_seconds: 5.0,
                duration_value: AssignmentValue::Number(5.0),
                async_run: false,
            })]
        );
    }

    #[test]
    fn parses_camera_relative_move_async_negative_axis_delta() {
        let program = parse_program("camera.move (x-2.5) for tm seconds async").unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::CameraMove(CameraMoveStatement {
                axis: SceneMaxAxis::X,
                distance: -2.5,
                distance_value: AssignmentValue::Number(-2.5),
                duration_seconds: 0.0,
                duration_value: AssignmentValue::Symbol("tm".to_owned()),
                async_run: true,
            })]
        );
    }

    #[test]
    fn parses_wait_for_key() {
        let program = parse_program("wait for key space to be pressed").unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::WaitForKey {
                key: "space".to_owned(),
            }]
        );
    }

    #[test]
    fn parses_visibility_look_at_turn_and_size() {
        let program = parse_program(
            "floor => static box : size (100.0,1.0,100.0), pos (0.0,-15.0,0.0)\nplayer1.show\nplayer1.look at (player2)\nring.turn left 360 in 50 seconds async",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![
                Statement::ModelDecl {
                    name: "floor".to_owned(),
                    resource: "box".to_owned(),
                    options: EntityOptions {
                        position: Some(SceneMaxVec3 {
                            x: 0.0,
                            y: -15.0,
                            z: 0.0,
                        }),
                        rotation_degrees: None,
                        scale: None,
                        size: Some(SceneMaxVec3 {
                            x: 100.0,
                            y: 1.0,
                            z: 100.0,
                        }),
                        hidden: false,
                        collider: false,
                        sprite: false,
                        radius: None,
                        body_kind: Some(SceneMaxBodyKind::Static),
                        collision_shape: None,
                        ..Default::default()
                    },
                },
                Statement::Visibility {
                    target: "player1".to_owned(),
                    visible: true,
                },
                Statement::LookAt {
                    target: "player1".to_owned(),
                    subject: "player2".to_owned(),
                },
                Statement::Turn(TurnStatement {
                    target: "ring".to_owned(),
                    degrees: 360.0,
                    degrees_value: AssignmentValue::Number(360.0),
                    duration_seconds: 50.0,
                    duration_value: AssignmentValue::Number(50.0),
                    loop_condition: None,
                    async_run: true,
                }),
            ]
        );
    }

    #[test]
    fn parses_loop_while_turn_condition() {
        let program = parse_program("axe.turn 360 in 1 second loop while 1==1").unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::Turn(TurnStatement {
                target: "axe".to_owned(),
                degrees: 360.0,
                degrees_value: AssignmentValue::Number(360.0),
                duration_seconds: 1.0,
                duration_value: AssignmentValue::Number(1.0),
                loop_condition: Some(Condition::EqualsValue {
                    left: AssignmentValue::Number(1.0),
                    right: AssignmentValue::Number(1.0),
                }),
                async_run: false,
            })]
        );
    }

    #[test]
    fn parses_java_projector_primitives_and_quad_size() {
        let program = parse_program(
            "q => quad : size (4,3)\n\
             cyl => cylinder : radius (0.5,1), height 2.5\n\
             hc => hollow cylinder : radius (1.2,1), inner radius (0.4,0.3), height 2\n\
             w => wedge : size (2,3,4)\n\
             co => cone : radius (0,1), height 2\n\
             st => stairs : size (2,0.25,0.4), steps 7\n\
             ar => arch : size (3,4,0.5), thickness 0.4, segments 10",
        )
        .unwrap();

        assert_eq!(program.statements.len(), 7);
        assert!(matches!(
            &program.statements[0],
            Statement::ModelDecl { resource, options, .. }
                if resource == "quad"
                    && options.size == Some(SceneMaxVec3 { x: 4.0, y: 3.0, z: 1.0 })
        ));
        assert!(matches!(
            &program.statements[1],
            Statement::ModelDecl { resource, options, .. }
                if resource == "cylinder"
                    && options.radius_top == Some(0.5)
                    && options.radius_bottom == Some(1.0)
                    && options.height == Some(2.5)
        ));
        assert!(matches!(
            &program.statements[2],
            Statement::ModelDecl { resource, options, .. }
                if resource == "hollow cylinder"
                    && options.radius_top == Some(1.2)
                    && options.radius_bottom == Some(1.0)
                    && options.inner_radius_top == Some(0.4)
                    && options.inner_radius_bottom == Some(0.3)
                    && options.height == Some(2.0)
        ));
        assert!(matches!(
            &program.statements[3],
            Statement::ModelDecl { resource, options, .. }
                if resource == "wedge"
                    && options.size == Some(SceneMaxVec3 { x: 2.0, y: 3.0, z: 4.0 })
        ));
        assert!(matches!(
            &program.statements[4],
            Statement::ModelDecl { resource, options, .. }
                if resource == "cone"
                    && options.radius_top == Some(0.0)
                    && options.radius_bottom == Some(1.0)
                    && options.height == Some(2.0)
        ));
        assert!(matches!(
            &program.statements[5],
            Statement::ModelDecl { resource, options, .. }
                if resource == "stairs" && options.steps == Some(7)
        ));
        assert!(matches!(
            &program.statements[6],
            Statement::ModelDecl { resource, options, .. }
                if resource == "arch"
                    && options.thickness == Some(0.4)
                    && options.segments == Some(10)
        ));
    }

    #[test]
    fn parses_primitive_material_option() {
        let program = parse_program("quad_1 => quad : size (1,1), material \"wall\"").unwrap();

        assert!(matches!(
            &program.statements[0],
            Statement::ModelDecl { options, .. }
                if options.material.as_deref() == Some("wall")
        ));
    }

    #[test]
    fn parses_expression_position_on_model_declaration() {
        let program =
            parse_program("box_test => box : pos (rnd(20)-10, rnd(4)+1, rnd(20)-10)").unwrap();

        assert!(matches!(
            &program.statements[0],
            Statement::ModelDecl { options, .. }
                if options.position.is_none()
                    && matches!(
                        options.position_value,
                        Some(PositionValue::Coordinates(ref values)) if values.len() == 3
                    )
        ));
    }

    #[test]
    fn parses_move_statement() {
        let program = parse_program("player1.move forward 0.5 for 0.25 seconds async").unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::Move(MoveStatement {
                target: "player1".to_owned(),
                direction: MoveDirection::Forward,
                distance: 0.5,
                distance_value: AssignmentValue::Number(0.5),
                duration_seconds: 0.25,
                duration_value: AssignmentValue::Number(0.25),
                loop_condition: None,
                async_run: true,
            })]
        );
    }

    #[test]
    fn parses_classic_directional_move_statement() {
        let program = parse_program("g.move up 4 in 3 Seconds").unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::Move(MoveStatement {
                target: "g".to_owned(),
                direction: MoveDirection::Up,
                distance: 4.0,
                distance_value: AssignmentValue::Number(4.0),
                duration_seconds: 3.0,
                duration_value: AssignmentValue::Number(3.0),
                loop_condition: None,
                async_run: false,
            })]
        );
    }

    #[test]
    fn parses_classic_directional_move_runtime_expression() {
        let program =
            parse_program("\ng => gemini\ng.move left 2+rnd(5)*1.3 in 10 Seconds\n").unwrap();

        assert_eq!(
            program.statements,
            vec![
                Statement::ModelDecl {
                    name: "g".to_owned(),
                    resource: "gemini".to_owned(),
                    options: EntityOptions::default(),
                },
                Statement::Move(MoveStatement {
                    target: "g".to_owned(),
                    direction: MoveDirection::Left,
                    distance: 0.0,
                    distance_value: AssignmentValue::Binary {
                        left: Box::new(AssignmentValue::Number(2.0)),
                        operator: ArithmeticOperator::Add,
                        right: Box::new(AssignmentValue::Binary {
                            left: Box::new(AssignmentValue::RandomInt {
                                max: Box::new(AssignmentValue::Number(5.0)),
                            }),
                            operator: ArithmeticOperator::Multiply,
                            right: Box::new(AssignmentValue::Number(1.3)),
                        }),
                    },
                    duration_seconds: 10.0,
                    duration_value: AssignmentValue::Number(10.0),
                    loop_condition: None,
                    async_run: false,
                })
            ]
        );
    }

    #[test]
    fn parses_runtime_expressions_for_action_scalars() {
        let program = parse_program(
            r#"
player.walk at speed of base+rnd(2) loop
player.turn right angle+15 in turn_time+0.5 seconds
player.move to (enemy forward dist+2) in move_time seconds async
player.character.jump at speed of jump_speed+5 async
player.physics impulse up force*2
player.animation speed speed_scale+0.1 for speed_time seconds
sprites.play frame start_frame+1 to end_frame-1 in sprite_time/2 seconds loop
run tick(score+10) every tick_time+0.25 seconds
"#,
        )
        .unwrap();

        let mut saw_animation = false;
        let mut saw_turn = false;
        let mut saw_move_to = false;
        let mut saw_jump = false;
        let mut saw_impulse = false;
        let mut saw_animation_speed = false;
        let mut saw_sprite = false;
        let mut saw_recurring = false;

        for statement in &program.statements {
            match statement {
                Statement::Animate(animation) => {
                    saw_animation = matches!(animation.speed_value, AssignmentValue::Binary { .. });
                }
                Statement::Turn(turn) => {
                    saw_turn = matches!(turn.degrees_value, AssignmentValue::Binary { .. })
                        && matches!(turn.duration_value, AssignmentValue::Binary { .. });
                }
                Statement::MoveTo(move_to) => {
                    let forward_is_expression = matches!(
                        &move_to.destination,
                        MoveToDestination::EntityForward {
                            distance_value: AssignmentValue::Binary { .. },
                            ..
                        }
                    );
                    saw_move_to = forward_is_expression
                        && matches!(move_to.duration_value, AssignmentValue::Symbol(_));
                }
                Statement::CharacterJump(jump) => {
                    saw_jump = matches!(jump.speed_value, AssignmentValue::Binary { .. });
                }
                Statement::PhysicsImpulse(impulse) => {
                    saw_impulse = matches!(impulse.strength_value, AssignmentValue::Binary { .. });
                }
                Statement::AnimationSpeed(animation_speed) => {
                    saw_animation_speed =
                        matches!(animation_speed.speed_value, AssignmentValue::Binary { .. })
                            && matches!(
                                animation_speed.duration_value,
                                Some(AssignmentValue::Symbol(_))
                            );
                }
                Statement::SpritePlay(sprite_play) => {
                    saw_sprite =
                        matches!(sprite_play.from_frame_value, AssignmentValue::Binary { .. })
                            && matches!(sprite_play.to_frame_value, AssignmentValue::Binary { .. })
                            && matches!(sprite_play.duration_value, AssignmentValue::Binary { .. });
                }
                Statement::RunEvery { interval_value, .. } => {
                    saw_recurring = matches!(interval_value, AssignmentValue::Binary { .. });
                }
                _ => {}
            }
        }

        assert!(saw_animation);
        assert!(saw_turn);
        assert!(saw_move_to);
        assert!(saw_jump);
        assert!(saw_impulse);
        assert!(saw_animation_speed);
        assert!(saw_sprite);
        assert!(saw_recurring);
    }

    #[test]
    fn parses_top_level_classic_repeat_move_block() {
        let program = parse_program(
            "g=>gemini\n\ndo 3 Times\n  g.move left 4 in 3 Seconds\n  g.move right 4 in 2 seconds \n  g.move up 2 in 1 Seconds\n  g.move down 3 in 2 seconds \nend do ",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![
                Statement::ModelDecl {
                    name: "g".to_owned(),
                    resource: "gemini".to_owned(),
                    options: EntityOptions::default(),
                },
                Statement::Repeat {
                    times: 3,
                    actions: vec![
                        Statement::Move(MoveStatement {
                            target: "g".to_owned(),
                            direction: MoveDirection::Left,
                            distance: 4.0,
                            distance_value: AssignmentValue::Number(4.0),
                            duration_seconds: 3.0,
                            duration_value: AssignmentValue::Number(3.0),
                            loop_condition: None,
                            async_run: false,
                        }),
                        Statement::Move(MoveStatement {
                            target: "g".to_owned(),
                            direction: MoveDirection::Right,
                            distance: 4.0,
                            distance_value: AssignmentValue::Number(4.0),
                            duration_seconds: 2.0,
                            duration_value: AssignmentValue::Number(2.0),
                            loop_condition: None,
                            async_run: false,
                        }),
                        Statement::Move(MoveStatement {
                            target: "g".to_owned(),
                            direction: MoveDirection::Up,
                            distance: 2.0,
                            distance_value: AssignmentValue::Number(2.0),
                            duration_seconds: 1.0,
                            duration_value: AssignmentValue::Number(1.0),
                            loop_condition: None,
                            async_run: false,
                        }),
                        Statement::Move(MoveStatement {
                            target: "g".to_owned(),
                            direction: MoveDirection::Down,
                            distance: 3.0,
                            distance_value: AssignmentValue::Number(3.0),
                            duration_seconds: 2.0,
                            duration_value: AssignmentValue::Number(2.0),
                            loop_condition: None,
                            async_run: false,
                        }),
                    ],
                },
            ]
        );
    }

    #[test]
    fn parses_loop_while_move_condition() {
        let program =
            parse_program("player1.move forward 0.2 for 0.5 seconds loop while move_forward==1")
                .unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::Move(MoveStatement {
                target: "player1".to_owned(),
                direction: MoveDirection::Forward,
                distance: 0.2,
                distance_value: AssignmentValue::Number(0.2),
                duration_seconds: 0.5,
                duration_value: AssignmentValue::Number(0.5),
                loop_condition: Some(Condition::EqualsNumber {
                    name: "move_forward".to_owned(),
                    value: 1.0,
                }),
                async_run: false,
            })]
        );
    }

    #[test]
    fn parses_move_to_coordinate_statement() {
        let program = parse_program("rock.move to (18.5,-48.0,99.0) in 0.36 seconds").unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::MoveTo(MoveToStatement {
                target: "rock".to_owned(),
                destination: MoveToDestination::Position(PositionValue::Coordinates(vec![
                    PositionExpr::Number(18.5),
                    PositionExpr::Number(-48.0),
                    PositionExpr::Number(99.0),
                ])),
                duration_seconds: 0.36,
                duration_value: AssignmentValue::Number(0.36),
                async_run: false,
            })]
        );
    }

    #[test]
    fn parses_move_to_entity_forward_statement() {
        let program =
            parse_program("player2.move to (player1 forward 4) in 0.5 second async").unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::MoveTo(MoveToStatement {
                target: "player2".to_owned(),
                destination: MoveToDestination::EntityForward {
                    entity: "player1".to_owned(),
                    distance: 4.0,
                    distance_value: AssignmentValue::Number(4.0),
                },
                duration_seconds: 0.5,
                duration_value: AssignmentValue::Number(0.5),
                async_run: true,
            })]
        );
    }

    #[test]
    fn parses_physics_impulse_stop_and_throw() {
        let program = parse_program(
            "r.physics impulse up 100\nr.physics stop\nr.throw at player1 power dist*30/147",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![
                Statement::PhysicsImpulse(PhysicsImpulseStatement {
                    target: "r".to_owned(),
                    direction: PhysicsDirection::Up,
                    strength: 100.0,
                    strength_value: AssignmentValue::Number(100.0),
                }),
                Statement::PhysicsStop {
                    target: "r".to_owned(),
                },
                Statement::PhysicsThrowAt(PhysicsThrowAtStatement {
                    target: "r".to_owned(),
                    subject: "player1".to_owned(),
                    power: AssignmentValue::Binary {
                        left: Box::new(AssignmentValue::Binary {
                            left: Box::new(AssignmentValue::Symbol("dist".to_owned())),
                            operator: ArithmeticOperator::Multiply,
                            right: Box::new(AssignmentValue::Number(30.0)),
                        }),
                        operator: ArithmeticOperator::Divide,
                        right: Box::new(AssignmentValue::Number(147.0)),
                    },
                }),
            ]
        );
    }

    #[test]
    fn parses_object_pool_lifecycle_commands() {
        let program = parse_program(
            "rocks => Object.Pool(create_rock, size 5)\nvar rock = rocks.acquire\nrocks.release rock\nrock.delete",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![
                Statement::ObjectPool(ObjectPoolStatement {
                    name: "rocks".to_owned(),
                    factory: "create_rock".to_owned(),
                    size: 5,
                }),
                Statement::Assignment(AssignmentStatement {
                    name: "rock".to_owned(),
                    value: AssignmentValue::PoolAcquire {
                        pool: "rocks".to_owned(),
                    },
                }),
                Statement::PoolRelease(PoolReleaseStatement {
                    pool: "rocks".to_owned(),
                    target: "rock".to_owned(),
                }),
                Statement::Delete {
                    target: "rock".to_owned(),
                },
            ]
        );

        let free_program =
            parse_program("rocks => Object.Pool(create_rock, 2)\nrocks.free rock").unwrap();
        assert!(matches!(
            &free_program.statements[1],
            Statement::PoolRelease(PoolReleaseStatement { pool, target })
                if pool == "rocks" && target == "rock"
        ));
    }

    #[test]
    fn parses_relative_y_rotate_command_as_turn() {
        let program = parse_program("player2.rotate(y+360) in 0.5 second").unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::Turn(TurnStatement {
                target: "player2".to_owned(),
                degrees: 360.0,
                degrees_value: AssignmentValue::Number(360.0),
                duration_seconds: 0.5,
                duration_value: AssignmentValue::Number(0.5),
                loop_condition: None,
                async_run: false,
            })]
        );
    }

    #[test]
    fn parses_position_commands() {
        let program = parse_program(
            "player1.pos (39.4,-87,29)\nthrow_text.pos (player1.x, player1.y+3, player1.z)\nplayer2_hit.pos(player2.\"mixamorig:Head\")",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![
                Statement::Position(PositionStatement {
                    target: "player1".to_owned(),
                    position: PositionValue::Coordinates(vec![
                        PositionExpr::Number(39.4),
                        PositionExpr::Number(-87.0),
                        PositionExpr::Number(29.0),
                    ]),
                }),
                Statement::Position(PositionStatement {
                    target: "throw_text".to_owned(),
                    position: PositionValue::Coordinates(vec![
                        PositionExpr::EntityAxis {
                            entity: "player1".to_owned(),
                            axis: SceneMaxAxis::X,
                            offset: 0.0,
                        },
                        PositionExpr::EntityAxis {
                            entity: "player1".to_owned(),
                            axis: SceneMaxAxis::Y,
                            offset: 3.0,
                        },
                        PositionExpr::EntityAxis {
                            entity: "player1".to_owned(),
                            axis: SceneMaxAxis::Z,
                            offset: 0.0,
                        },
                    ]),
                }),
                Statement::Position(PositionStatement {
                    target: "player2_hit".to_owned(),
                    position: PositionValue::Entity("player2.\"mixamorig:Head\"".to_owned()),
                }),
            ]
        );
    }

    #[test]
    fn parses_move_to_quoted_bone_subject() {
        let program = parse_program("spark.move to (player1.\"mixamorig:Head\")").unwrap();

        assert!(matches!(
            &program.statements[0],
            Statement::MoveTo(MoveToStatement {
                target,
                destination: MoveToDestination::Position(PositionValue::Entity(subject)),
                ..
            }) if target == "spark" && subject == "player1.\"mixamorig:Head\""
        ));
    }

    #[test]
    fn parses_position_with_nested_runtime_expressions() {
        let program = parse_program("rock.pos(0,-2+index*0.5,0-rnd(10))").unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::Position(PositionStatement {
                target: "rock".to_owned(),
                position: PositionValue::Coordinates(vec![
                    PositionExpr::Number(0.0),
                    PositionExpr::Value(AssignmentValue::Binary {
                        left: Box::new(AssignmentValue::Number(-2.0)),
                        operator: ArithmeticOperator::Add,
                        right: Box::new(AssignmentValue::Binary {
                            left: Box::new(AssignmentValue::Symbol("index".to_owned())),
                            operator: ArithmeticOperator::Multiply,
                            right: Box::new(AssignmentValue::Number(0.5)),
                        }),
                    }),
                    PositionExpr::Value(AssignmentValue::Binary {
                        left: Box::new(AssignmentValue::Number(0.0)),
                        operator: ArithmeticOperator::Subtract,
                        right: Box::new(AssignmentValue::RandomInt {
                            max: Box::new(AssignmentValue::Number(10.0)),
                        }),
                    }),
                ]),
            })]
        );
    }

    #[test]
    fn parses_key_event_actions_for_runtime_execution() {
        let program = parse_program(
            "[@allow_move]\nwhen key A is pressed once do async\n  player1.look at (player2)\n  player1.move forward 0.3 for 0.2 seconds async\n  player1.mma_kick1 at speed of 2.5 async\nend do",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::KeyEvent(KeyEventStatement {
                key: "a".to_owned(),
                trigger: KeyTrigger::PressedOnce,
                guard: Some(Condition::Alias("allow_move".to_owned())),
                actions: vec![
                    Statement::LookAt {
                        target: "player1".to_owned(),
                        subject: "player2".to_owned(),
                    },
                    Statement::Move(MoveStatement {
                        target: "player1".to_owned(),
                        direction: MoveDirection::Forward,
                        distance: 0.3,
                        distance_value: AssignmentValue::Number(0.3),
                        duration_seconds: 0.2,
                        duration_value: AssignmentValue::Number(0.2),
                        loop_condition: None,
                        async_run: true,
                    }),
                    Statement::Animate(AnimationStatement {
                        target: "player1".to_owned(),
                        clip: "mma_kick1".to_owned(),
                        speed: 2.5,
                        speed_value: AssignmentValue::Number(2.5),
                        looped: false,
                        blocking: false,
                    }),
                ],
            })]
        );
    }

    #[test]
    fn parses_key_event_if_else_without_flattening_branches() {
        let program = parse_program(
            "when key X is pressed once do\n  player1.pull_start at speed of 4\n  if (player2.data.trapped == 1) {\n    if(rnd(3)==0) {\n      player1.turn left 360 in 0.5 seconds async\n    }\n    player1.kip_up\n  } else {\n    player1.idle2 loop\n  }\nend do",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::KeyEvent(KeyEventStatement {
                key: "x".to_owned(),
                trigger: KeyTrigger::PressedOnce,
                guard: None,
                actions: vec![
                    Statement::Animate(AnimationStatement {
                        target: "player1".to_owned(),
                        clip: "pull_start".to_owned(),
                        speed: 4.0,
                        speed_value: AssignmentValue::Number(4.0),
                        looped: false,
                        blocking: true,
                    }),
                    Statement::If(IfStatement {
                        condition: Condition::EqualsNumber {
                            name: "player2.data.trapped".to_owned(),
                            value: 1.0,
                        },
                        actions: vec![
                            Statement::If(IfStatement {
                                condition: Condition::EqualsValue {
                                    left: AssignmentValue::RandomInt {
                                        max: Box::new(AssignmentValue::Number(3.0)),
                                    },
                                    right: AssignmentValue::Number(0.0),
                                },
                                actions: vec![Statement::Turn(TurnStatement {
                                    target: "player1".to_owned(),
                                    degrees: 360.0,
                                    degrees_value: AssignmentValue::Number(360.0),
                                    duration_seconds: 0.5,
                                    duration_value: AssignmentValue::Number(0.5),
                                    loop_condition: None,
                                    async_run: true,
                                })],
                                else_actions: Vec::new(),
                            }),
                            Statement::Animate(AnimationStatement {
                                target: "player1".to_owned(),
                                clip: "kip_up".to_owned(),
                                speed: 1.0,
                                speed_value: AssignmentValue::Number(1.0),
                                looped: false,
                                blocking: true,
                            }),
                        ],
                        else_actions: vec![Statement::Animate(AnimationStatement {
                            target: "player1".to_owned(),
                            clip: "idle2".to_owned(),
                            speed: 1.0,
                            speed_value: AssignmentValue::Number(1.0),
                            looped: true,
                            blocking: false,
                        })],
                    }),
                ],
            })]
        );
    }

    #[test]
    fn parses_else_if_chain_as_nested_else_branch() {
        let program = parse_program(
            "ai = {\n  if (close_choice == 0) {\n    player2.CrossPunch\n  } else if (close_choice == 1) {\n    player2.HighKick\n  } else if (close_choice == 2) {\n    player2.ButterflyKick\n  } else {\n    player2.Idle loop\n  }\n}",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::FunctionDef(FunctionDefStatement {
                name: "ai".to_owned(),
                params: Vec::new(),
                guard: None,
                actions: vec![Statement::If(IfStatement {
                    condition: Condition::EqualsNumber {
                        name: "close_choice".to_owned(),
                        value: 0.0,
                    },
                    actions: vec![Statement::Animate(AnimationStatement {
                        target: "player2".to_owned(),
                        clip: "CrossPunch".to_owned(),
                        speed: 1.0,
                        speed_value: AssignmentValue::Number(1.0),
                        looped: false,
                        blocking: true,
                    })],
                    else_actions: vec![Statement::If(IfStatement {
                        condition: Condition::EqualsNumber {
                            name: "close_choice".to_owned(),
                            value: 1.0,
                        },
                        actions: vec![Statement::Animate(AnimationStatement {
                            target: "player2".to_owned(),
                            clip: "HighKick".to_owned(),
                            speed: 1.0,
                            speed_value: AssignmentValue::Number(1.0),
                            looped: false,
                            blocking: true,
                        })],
                        else_actions: vec![Statement::If(IfStatement {
                            condition: Condition::EqualsNumber {
                                name: "close_choice".to_owned(),
                                value: 2.0,
                            },
                            actions: vec![Statement::Animate(AnimationStatement {
                                target: "player2".to_owned(),
                                clip: "ButterflyKick".to_owned(),
                                speed: 1.0,
                                speed_value: AssignmentValue::Number(1.0),
                                looped: false,
                                blocking: true,
                            })],
                            else_actions: vec![Statement::Animate(AnimationStatement {
                                target: "player2".to_owned(),
                                clip: "Idle".to_owned(),
                                speed: 1.0,
                                speed_value: AssignmentValue::Number(1.0),
                                looped: true,
                                blocking: false,
                            })],
                        })],
                    })],
                })],
            })]
        );
    }

    #[test]
    fn parses_numeric_and_symbolic_assignments() {
        let program = parse_program("move_forward=1\naction=PLAYER_ACTION_IDLE").unwrap();

        assert_eq!(
            program.statements,
            vec![
                Statement::Assignment(AssignmentStatement {
                    name: "move_forward".to_owned(),
                    value: AssignmentValue::Number(1.0),
                }),
                Statement::Assignment(AssignmentStatement {
                    name: "action".to_owned(),
                    value: AssignmentValue::Symbol("PLAYER_ACTION_IDLE".to_owned()),
                }),
            ]
        );
    }

    #[test]
    fn parses_comma_separated_constants() {
        let program = parse_program("var PLAYER_ACTION_X_1 = 7, PLAYER_ACTION_X_2 = 8,").unwrap();

        assert_eq!(
            program.statements,
            vec![
                Statement::Assignment(AssignmentStatement {
                    name: "PLAYER_ACTION_X_1".to_owned(),
                    value: AssignmentValue::Number(7.0),
                }),
                Statement::Assignment(AssignmentStatement {
                    name: "PLAYER_ACTION_X_2".to_owned(),
                    value: AssignmentValue::Number(8.0),
                }),
            ]
        );
    }

    #[test]
    fn parses_multiline_comma_separated_constants() {
        let program = parse_program(
            "var PLAYER_ACTION_IDLE = 0,\n    PLAYER_ACTION_D = 1,\n    PLAYER_ACTION_X_1 = 7, PLAYER_ACTION_X_2 = 8,\n    PLAYER_ACTION_C = 9\n\nvar GAME_STATE_BEFORE_START = 0,\n    GAME_STATE_START = 1,\n    GAME_STATE_OVER = 2",
        )
        .unwrap();

        assert!(
            program
                .statements
                .contains(&Statement::Assignment(AssignmentStatement {
                    name: "PLAYER_ACTION_X_2".to_owned(),
                    value: AssignmentValue::Number(8.0),
                },))
        );
        assert!(
            program
                .statements
                .contains(&Statement::Assignment(AssignmentStatement {
                    name: "GAME_STATE_START".to_owned(),
                    value: AssignmentValue::Number(1.0),
                },))
        );
        assert!(
            program
                .statements
                .contains(&Statement::Assignment(AssignmentStatement {
                    name: "GAME_STATE_OVER".to_owned(),
                    value: AssignmentValue::Number(2.0),
                },))
        );
    }

    #[test]
    fn parses_shared_var_declarations_as_shared_assignments() {
        let program = parse_program("shared var timer=0 [0..],\n    life1=100 [0..100]").unwrap();

        assert!(
            program
                .statements
                .contains(&Statement::SharedAssignment(AssignmentStatement {
                    name: "timer".to_owned(),
                    value: AssignmentValue::Number(0.0),
                },))
        );
        assert!(
            program
                .statements
                .contains(&Statement::SharedAssignment(AssignmentStatement {
                    name: "life1".to_owned(),
                    value: AssignmentValue::Number(100.0),
                },))
        );
    }

    #[test]
    fn parses_var_prefix_with_original_whitespace_and_casing() {
        let program = parse_program(
            "VAR PLAYER_ACTION_IDLE = 0,\n    PLAYER_ACTION_X_1 = 7,\n    PLAYER_ACTION_X_2 = 8\nSHARED   VAR @enemy_ai_allowed = enemy_ko==0 && op_hit==0",
        )
        .unwrap();

        assert!(
            program
                .statements
                .contains(&Statement::Assignment(AssignmentStatement {
                    name: "PLAYER_ACTION_X_2".to_owned(),
                    value: AssignmentValue::Number(8.0),
                },))
        );
        assert!(program.statements.contains(&Statement::GuardDef {
            name: "enemy_ai_allowed".to_owned(),
            condition: Condition::And(vec![
                Condition::EqualsNumber {
                    name: "enemy_ko".to_owned(),
                    value: 0.0,
                },
                Condition::EqualsNumber {
                    name: "op_hit".to_owned(),
                    value: 0.0,
                },
            ]),
        }));
    }

    #[test]
    fn parses_multiline_guard_definition() {
        let program = parse_program(
            "var @allow_move = player1.data.is_jumping==0\n  && slow_motion==0\n  && game_status!=GAME_STATE_OVER",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::GuardDef {
                name: "allow_move".to_owned(),
                condition: Condition::And(vec![
                    Condition::EqualsNumber {
                        name: "player1.data.is_jumping".to_owned(),
                        value: 0.0,
                    },
                    Condition::EqualsNumber {
                        name: "slow_motion".to_owned(),
                        value: 0.0,
                    },
                    Condition::NotEqualsSymbol {
                        name: "game_status".to_owned(),
                        value: "GAME_STATE_OVER".to_owned(),
                    },
                ]),
            }]
        );
    }

    #[test]
    fn parses_multiline_guard_definition_after_trailing_operator() {
        let program = parse_program(
            "var @player1_grabs_for_throwing_player2 = @player1_can_hit &&\n  player1.data.hand_attack_hit == 1 && action == PLAYER_ACTION_X_1",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::GuardDef {
                name: "player1_grabs_for_throwing_player2".to_owned(),
                condition: Condition::And(vec![
                    Condition::Alias("player1_can_hit".to_owned()),
                    Condition::EqualsNumber {
                        name: "player1.data.hand_attack_hit".to_owned(),
                        value: 1.0,
                    },
                    Condition::EqualsSymbol {
                        name: "action".to_owned(),
                        value: "PLAYER_ACTION_X_1".to_owned(),
                    },
                ]),
            }]
        );
    }

    #[test]
    fn parses_guarded_action_block() {
        let program = parse_program(
            "when key D is pressed once do\n  [player1.data.is_jumping==0]\n  do\n    player1.flying_kick at speed of 2\n  end do\nend do",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::KeyEvent(KeyEventStatement {
                key: "d".to_owned(),
                trigger: KeyTrigger::PressedOnce,
                guard: None,
                actions: vec![Statement::Guarded {
                    condition: Condition::EqualsNumber {
                        name: "player1.data.is_jumping".to_owned(),
                        value: 0.0,
                    },
                    actions: vec![Statement::Animate(AnimationStatement {
                        target: "player1".to_owned(),
                        clip: "flying_kick".to_owned(),
                        speed: 2.0,
                        speed_value: AssignmentValue::Number(2.0),
                        looped: false,
                        blocking: true,
                    })],
                }],
            })]
        );
    }

    #[test]
    fn parses_or_and_parenthesized_key_guard() {
        let program = parse_program(
            "[player1_ko==0 && (game_status!=GAME_STATE_OVER || action == PLAYER_ACTION_X_2)]\nwhen key X is pressed once do\n  player1.pull_start\nend do",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::KeyEvent(KeyEventStatement {
                key: "x".to_owned(),
                trigger: KeyTrigger::PressedOnce,
                guard: Some(Condition::And(vec![
                    Condition::EqualsNumber {
                        name: "player1_ko".to_owned(),
                        value: 0.0,
                    },
                    Condition::Or(vec![
                        Condition::NotEqualsSymbol {
                            name: "game_status".to_owned(),
                            value: "GAME_STATE_OVER".to_owned(),
                        },
                        Condition::EqualsSymbol {
                            name: "action".to_owned(),
                            value: "PLAYER_ACTION_X_2".to_owned(),
                        },
                    ]),
                ])),
                actions: vec![Statement::Animate(AnimationStatement {
                    target: "player1".to_owned(),
                    clip: "pull_start".to_owned(),
                    speed: 1.0,
                    speed_value: AssignmentValue::Number(1.0),
                    looped: false,
                    blocking: true,
                })],
            })]
        );
    }

    #[test]
    fn parses_wait_and_async_actions() {
        let program = parse_program(
            "when key Z is pressed once do\n  do async\n    wait 0.3 seconds\n    action=PLAYER_ACTION_IDLE\n  end do\n  player1.duck_right1 async\n  wait 1 second\n  player1.idle2 loop\nend do",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::KeyEvent(KeyEventStatement {
                key: "z".to_owned(),
                trigger: KeyTrigger::PressedOnce,
                guard: None,
                actions: vec![
                    Statement::Async {
                        actions: vec![
                            Statement::Wait { seconds: 0.3 },
                            Statement::Assignment(AssignmentStatement {
                                name: "action".to_owned(),
                                value: AssignmentValue::Symbol("PLAYER_ACTION_IDLE".to_owned()),
                            }),
                        ],
                    },
                    Statement::Animate(AnimationStatement {
                        target: "player1".to_owned(),
                        clip: "duck_right1".to_owned(),
                        speed: 1.0,
                        speed_value: AssignmentValue::Number(1.0),
                        looped: false,
                        blocking: false,
                    }),
                    Statement::Wait { seconds: 1.0 },
                    Statement::Animate(AnimationStatement {
                        target: "player1".to_owned(),
                        clip: "idle2".to_owned(),
                        speed: 1.0,
                        speed_value: AssignmentValue::Number(1.0),
                        looped: true,
                        blocking: false,
                    }),
                ],
            })]
        );
    }

    #[test]
    fn parses_repeat_block_with_waits() {
        let program = parse_program(
            "when key X is pressed once do\n  do 3 times async\n    throw_text.show\n    wait 0.2 seconds\n    throw_text.hide\n    wait 0.2 seconds\n  end do\nend do",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::KeyEvent(KeyEventStatement {
                key: "x".to_owned(),
                trigger: KeyTrigger::PressedOnce,
                guard: None,
                actions: vec![Statement::Async {
                    actions: vec![Statement::Repeat {
                        times: 3,
                        actions: vec![
                            Statement::Visibility {
                                target: "throw_text".to_owned(),
                                visible: true,
                            },
                            Statement::Wait { seconds: 0.2 },
                            Statement::Visibility {
                                target: "throw_text".to_owned(),
                                visible: false,
                            },
                            Statement::Wait { seconds: 0.2 },
                        ],
                    }],
                }],
            })]
        );
    }

    #[test]
    fn parses_async_while_terminator_without_swallowing_following_actions() {
        let program = parse_program(
            "old_fighter_jump = {\n  do async\n    last_player2_y = player2.y\n  while player2.data.is_jumping == 1\n  player2.Idle loop\n}",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::FunctionDef(FunctionDefStatement {
                name: "old_fighter_jump".to_owned(),
                params: Vec::new(),
                guard: None,
                actions: vec![
                    Statement::Async {
                        actions: vec![Statement::DoWhile {
                            condition: Condition::EqualsNumber {
                                name: "player2.data.is_jumping".to_owned(),
                                value: 1.0,
                            },
                            actions: vec![Statement::Assignment(AssignmentStatement {
                                name: "last_player2_y".to_owned(),
                                value: AssignmentValue::Symbol("player2.y".to_owned()),
                            })],
                        }],
                    },
                    Statement::Animate(AnimationStatement {
                        target: "player2".to_owned(),
                        clip: "Idle".to_owned(),
                        speed: 1.0,
                        speed_value: AssignmentValue::Number(1.0),
                        looped: true,
                        blocking: false,
                    }),
                ],
            })]
        );
    }

    #[test]
    fn parses_do_while_with_nested_else_without_swallowing_following_function() {
        let program = parse_program(
            "looping_fx = {\n  active=0\n\n  do\n    wait 0.35 seconds\n    if(game_status==GAME_STATE_START) {\n      if(active==0) {\n        active=1\n      }\n    } else {\n      if(active==1) {\n        active=0\n      }\n    }\n  while train_stage_started==1\n\n  if(active==1) {\n    active=0\n  }\n}\n\nnext_function = {\n  marker=1\n}\n",
        )
        .unwrap();

        let function_names = program
            .statements
            .iter()
            .filter_map(|statement| match statement {
                Statement::FunctionDef(function) => Some(function.name.as_str()),
                _ => None,
            })
            .collect::<Vec<_>>();

        assert_eq!(function_names, vec!["looping_fx", "next_function"]);
    }

    #[test]
    fn parses_ai_expressions_and_return() {
        let program = parse_program(
            "opponent_ai(p1, p2) = {\n  var dist = distance(p1, p2)\n  var dchoice = rnd(2)\n  var is_desperate = (life2 <= 3)\n  if (rnd(3)==0) {\n    return\n  }\n}\n",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::FunctionDef(FunctionDefStatement {
                name: "opponent_ai".to_owned(),
                params: vec!["p1".to_owned(), "p2".to_owned()],
                guard: None,
                actions: vec![
                    Statement::LocalAssignment(AssignmentStatement {
                        name: "dist".to_owned(),
                        value: AssignmentValue::Distance {
                            left: "p1".to_owned(),
                            right: "p2".to_owned(),
                        },
                    }),
                    Statement::LocalAssignment(AssignmentStatement {
                        name: "dchoice".to_owned(),
                        value: AssignmentValue::RandomInt {
                            max: Box::new(AssignmentValue::Number(2.0)),
                        },
                    }),
                    Statement::LocalAssignment(AssignmentStatement {
                        name: "is_desperate".to_owned(),
                        value: AssignmentValue::Condition(Box::new(Condition::Compare {
                            name: "life2".to_owned(),
                            operator: ComparisonOperator::LessOrEqual,
                            value: AssignmentValue::Number(3.0),
                        })),
                    }),
                    Statement::If(IfStatement {
                        condition: Condition::EqualsValue {
                            left: AssignmentValue::RandomInt {
                                max: Box::new(AssignmentValue::Number(3.0)),
                            },
                            right: AssignmentValue::Number(0.0),
                        },
                        actions: vec![Statement::Return],
                        else_actions: Vec::new(),
                    }),
                ],
            })]
        );
    }

    #[test]
    fn parses_spaced_case_insensitive_expression_functions() {
        let program = parse_program(
            "opponent_ai(p1, p2) = {\n  VAR dist = Distance ( p1 , p2 )\n  var dchoice = RND ( 2 )\n  var frame1 = round ( life1*16/INITIAL_PLAYER_STRENGTH )\n  if ( RND ( 6 ) == 0 && dist < 4.5 ) {\n    return\n  }\n}\n",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::FunctionDef(FunctionDefStatement {
                name: "opponent_ai".to_owned(),
                params: vec!["p1".to_owned(), "p2".to_owned()],
                guard: None,
                actions: vec![
                    Statement::LocalAssignment(AssignmentStatement {
                        name: "dist".to_owned(),
                        value: AssignmentValue::Distance {
                            left: "p1".to_owned(),
                            right: "p2".to_owned(),
                        },
                    }),
                    Statement::LocalAssignment(AssignmentStatement {
                        name: "dchoice".to_owned(),
                        value: AssignmentValue::RandomInt {
                            max: Box::new(AssignmentValue::Number(2.0)),
                        },
                    }),
                    Statement::LocalAssignment(AssignmentStatement {
                        name: "frame1".to_owned(),
                        value: AssignmentValue::Round {
                            value: Box::new(AssignmentValue::Binary {
                                left: Box::new(AssignmentValue::Binary {
                                    left: Box::new(AssignmentValue::Symbol("life1".to_owned())),
                                    operator: ArithmeticOperator::Multiply,
                                    right: Box::new(AssignmentValue::Number(16.0)),
                                }),
                                operator: ArithmeticOperator::Divide,
                                right: Box::new(AssignmentValue::Symbol(
                                    "INITIAL_PLAYER_STRENGTH".to_owned(),
                                )),
                            }),
                        },
                    }),
                    Statement::If(IfStatement {
                        condition: Condition::And(vec![
                            Condition::EqualsValue {
                                left: AssignmentValue::RandomInt {
                                    max: Box::new(AssignmentValue::Number(6.0)),
                                },
                                right: AssignmentValue::Number(0.0),
                            },
                            Condition::Compare {
                                name: "dist".to_owned(),
                                operator: ComparisonOperator::Less,
                                value: AssignmentValue::Number(4.5),
                            },
                        ]),
                        actions: vec![Statement::Return],
                        else_actions: Vec::new(),
                    }),
                ],
            })]
        );
    }

    #[test]
    fn parses_java_vm_boolean_not_inequality_and_return_value_forms() {
        let program = parse_program(
            "test_vm = {\n  var flag = true\n  if (!flag || not false || enemy_ko <> 1) {\n    return flag\n  }\n}\n",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::FunctionDef(FunctionDefStatement {
                name: "test_vm".to_owned(),
                params: Vec::new(),
                guard: None,
                actions: vec![
                    Statement::LocalAssignment(AssignmentStatement {
                        name: "flag".to_owned(),
                        value: AssignmentValue::Condition(Box::new(Condition::Boolean(true))),
                    }),
                    Statement::If(IfStatement {
                        condition: Condition::Or(vec![
                            Condition::Not(Box::new(Condition::Truthy {
                                name: "flag".to_owned(),
                            })),
                            Condition::Not(Box::new(Condition::Boolean(false))),
                            Condition::NotEqualsValue {
                                left: AssignmentValue::Symbol("enemy_ko".to_owned()),
                                right: AssignmentValue::Number(1.0),
                            },
                        ]),
                        actions: vec![Statement::ReturnValue {
                            value: AssignmentValue::Symbol("flag".to_owned()),
                        }],
                        else_actions: Vec::new(),
                    }),
                ],
            })]
        );
    }

    #[test]
    fn parses_reference_ai_else_if_choice_tree_and_recurring_args() {
        let program = parse_program(
            "opponent_ai (p1, p2) = {\n  var dist = distance(p1, p2)\n  if (dist < 5.5) {\n    var mid_choice = rnd(3)\n    if (mid_choice == 0 || is_aggressive == 1) {\n      run op_rush_attack(p2)\n    } else if (mid_choice == 1) {\n      op_action = 3\n      p2.FlyKick at speed of 2.9\n    } else {\n      op_action = 2\n      p2.HighKick at speed of 2.3\n    }\n    return\n  }\n}\nrun opponent_ai(player1,player2) every 0.65 seconds",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![
                Statement::FunctionDef(FunctionDefStatement {
                    name: "opponent_ai".to_owned(),
                    params: vec!["p1".to_owned(), "p2".to_owned()],
                    guard: None,
                    actions: vec![
                        Statement::LocalAssignment(AssignmentStatement {
                            name: "dist".to_owned(),
                            value: AssignmentValue::Distance {
                                left: "p1".to_owned(),
                                right: "p2".to_owned(),
                            },
                        }),
                        Statement::If(IfStatement {
                            condition: Condition::Compare {
                                name: "dist".to_owned(),
                                operator: ComparisonOperator::Less,
                                value: AssignmentValue::Number(5.5),
                            },
                            actions: vec![
                                Statement::LocalAssignment(AssignmentStatement {
                                    name: "mid_choice".to_owned(),
                                    value: AssignmentValue::RandomInt {
                                        max: Box::new(AssignmentValue::Number(3.0)),
                                    },
                                }),
                                Statement::If(IfStatement {
                                    condition: Condition::Or(vec![
                                        Condition::EqualsNumber {
                                            name: "mid_choice".to_owned(),
                                            value: 0.0,
                                        },
                                        Condition::EqualsNumber {
                                            name: "is_aggressive".to_owned(),
                                            value: 1.0,
                                        },
                                    ]),
                                    actions: vec![Statement::RunFunction {
                                        name: "op_rush_attack".to_owned(),
                                        args: vec!["p2".to_owned()],
                                    }],
                                    else_actions: vec![Statement::If(IfStatement {
                                        condition: Condition::EqualsNumber {
                                            name: "mid_choice".to_owned(),
                                            value: 1.0,
                                        },
                                        actions: vec![
                                            Statement::Assignment(AssignmentStatement {
                                                name: "op_action".to_owned(),
                                                value: AssignmentValue::Number(3.0),
                                            }),
                                            Statement::Animate(AnimationStatement {
                                                target: "p2".to_owned(),
                                                clip: "FlyKick".to_owned(),
                                                speed: 2.9,
                                                speed_value: AssignmentValue::Number(2.9),
                                                looped: false,
                                                blocking: true,
                                            }),
                                        ],
                                        else_actions: vec![
                                            Statement::Assignment(AssignmentStatement {
                                                name: "op_action".to_owned(),
                                                value: AssignmentValue::Number(2.0),
                                            }),
                                            Statement::Animate(AnimationStatement {
                                                target: "p2".to_owned(),
                                                clip: "HighKick".to_owned(),
                                                speed: 2.3,
                                                speed_value: AssignmentValue::Number(2.3),
                                                looped: false,
                                                blocking: true,
                                            }),
                                        ],
                                    })],
                                }),
                                Statement::Return,
                            ],
                            else_actions: Vec::new(),
                        }),
                    ],
                }),
                Statement::RunEvery {
                    name: "opponent_ai".to_owned(),
                    args: vec!["player1".to_owned(), "player2".to_owned()],
                    interval_seconds: 0.65,
                    interval_value: AssignmentValue::Number(0.65),
                },
            ]
        );
    }

    #[test]
    fn parses_async_run_and_arithmetic_assignment() {
        let program = parse_program("score = score + 10;\nrun player1_head_hit Async").unwrap();

        assert_eq!(
            program.statements,
            vec![
                Statement::Assignment(AssignmentStatement {
                    name: "score".to_owned(),
                    value: AssignmentValue::Binary {
                        left: Box::new(AssignmentValue::Symbol("score".to_owned())),
                        operator: ArithmeticOperator::Add,
                        right: Box::new(AssignmentValue::Number(10.0)),
                    },
                }),
                Statement::Async {
                    actions: vec![Statement::RunFunction {
                        name: "player1_head_hit".to_owned(),
                        args: Vec::new(),
                    }],
                },
            ]
        );
    }

    #[test]
    fn parses_dotted_numeric_assignment_without_treating_it_as_animation() {
        let program = parse_program("player1.data.attack_legs = 1").unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::Assignment(AssignmentStatement {
                name: "player1.data.attack_legs".to_owned(),
                value: AssignmentValue::Number(1.0),
            })]
        );
    }

    #[test]
    fn parses_simple_when_condition_actions() {
        let program = parse_program(
            "when move_forward == 1 do\n  player1.\"run_sword\" loop\n  player1.move forward 0.2 for 0.5 seconds\nend do",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::WhenEvent(WhenEventStatement {
                condition: Condition::EqualsNumber {
                    name: "move_forward".to_owned(),
                    value: 1.0,
                },
                after_condition: None,
                guard: None,
                actions: vec![
                    Statement::Animate(AnimationStatement {
                        target: "player1".to_owned(),
                        clip: "run_sword".to_owned(),
                        speed: 1.0,
                        speed_value: AssignmentValue::Number(1.0),
                        looped: true,
                        blocking: false,
                    }),
                    Statement::Move(MoveStatement {
                        target: "player1".to_owned(),
                        direction: MoveDirection::Forward,
                        distance: 0.2,
                        distance_value: AssignmentValue::Number(0.2),
                        duration_seconds: 0.5,
                        duration_value: AssignmentValue::Number(0.5),
                        loop_condition: None,
                        async_run: false,
                    }),
                ],
            })]
        );
    }

    #[test]
    fn parses_dotted_when_condition() {
        let program = parse_program(
            "when player1.data.attack_legs == 1 do\n  player1.leg_takedown_attacker at speed of 2.5\nend do",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::WhenEvent(WhenEventStatement {
                condition: Condition::EqualsNumber {
                    name: "player1.data.attack_legs".to_owned(),
                    value: 1.0,
                },
                after_condition: None,
                guard: None,
                actions: vec![Statement::Animate(AnimationStatement {
                    target: "player1".to_owned(),
                    clip: "leg_takedown_attacker".to_owned(),
                    speed: 2.5,
                    speed_value: AssignmentValue::Number(2.5),
                    looped: false,
                    blocking: true,
                })],
            })]
        );
    }

    #[test]
    fn parses_collision_when_event() {
        let program = parse_program(
            "[@player2_can_hit]\nwhen player2_left_hand_collider, player2_right_hand_collider collides with player1_head_collider do\n  player2.data.hand_attack_hit = 1\nend do",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::WhenEvent(WhenEventStatement {
                condition: Condition::Collision {
                    sources: vec![
                        "player2_left_hand_collider".to_owned(),
                        "player2_right_hand_collider".to_owned(),
                    ],
                    target: "player1_head_collider".to_owned(),
                },
                after_condition: None,
                guard: Some(Condition::Alias("player2_can_hit".to_owned())),
                actions: vec![Statement::Assignment(AssignmentStatement {
                    name: "player2.data.hand_attack_hit".to_owned(),
                    value: AssignmentValue::Number(1.0),
                })],
            })]
        );
    }

    #[test]
    fn parses_modulo_condition_expression() {
        let program =
            parse_program("when high_kick_counter%3==0 do\n  player1.back_death1\nend do").unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::WhenEvent(WhenEventStatement {
                condition: Condition::EqualsValue {
                    left: AssignmentValue::Binary {
                        left: Box::new(AssignmentValue::Symbol("high_kick_counter".to_owned())),
                        operator: ArithmeticOperator::Modulo,
                        right: Box::new(AssignmentValue::Number(3.0)),
                    },
                    right: AssignmentValue::Number(0.0),
                },
                after_condition: None,
                guard: None,
                actions: vec![Statement::Animate(AnimationStatement {
                    target: "player1".to_owned(),
                    clip: "back_death1".to_owned(),
                    speed: 1.0,
                    speed_value: AssignmentValue::Number(1.0),
                    looped: false,
                    blocking: true,
                })],
            })]
        );
    }

    #[test]
    fn parses_computed_comparison_conditions() {
        let program = parse_program(
            "when distance(player1, player2) < 5 do\n  player2.CrossPunch\nend do\nopponent_ai = {\n  if (rnd(6) >= 3) {\n    player2.HighKick\n  }\n}",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![
                Statement::WhenEvent(WhenEventStatement {
                    condition: Condition::CompareValue {
                        left: AssignmentValue::Distance {
                            left: "player1".to_owned(),
                            right: "player2".to_owned(),
                        },
                        operator: ComparisonOperator::Less,
                        right: AssignmentValue::Number(5.0),
                    },
                    after_condition: None,
                    guard: None,
                    actions: vec![Statement::Animate(AnimationStatement {
                        target: "player2".to_owned(),
                        clip: "CrossPunch".to_owned(),
                        speed: 1.0,
                        speed_value: AssignmentValue::Number(1.0),
                        looped: false,
                        blocking: true,
                    })],
                }),
                Statement::FunctionDef(FunctionDefStatement {
                    name: "opponent_ai".to_owned(),
                    params: Vec::new(),
                    guard: None,
                    actions: vec![Statement::If(IfStatement {
                        condition: Condition::CompareValue {
                            left: AssignmentValue::RandomInt {
                                max: Box::new(AssignmentValue::Number(6.0)),
                            },
                            operator: ComparisonOperator::GreaterOrEqual,
                            right: AssignmentValue::Number(3.0),
                        },
                        actions: vec![Statement::Animate(AnimationStatement {
                            target: "player2".to_owned(),
                            clip: "HighKick".to_owned(),
                            speed: 1.0,
                            speed_value: AssignmentValue::Number(1.0),
                            looped: false,
                            blocking: true,
                        })],
                        else_actions: Vec::new(),
                    })],
                }),
            ]
        );
    }

    #[test]
    fn parses_parenthesized_arithmetic_comparison_condition() {
        let program = parse_program(
            "math_case = {\n  if ((a + b) * c == 18) {\n    Logger.info \"PASS:parentheses\"\n  }\n}",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::FunctionDef(FunctionDefStatement {
                name: "math_case".to_owned(),
                params: Vec::new(),
                guard: None,
                actions: vec![Statement::If(IfStatement {
                    condition: Condition::EqualsValue {
                        left: AssignmentValue::Binary {
                            left: Box::new(AssignmentValue::Binary {
                                left: Box::new(AssignmentValue::Symbol("a".to_owned())),
                                operator: ArithmeticOperator::Add,
                                right: Box::new(AssignmentValue::Symbol("b".to_owned())),
                            }),
                            operator: ArithmeticOperator::Multiply,
                            right: Box::new(AssignmentValue::Symbol("c".to_owned())),
                        },
                        right: AssignmentValue::Number(18.0),
                    },
                    actions: vec![Statement::Logger(LoggerStatement {
                        level: LoggerLevel::Info,
                        message: LoggerMessage::Text("PASS:parentheses".to_owned()),
                    })],
                    else_actions: Vec::new(),
                })],
            })]
        );
    }

    #[test]
    fn parses_transition_when_and_wait_until() {
        let program = parse_program(
            "when op_hit==0 after op_hit==1 do\n  life1=INITIAL_PLAYER_STRENGTH\nend do\nwait for camera_mode==CAMERA_MODE_DEFAULT",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![
                Statement::WhenEvent(WhenEventStatement {
                    condition: Condition::EqualsNumber {
                        name: "op_hit".to_owned(),
                        value: 0.0,
                    },
                    after_condition: Some(Condition::EqualsNumber {
                        name: "op_hit".to_owned(),
                        value: 1.0,
                    }),
                    guard: None,
                    actions: vec![Statement::Assignment(AssignmentStatement {
                        name: "life1".to_owned(),
                        value: AssignmentValue::Symbol("INITIAL_PLAYER_STRENGTH".to_owned()),
                    })],
                }),
                Statement::WaitUntil {
                    condition: Condition::EqualsSymbol {
                        name: "camera_mode".to_owned(),
                        value: "CAMERA_MODE_DEFAULT".to_owned(),
                    },
                },
            ]
        );
    }

    #[test]
    fn parses_whitespace_tolerant_key_event_header() {
        let program = parse_program(
            "[@allow_move]\nwhen key left is pressed  do\n  player1.turn left 3 in 0.1 seconds async\nend do",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::KeyEvent(KeyEventStatement {
                key: "left".to_owned(),
                trigger: KeyTrigger::Pressed,
                guard: Some(Condition::Alias("allow_move".to_owned())),
                actions: vec![Statement::Turn(TurnStatement {
                    target: "player1".to_owned(),
                    degrees: 3.0,
                    degrees_value: AssignmentValue::Number(3.0),
                    duration_seconds: 0.1,
                    duration_value: AssignmentValue::Number(0.1),
                    loop_condition: None,
                    async_run: true,
                })],
            })]
        );
    }

    #[test]
    fn parses_brace_when_symbolic_wait_and_return_value() {
        let program = parse_program(
            "when (life2 < 5 && rocks_started==0) {\n  wait tm seconds\n  return rock1\n}",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::WhenEvent(WhenEventStatement {
                condition: Condition::And(vec![
                    Condition::Compare {
                        name: "life2".to_owned(),
                        operator: ComparisonOperator::Less,
                        value: AssignmentValue::Number(5.0),
                    },
                    Condition::EqualsNumber {
                        name: "rocks_started".to_owned(),
                        value: 0.0,
                    },
                ]),
                after_condition: None,
                guard: None,
                actions: vec![
                    Statement::WaitValue {
                        value: AssignmentValue::Symbol("tm".to_owned()),
                    },
                    Statement::ReturnValue {
                        value: AssignmentValue::Symbol("rock1".to_owned()),
                    },
                ],
            })]
        );
    }

    #[test]
    fn parses_fighting_camera_system_declaration() {
        let program = parse_program(
            "fight_cam = camera.system.fighting(player1, player2, depth 18, height 3, side 1.5, min distance 10, max distance 28, damping 8)",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::FightingCamera(FightingCameraStatement {
                name: "fight_cam".to_owned(),
                target_a: "player1".to_owned(),
                target_b: "player2".to_owned(),
                depth: 18.0,
                height: 3.0,
                side: 1.5,
                min_distance: 10.0,
                max_distance: 28.0,
                zoom_factor: 1.15,
                damping: 8.0,
                look_ahead: 0.0,
                fov: 50.0,
                max_fov: 62.0,
            })]
        );
    }

    #[test]
    fn parses_function_definition_and_run_command() {
        let program = parse_program(
            "game_start = {\n  boss.show\n  boss.Idle loop\n  run set_camera_on_player\n}\nrun game_start",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![
                Statement::FunctionDef(FunctionDefStatement {
                    name: "game_start".to_owned(),
                    params: Vec::new(),
                    guard: None,
                    actions: vec![
                        Statement::Visibility {
                            target: "boss".to_owned(),
                            visible: true,
                        },
                        Statement::Animate(AnimationStatement {
                            target: "boss".to_owned(),
                            clip: "Idle".to_owned(),
                            speed: 1.0,
                            speed_value: AssignmentValue::Number(1.0),
                            looped: true,
                            blocking: false,
                        }),
                        Statement::RunFunction {
                            name: "set_camera_on_player".to_owned(),
                            args: Vec::new(),
                        },
                    ],
                }),
                Statement::RunFunction {
                    name: "game_start".to_owned(),
                    args: Vec::new(),
                },
            ]
        );
    }

    #[test]
    fn parses_key_event_inside_function_without_flattening_body() {
        let program = parse_program(
            "install_input = {\n  when key Q is pressed once do\n    marker = 1\n  end do\n}\nrun install_input",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![
                Statement::FunctionDef(FunctionDefStatement {
                    name: "install_input".to_owned(),
                    params: Vec::new(),
                    guard: None,
                    actions: vec![Statement::KeyEvent(KeyEventStatement {
                        key: "q".to_owned(),
                        trigger: KeyTrigger::PressedOnce,
                        guard: None,
                        actions: vec![Statement::Assignment(AssignmentStatement {
                            name: "marker".to_owned(),
                            value: AssignmentValue::Number(1.0),
                        })],
                    })],
                }),
                Statement::RunFunction {
                    name: "install_input".to_owned(),
                    args: Vec::new(),
                },
            ]
        );
    }

    #[test]
    fn parses_guarded_key_event_inside_function_as_event_guard() {
        let program = parse_program(
            "actor.data.ready = 1\nactor.data.busy = 0\ninstall_input = {\n  [actor.data.ready == 1 && actor.data.busy != 1]\n  when key Q is pressed once do\n    marker = 1\n  end do\n}\nrun install_input",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![
                Statement::Assignment(AssignmentStatement {
                    name: "actor.data.ready".to_owned(),
                    value: AssignmentValue::Number(1.0),
                }),
                Statement::Assignment(AssignmentStatement {
                    name: "actor.data.busy".to_owned(),
                    value: AssignmentValue::Number(0.0),
                }),
                Statement::FunctionDef(FunctionDefStatement {
                    name: "install_input".to_owned(),
                    params: Vec::new(),
                    guard: None,
                    actions: vec![Statement::KeyEvent(KeyEventStatement {
                        key: "q".to_owned(),
                        trigger: KeyTrigger::PressedOnce,
                        guard: Some(Condition::And(vec![
                            Condition::EqualsNumber {
                                name: "actor.data.ready".to_owned(),
                                value: 1.0,
                            },
                            Condition::NotEqualsNumber {
                                name: "actor.data.busy".to_owned(),
                                value: 1.0,
                            },
                        ])),
                        actions: vec![Statement::Assignment(AssignmentStatement {
                            name: "marker".to_owned(),
                            value: AssignmentValue::Number(1.0),
                        })],
                    })],
                }),
                Statement::RunFunction {
                    name: "install_input".to_owned(),
                    args: Vec::new(),
                },
            ]
        );
    }

    #[test]
    fn parses_guarded_when_event_inside_function_as_event_guard() {
        let program = parse_program(
            "install_collision = {\n  [actor.data.active == 1]\n  when actor.tool.colliders[\"tool_sensor\"] collides with target_box do\n    marker = 1\n  end do\n}\nrun install_collision",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![
                Statement::FunctionDef(FunctionDefStatement {
                    name: "install_collision".to_owned(),
                    params: Vec::new(),
                    guard: None,
                    actions: vec![Statement::WhenEvent(WhenEventStatement {
                        condition: Condition::Collision {
                            sources: vec!["actor.tool.colliders[\"tool_sensor\"]".to_owned()],
                            target: "target_box".to_owned(),
                        },
                        after_condition: None,
                        guard: Some(Condition::EqualsNumber {
                            name: "actor.data.active".to_owned(),
                            value: 1.0,
                        }),
                        actions: vec![Statement::Assignment(AssignmentStatement {
                            name: "marker".to_owned(),
                            value: AssignmentValue::Number(1.0),
                        })],
                    })],
                }),
                Statement::RunFunction {
                    name: "install_collision".to_owned(),
                    args: Vec::new(),
                },
            ]
        );
    }

    #[test]
    fn parses_recurring_run_command() {
        let program = parse_program("run enemy_turn every 1.2 seconds").unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::RunEvery {
                name: "enemy_turn".to_owned(),
                args: Vec::new(),
                interval_seconds: 1.2,
                interval_value: AssignmentValue::Number(1.2),
            }]
        );
    }

    #[test]
    fn parses_parameterized_function_and_call() {
        let program = parse_program(
            "op_punch(p2) = {\n  p2.move forward 0.2 for 0.2 seconds async\n  p2.CrossPunch at speed of 1.8\n}\nrun op_punch(player2)\nrun opponent_ai(player1, player2) every 0.65 seconds",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![
                Statement::FunctionDef(FunctionDefStatement {
                    name: "op_punch".to_owned(),
                    params: vec!["p2".to_owned()],
                    guard: None,
                    actions: vec![
                        Statement::Move(MoveStatement {
                            target: "p2".to_owned(),
                            direction: MoveDirection::Forward,
                            distance: 0.2,
                            distance_value: AssignmentValue::Number(0.2),
                            duration_seconds: 0.2,
                            duration_value: AssignmentValue::Number(0.2),
                            loop_condition: None,
                            async_run: true,
                        }),
                        Statement::Animate(AnimationStatement {
                            target: "p2".to_owned(),
                            clip: "CrossPunch".to_owned(),
                            speed: 1.8,
                            speed_value: AssignmentValue::Number(1.8),
                            looped: false,
                            blocking: true,
                        }),
                    ],
                }),
                Statement::RunFunction {
                    name: "op_punch".to_owned(),
                    args: vec!["player2".to_owned()],
                },
                Statement::RunEvery {
                    name: "opponent_ai".to_owned(),
                    args: vec!["player1".to_owned(), "player2".to_owned()],
                    interval_seconds: 0.65,
                    interval_value: AssignmentValue::Number(0.65),
                },
            ]
        );
    }

    #[test]
    fn parses_guarded_function_definition() {
        let program = parse_program(
            "[@enemy_ai_allowed]\nopponent_ai(p1, p2) = {\n  p2.look at (p1)\n}\nrun opponent_ai(player1, player2) every 0.65 seconds",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![
                Statement::FunctionDef(FunctionDefStatement {
                    name: "opponent_ai".to_owned(),
                    params: vec!["p1".to_owned(), "p2".to_owned()],
                    guard: Some(Condition::Alias("enemy_ai_allowed".to_owned())),
                    actions: vec![Statement::LookAt {
                        target: "p2".to_owned(),
                        subject: "p1".to_owned(),
                    }],
                }),
                Statement::RunEvery {
                    name: "opponent_ai".to_owned(),
                    args: vec!["player1".to_owned(), "player2".to_owned()],
                    interval_seconds: 0.65,
                    interval_value: AssignmentValue::Number(0.65),
                },
            ]
        );
    }

    #[test]
    fn parses_camera_system_selection() {
        let program = parse_program("camera.system = fight_cam").unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::CameraSystemSelect {
                name: "fight_cam".to_owned(),
            }]
        );
    }

    #[test]
    fn parses_logger_commands() {
        let program =
            parse_program("Logger.info \"ready\"\nLogger.debug counter\nLogger.error enemy_ko")
                .unwrap();

        assert_eq!(
            program.statements,
            vec![
                Statement::Logger(LoggerStatement {
                    level: LoggerLevel::Info,
                    message: LoggerMessage::Text("ready".to_owned()),
                }),
                Statement::Logger(LoggerStatement {
                    level: LoggerLevel::Debug,
                    message: LoggerMessage::Value(AssignmentValue::Symbol("counter".to_owned())),
                }),
                Statement::Logger(LoggerStatement {
                    level: LoggerLevel::Error,
                    message: LoggerMessage::Value(AssignmentValue::Symbol("enemy_ko".to_owned())),
                }),
            ]
        );
    }

    #[test]
    fn parses_debug_mode_commands() {
        let program = parse_program("debug.on\ndebug.off").unwrap();

        assert_eq!(
            program.statements,
            vec![
                Statement::DebugMode { enabled: true },
                Statement::DebugMode { enabled: false },
            ]
        );
    }

    #[test]
    fn parses_weapon_runtime_commands() {
        let program = parse_program(
            "actor.weapon = \"weapon_training_tool\"\n\
             actor.weapon = empty",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![
                Statement::Weapon(WeaponStatement {
                    owner: "actor".to_owned(),
                    action: WeaponAction::Equip {
                        weapon: "weapon_training_tool".to_owned(),
                    },
                }),
                Statement::Weapon(WeaponStatement {
                    owner: "actor".to_owned(),
                    action: WeaponAction::Unequip,
                }),
            ]
        );

        let program = parse_program("actor.weapon.posture = \"ready\"").unwrap();
        assert_eq!(
            program.statements,
            vec![Statement::Weapon(WeaponStatement {
                owner: "actor".to_owned(),
                action: WeaponAction::Posture {
                    posture: "ready".to_owned(),
                },
            })]
        );

        let program = parse_program("equip = {\n  actor.weapon = weapon_training_tool\n}").unwrap();
        let Statement::FunctionDef(function) = &program.statements[0] else {
            panic!("expected function definition");
        };
        assert_eq!(
            function.actions,
            vec![Statement::Weapon(WeaponStatement {
                owner: "actor".to_owned(),
                action: WeaponAction::Equip {
                    weapon: "weapon_training_tool".to_owned(),
                },
            })]
        );
    }

    #[test]
    fn parses_throw_motion_runtime_commands() {
        let program = parse_program(
            "tool => model_tool\n\
             target => model_target\n\
             motion = system.motion(\"motion_training_throw\", target target)\n\
             motion.apply tool async",
        )
        .unwrap();

        let Statement::Assignment(AssignmentStatement {
            name,
            value: AssignmentValue::ThrowMotion(value),
        }) = &program.statements[2]
        else {
            panic!("expected throw motion assignment");
        };
        assert_eq!(name, "motion");
        assert!(matches!(
            value.as_ref(),
            ThrowMotionValue {
                asset: ThrowMotionAsset::Literal(asset),
                target: Some(ThrowMotionTarget::Object(target)),
            } if asset == "motion_training_throw" && target == "target"
        ));
        assert_eq!(
            program.statements[3],
            Statement::ThrowMotionApply(ThrowMotionApplyStatement {
                motion: "motion".to_owned(),
                target: "tool".to_owned(),
                target_is_equipped_weapon: false,
                async_run: true,
            })
        );
    }

    #[test]
    fn parses_throw_motion_equipped_weapon_and_events() {
        let program = parse_program(
            "actor => model_actor\n\
             actor.weapon.detach\n\
             motion = system.motion(\"motion_training_throw\", target (1, height, 3))\n\
             motion.event(\"on_index\", 50) = {\n\
               marker = 1\n\
             }\n\
             motion.apply actor.weapon",
        )
        .unwrap();

        assert_eq!(
            program.statements[1],
            Statement::Weapon(WeaponStatement {
                owner: "actor".to_owned(),
                action: WeaponAction::Detach,
            })
        );
        let Statement::Assignment(AssignmentStatement {
            value: AssignmentValue::ThrowMotion(value),
            ..
        }) = &program.statements[2]
        else {
            panic!("expected throw motion assignment");
        };
        assert!(matches!(
            value.as_ref(),
            ThrowMotionValue {
                target: Some(ThrowMotionTarget::Position(values)),
                ..
            } if values[1] == AssignmentValue::Symbol("height".to_owned())
        ));
        assert!(matches!(
            &program.statements[3],
            Statement::ThrowMotionEvent(ThrowMotionEventStatement {
                motion,
                event,
                index_percent: Some(AssignmentValue::Number(50.0)),
                actions,
            }) if motion == "motion"
                && event == "on_index"
                && actions == &vec![Statement::Assignment(AssignmentStatement {
                    name: "marker".to_owned(),
                    value: AssignmentValue::Number(1.0),
                })]
        ));
        assert_eq!(
            program.statements[4],
            Statement::ThrowMotionApply(ThrowMotionApplyStatement {
                motion: "motion".to_owned(),
                target: "actor".to_owned(),
                target_is_equipped_weapon: true,
                async_run: false,
            })
        );
    }

    #[test]
    fn parses_animation_controller_runtime_commands() {
        let program = parse_program(
            "actor => model_actor\n\
             anim = animation actor.attack\n\
             anim.event(\"attack\", 30) = {\n\
               marker = 1\n\
             }\n\
             anim.run\n\
             anim.stop",
        )
        .unwrap();

        let Statement::Assignment(AssignmentStatement {
            name,
            value: AssignmentValue::AnimationController(value),
        }) = &program.statements[1]
        else {
            panic!("expected animation controller assignment");
        };
        assert_eq!(name, "anim");
        assert_eq!(
            value,
            &AnimationControllerValue {
                target: "actor".to_owned(),
                clip: "attack".to_owned(),
            }
        );
        assert!(matches!(
            &program.statements[2],
            Statement::AnimationControllerEvent(AnimationControllerEventStatement {
                controller,
                animation,
                percent: AssignmentValue::Number(30.0),
                actions,
            }) if controller == "anim"
                && animation == "attack"
                && actions == &vec![Statement::Assignment(AssignmentStatement {
                    name: "marker".to_owned(),
                    value: AssignmentValue::Number(1.0),
                })]
        ));
        assert_eq!(
            program.statements[3],
            Statement::AnimationControllerAction(AnimationControllerActionStatement {
                controller: "anim".to_owned(),
                action: AnimationControllerAction::Run,
            })
        );
        assert_eq!(
            program.statements[4],
            Statement::AnimationControllerAction(AnimationControllerActionStatement {
                controller: "anim".to_owned(),
                action: AnimationControllerAction::Stop,
            })
        );
    }

    #[test]
    fn parses_animation_controller_inside_key_event() {
        let program = parse_program(
            "actor => model_actor\n\
             when key Q is pressed once do\n\
               anim = animation actor.attack\n\
               anim.event(\"attack\", 30) = {\n\
                 marker = 1\n\
               }\n\
               anim.run\n\
             end do",
        )
        .unwrap();

        let Statement::KeyEvent(event) = &program.statements[1] else {
            panic!("expected key event");
        };
        assert!(matches!(
            &event.actions[0],
            Statement::Assignment(AssignmentStatement {
                name,
                value: AssignmentValue::AnimationController(AnimationControllerValue {
                    target,
                    clip,
                }),
            }) if name == "anim" && target == "actor" && clip == "attack"
        ));
        assert!(matches!(
            &event.actions[1],
            Statement::AnimationControllerEvent(AnimationControllerEventStatement {
                controller,
                animation,
                percent: AssignmentValue::Number(30.0),
                ..
            }) if controller == "anim" && animation == "attack"
        ));
        assert_eq!(
            event.actions[2],
            Statement::AnimationControllerAction(AnimationControllerActionStatement {
                controller: "anim".to_owned(),
                action: AnimationControllerAction::Run,
            })
        );
    }

    #[test]
    fn parses_animation_controller_run_after_nested_event_blocks() {
        let program = parse_program(
            "actor => model_actor\n\
             when key Q is pressed once do\n\
               anim = animation actor.attack\n\
               anim.event(\"attack\", 30) = {\n\
                 motion = system.motion(\"motion_training_throw\")\n\
                 motion.event(\"on_index\", 50) = {\n\
                   anim.stop\n\
                 }\n\
                 motion.apply actor\n\
               }\n\
               anim.run\n\
             end do",
        )
        .unwrap();

        let Statement::KeyEvent(event) = &program.statements[1] else {
            panic!("expected key event");
        };
        assert!(matches!(
            event.actions.last(),
            Some(Statement::AnimationControllerAction(AnimationControllerActionStatement {
                controller,
                action: AnimationControllerAction::Run,
            })) if controller == "anim"
        ));
        let Statement::AnimationControllerEvent(animation_event) = &event.actions[1] else {
            panic!("expected animation controller event");
        };
        assert!(matches!(
            animation_event.actions.get(1),
            Some(Statement::ThrowMotionEvent(ThrowMotionEventStatement {
                motion,
                event,
                index_percent: Some(AssignmentValue::Number(50.0)),
                ..
            })) if motion == "motion" && event == "on_index"
        ));
        let Statement::ThrowMotionEvent(motion_event) = &animation_event.actions[1] else {
            panic!("expected throw motion event");
        };
        assert_eq!(
            motion_event.actions,
            vec![Statement::AnimationControllerAction(
                AnimationControllerActionStatement {
                    controller: "anim".to_owned(),
                    action: AnimationControllerAction::Stop,
                },
            )]
        );
    }

    #[test]
    fn parses_runtime_ui_commands() {
        let program = parse_program(
            "UI.load \"game_intro_ui\"\n\
             UI.layer1.titlePanel.ease(\"EaseInBack\", Down, 0.6)\n\
             UI.layer1.titlePanel.titleText.message(\"MASTER THE FIGHT\", TextEffect.fade_in, 1.1)\n\
             UI.layer1.footerPanel.hide",
        )
        .unwrap();

        assert!(matches!(
            &program.statements[0],
            Statement::UiLoad { name } if name == "game_intro_ui"
        ));
        assert!(matches!(
            &program.statements[1],
            Statement::UiEase(ease)
                if ease.target.layer == "layer1"
                    && ease.target.widget_path == vec!["titlePanel"]
                    && ease.direction == UiEaseDirection::Down
                    && !ease.async_run
        ));
        assert!(matches!(
            &program.statements[2],
            Statement::UiMessage(message)
                if message.target.widget_path == vec!["titlePanel", "titleText"]
                    && message.text == "MASTER THE FIGHT"
                    && message.effects == "TextEffect.fade_in"
        ));
        assert!(matches!(
            &program.statements[3],
            Statement::UiShowHide(show_hide)
                if show_hide.target.widget_path == vec!["footerPanel"] && !show_hide.visible
        ));
    }

    #[test]
    fn parses_print_statement_with_position_and_clear() {
        let program = parse_program(
            "sys.print \"hello world\": pos (10, 0, 0)\n\
             debug.print \"hp=\" + hp having color yellow and size 1.5 and append\n\
             sys.print \"\"",
        )
        .unwrap();

        assert!(matches!(
            &program.statements[0],
            Statement::Print(PrintStatement {
                channel,
                text: UiPropertyValue::Literal(text),
                position: Some(PositionValue::Coordinates(values)),
                ..
            }) if channel == "sys" && text == "hello world" && values.len() == 3
        ));
        assert!(matches!(
            &program.statements[1],
            Statement::Print(PrintStatement {
                channel,
                text: UiPropertyValue::Concatenation(parts),
                color: Some(color),
                font_size: Some(AssignmentValue::Number(size)),
                append: true,
                ..
            }) if channel == "debug"
                && parts == &vec![
                    UiPropertyValuePart::Literal("hp=".to_owned()),
                    UiPropertyValuePart::Expression(AssignmentValue::Symbol("hp".to_owned())),
                ]
                && color == "yellow"
                && (*size - 1.5).abs() < f32::EPSILON
        ));
        assert!(matches!(
            &program.statements[2],
            Statement::Print(PrintStatement {
                channel,
                text: UiPropertyValue::Literal(text),
                ..
            }) if channel == "sys" && text.is_empty()
        ));
    }

    #[test]
    fn parses_async_ui_ease_suffix() {
        let program =
            parse_program("UI.layer1.titlePanel.ease(\"EaseInBack\", Down, 0.6) async").unwrap();

        assert!(matches!(
            &program.statements[0],
            Statement::UiEase(ease) if ease.async_run
        ));
    }

    #[test]
    fn parses_ui_message_with_comma_in_text() {
        let program = parse_program(
            "UI.layer1.footerPanel.footerSub.message(\"Memorize the keys, then launch straight into the fight.\", TextEffect.word_reveal | TextEffect.fade_in, 1.2)",
        )
        .unwrap();

        assert!(matches!(
            &program.statements[0],
            Statement::UiMessage(message)
                if message.target.widget_path == vec!["footerPanel", "footerSub"]
                    && message.text == "Memorize the keys, then launch straight into the fight."
                    && message.effects == "TextEffect.word_reveal | TextEffect.fade_in"
                    && (message.duration_seconds - 1.2).abs() < f32::EPSILON
                    && !message.async_run
        ));
    }

    #[test]
    fn parses_async_ui_message_suffix() {
        let program = parse_program(
            "UI.layer1.panelMsg.txtMsg.message(\"Async is just text\", TextEffect.fade_in, 7) Async",
        )
        .unwrap();

        assert!(matches!(
            &program.statements[0],
            Statement::UiMessage(message)
                if message.text == "Async is just text"
                    && (message.duration_seconds - 7.0).abs() < f32::EPSILON
                    && message.async_run
        ));
    }

    #[test]
    fn parses_ui_text_property_expression_and_literal() {
        let program = parse_program(
            "UI.layer1.panel2.timer.text = timer\n\
             UI.layer1.panel2.label.text = \"timer\"\n\
             UI.layer1.panel2.caption.text = \"timer = \" + timer",
        )
        .unwrap();

        assert!(matches!(
            &program.statements[0],
            Statement::UiSetProperty(property)
                if property.target.widget_path == vec!["panel2", "timer"]
                    && property.property == "text"
                    && property.value == UiPropertyValue::Expression(
                        AssignmentValue::Symbol("timer".to_owned())
                    )
        ));
        assert!(matches!(
            &program.statements[1],
            Statement::UiSetProperty(property)
                if property.target.widget_path == vec!["panel2", "label"]
                    && property.property == "text"
                    && property.value == UiPropertyValue::Literal("timer".to_owned())
        ));
        assert!(matches!(
            &program.statements[2],
            Statement::UiSetProperty(property)
                if property.target.widget_path == vec!["panel2", "caption"]
                    && property.property == "text"
                    && property.value == UiPropertyValue::Concatenation(vec![
                        UiPropertyValuePart::Literal("timer = ".to_owned()),
                        UiPropertyValuePart::Expression(AssignmentValue::Symbol("timer".to_owned())),
                    ])
        ));
    }

    #[test]
    fn parses_audio_play_loop_stop_expression_and_volume() {
        let program = parse_program(
            "audio.play \"drums_music1\" loop\n\
             audio.stop \"drums_music1\"\n\
             audio.play selected_sound having volume music_volume loop\n\
             play sound punch1",
        )
        .unwrap();

        assert!(matches!(
            &program.statements[0],
            Statement::Audio(AudioStatement {
                action: AudioAction::Play,
                sound: Some(sound),
                sound_value: None,
                looped: true,
                volume: None,
            }) if sound == "drums_music1"
        ));
        assert!(matches!(
            &program.statements[1],
            Statement::Audio(AudioStatement {
                action: AudioAction::Stop,
                sound: Some(sound),
                ..
            }) if sound == "drums_music1"
        ));
        assert!(matches!(
            &program.statements[2],
            Statement::Audio(AudioStatement {
                action: AudioAction::Play,
                sound: None,
                sound_value: Some(AssignmentValue::Symbol(sound)),
                looped: true,
                volume: Some(AssignmentValue::Symbol(volume)),
            }) if sound == "selected_sound" && volume == "music_volume"
        ));
        assert!(matches!(
            &program.statements[3],
            Statement::Audio(AudioStatement {
                action: AudioAction::Play,
                sound: Some(sound),
                looped: false,
                ..
            }) if sound == "punch1"
        ));
    }

    #[test]
    fn parses_local_comma_assignments_inside_function_blocks() {
        let program = parse_program(
            "print_status = {\n  var frame1=round(life1*16/INITIAL_PLAYER_STRENGTH),\n      frame2=round(life2*16/INITIAL_PLAYER_STRENGTH),\n      player_percent=round(life1*100/INITIAL_PLAYER_STRENGTH),\n      opponent_percent=round(life2*100/INITIAL_PLAYER_STRENGTH)\n  UI.layer1.playerHud.playerHealthValue.text = player_percent + \"%\"\n}",
        )
        .unwrap();

        let Statement::FunctionDef(function) = &program.statements[0] else {
            panic!("expected function definition");
        };

        assert_eq!(function.actions.len(), 5);
        assert!(matches!(
            &function.actions[2],
            Statement::LocalAssignment(AssignmentStatement { name, .. })
                if name == "player_percent"
        ));
        assert!(matches!(
            &function.actions[4],
            Statement::UiSetProperty(property)
                if property.value == UiPropertyValue::Concatenation(vec![
                    UiPropertyValuePart::Expression(AssignmentValue::Symbol(
                        "player_percent".to_owned()
                    )),
                    UiPropertyValuePart::Literal("%".to_owned()),
                ])
        ));
    }

    #[test]
    fn parses_third_person_camera_and_attach_commands() {
        let program = parse_program(
            "crystal_hunt_cam = camera.system.third_person(player1, distance 12, height 3.4, side 4.5, look ahead 4, damping 7, fov 62, max fov 72)\ncamera.attach to tg : pos (0,1,-12)\ncamera.attach stop",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![
                Statement::ThirdPersonCamera(ThirdPersonCameraStatement {
                    name: "crystal_hunt_cam".to_owned(),
                    target: "player1".to_owned(),
                    distance: 12.0,
                    height: 3.4,
                    side: 4.5,
                    look_ahead: 4.0,
                    damping: 7.0,
                    fov: 62.0,
                    max_fov: 72.0,
                }),
                Statement::CameraAttach(CameraAttachStatement {
                    target: "tg".to_owned(),
                    offset: SceneMaxVec3 {
                        x: 0.0,
                        y: 1.0,
                        z: -12.0,
                    },
                }),
                Statement::CameraAttachStop,
            ]
        );
    }

    #[test]
    fn parses_camera_modifier_declaration_and_apply() {
        let program = parse_program(
            "hit_fx = camera.system.modifiers.hit_modifier\nfight_cam.apply hit_fx : duration 0.35, amplitude power",
        )
        .unwrap();

        let Statement::Assignment(assignment) = &program.statements[0] else {
            panic!("expected camera modifier assignment");
        };
        assert_eq!(assignment.name, "hit_fx");
        assert!(matches!(
            assignment.value,
            AssignmentValue::CameraModifier(CameraModifierValue {
                ref modifier_type,
                ..
            }) if modifier_type == "hit_modifier"
        ));
        assert_eq!(
            program.statements[1],
            Statement::CameraModifierApply(CameraModifierApplyStatement {
                target: "fight_cam".to_owned(),
                modifier: "hit_fx".to_owned(),
                overrides: vec![
                    ("duration".to_owned(), AssignmentValue::Number(0.35)),
                    (
                        "amplitude".to_owned(),
                        AssignmentValue::Symbol("power".to_owned()),
                    ),
                ],
            })
        );
    }

    #[test]
    fn parses_audio_and_preserves_runtime_noop_commands_in_action_flow() {
        let program = parse_program(
            "fx_test = {\n  audio.play \"kick1\"\n  laser_effect.play pos (player1)\n  fight_cam.apply hit_fx : duration 0.35\n  axe_throw_cam.play : target axe, duration 1.5\n  camera.chase player1\n  camera.chase stop\n  player2.HighKick at speed of 2.4\n}",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::FunctionDef(FunctionDefStatement {
                name: "fx_test".to_owned(),
                params: Vec::new(),
                guard: None,
                actions: vec![
                    Statement::Audio(AudioStatement {
                        action: AudioAction::Play,
                        sound: Some("kick1".to_owned()),
                        sound_value: None,
                        looped: false,
                        volume: None,
                    }),
                    Statement::EffekseerPlay(EffekseerPlayStatement {
                        target: "laser_effect".to_owned(),
                        position: Some(PositionValue::Entity("player1".to_owned())),
                        looped: false,
                        attrs: Vec::new(),
                        async_run: false,
                    }),
                    Statement::CameraModifierApply(CameraModifierApplyStatement {
                        target: "fight_cam".to_owned(),
                        modifier: "hit_fx".to_owned(),
                        overrides: vec![("duration".to_owned(), AssignmentValue::Number(0.35),)],
                    }),
                    Statement::CinematicPlay(CinematicPlayStatement {
                        target: "axe_throw_cam".to_owned(),
                        look_at: Some(CinematicLookAt::Entity("axe".to_owned())),
                        duration_seconds: 1.5,
                        reverse: false,
                        async_run: false,
                    }),
                    Statement::CameraChase {
                        target: "player1".to_owned(),
                    },
                    Statement::CameraAttachStop,
                    Statement::Animate(AnimationStatement {
                        target: "player2".to_owned(),
                        clip: "HighKick".to_owned(),
                        speed: 2.4,
                        speed_value: AssignmentValue::Number(2.4),
                        looped: false,
                        blocking: true,
                    }),
                ],
            })]
        );
    }

    #[test]
    fn function_block_handles_else_lines_without_swallowing_next_statement() {
        let program = parse_program(
            "set_camera_on_player = {\n  if(enemy_ko==1) {\n    camera.system = crystal_hunt_cam\n  } else {\n    camera.system = fight_cam\n  }\n}\nrun set_camera_on_player",
        )
        .unwrap();

        assert_eq!(
            program.statements,
            vec![
                Statement::FunctionDef(FunctionDefStatement {
                    name: "set_camera_on_player".to_owned(),
                    params: Vec::new(),
                    guard: None,
                    actions: vec![Statement::If(IfStatement {
                        condition: Condition::EqualsNumber {
                            name: "enemy_ko".to_owned(),
                            value: 1.0,
                        },
                        actions: vec![Statement::CameraSystemSelect {
                            name: "crystal_hunt_cam".to_owned(),
                        }],
                        else_actions: vec![Statement::CameraSystemSelect {
                            name: "fight_cam".to_owned(),
                        }],
                    })],
                }),
                Statement::RunFunction {
                    name: "set_camera_on_player".to_owned(),
                    args: Vec::new(),
                },
            ]
        );
    }

    #[test]
    fn preserves_runtime_declarations_inside_function_blocks() {
        let program =
            parse_program("enemy_knockout = {\nwin1 => you_win1 sprite : scale 3\n}\nplayer1.show")
                .unwrap();

        assert_eq!(
            program.statements,
            vec![
                Statement::FunctionDef(FunctionDefStatement {
                    name: "enemy_knockout".to_owned(),
                    params: Vec::new(),
                    guard: None,
                    actions: vec![Statement::ModelDecl {
                        name: "win1".to_owned(),
                        resource: "you_win1".to_owned(),
                        options: EntityOptions {
                            scale: Some(SceneMaxVec3 {
                                x: 3.0,
                                y: 3.0,
                                z: 3.0,
                            }),
                            sprite: true,
                            ..Default::default()
                        },
                    }],
                }),
                Statement::Visibility {
                    target: "player1".to_owned(),
                    visible: true,
                },
            ]
        );
    }

    #[test]
    fn parses_non_gltf_entity_systems_as_placeholder_declarations() {
        let program = parse_program(
            "intro_camera=>cinematic.camera.cinematic_rig_1\nthrow_text => throw_text sprite : hidden\nvid=>videos.foggy_day1\nrocks => Object.Pool(create_rock, size 5)",
        )
        .unwrap();

        assert_eq!(
            program.statements[0],
            Statement::ModelDecl {
                name: "intro_camera".to_owned(),
                resource: "cinematic.camera.cinematic_rig_1".to_owned(),
                options: EntityOptions::default(),
            }
        );
        assert_eq!(
            program.statements[1],
            Statement::ModelDecl {
                name: "throw_text".to_owned(),
                resource: "throw_text".to_owned(),
                options: EntityOptions {
                    hidden: true,
                    sprite: true,
                    ..Default::default()
                },
            }
        );
        assert_eq!(
            program.statements[2],
            Statement::ModelDecl {
                name: "vid".to_owned(),
                resource: "videos.foggy_day1".to_owned(),
                options: EntityOptions::default(),
            }
        );
        assert_eq!(
            program.statements[3],
            Statement::ObjectPool(ObjectPoolStatement {
                name: "rocks".to_owned(),
                factory: "create_rock".to_owned(),
                size: 5,
            })
        );
    }

    #[test]
    fn parses_multiline_assignment_expression_after_trailing_operator() {
        let program = parse_program(
            "var can_go = player1.data.is_jumping == 0 &&\n    game_status != GAME_STATE_OVER",
        )
        .unwrap();

        assert_eq!(program.statements.len(), 1);
        assert!(matches!(
            &program.statements[0],
            Statement::Assignment(AssignmentStatement { name, .. }) if name == "can_go"
        ));
    }

    #[test]
    fn parses_whitespace_heavy_split_dot_command() {
        let program =
            parse_program("player1.\n    move    forward    0.2   for   0.5   seconds").unwrap();

        assert_eq!(
            program.statements,
            vec![Statement::Move(MoveStatement {
                target: "player1".to_owned(),
                direction: MoveDirection::Forward,
                distance: 0.2,
                distance_value: AssignmentValue::Number(0.2),
                duration_seconds: 0.5,
                duration_value: AssignmentValue::Number(0.5),
                loop_condition: None,
                async_run: false,
            })]
        );
    }

    #[test]
    fn parses_effekseer_declaration_and_play_with_entity_position() {
        let program = parse_program(
            "laser_effect => effects.effekseer.Homing_Laser01_3\n\
             laser_effect.play pos (player1), loop",
        )
        .unwrap();

        assert!(matches!(
            &program.statements[0],
            Statement::ModelDecl { name, resource, .. }
                if name == "laser_effect" && resource == "effects.effekseer.Homing_Laser01_3"
        ));
        assert!(matches!(
            &program.statements[1],
            Statement::EffekseerPlay(play)
                if play.target == "laser_effect"
                    && play.position == Some(PositionValue::Entity("player1".to_owned()))
                    && play.looped
        ));
    }

    #[test]
    fn parses_effekseer_play_with_bone_position_and_attrs() {
        let program = parse_program(
            "effect => effects.effekseer.A_Salamander1\n\
             effect.play pos (player1.\"mixamorig:RightHand\"), attr = [\"play_back_speed\" 1.2, \"input0\" 0.9] Async",
        )
        .unwrap();

        assert!(matches!(
            &program.statements[1],
            Statement::EffekseerPlay(play)
                if play.position == Some(PositionValue::Entity("player1.\"mixamorig:RightHand\"".to_owned()))
                    && play.attrs == vec![
                        ("play_back_speed".to_owned(), AssignmentValue::Number(1.2)),
                        ("input0".to_owned(), AssignmentValue::Number(0.9)),
                    ]
                    && play.async_run
        ));
    }
}
