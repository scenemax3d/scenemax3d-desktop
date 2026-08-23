// Generated from SceneMaxNextGen.g4 by ANTLR 4.8
#![allow(dead_code)]
#![allow(non_snake_case)]
#![allow(non_upper_case_globals)]
#![allow(nonstandard_style)]
#![allow(unused_imports)]
#![allow(unused_mut)]
#![allow(unused_braces)]
use super::scenemaxnextgenlistener::*;
use super::scenemaxnextgenvisitor::*;
use antlr_rust::PredictionContextCache;
use antlr_rust::TokenSource;
use antlr_rust::atn::{ATN, INVALID_ALT};
use antlr_rust::atn_deserializer::ATNDeserializer;
use antlr_rust::dfa::DFA;
use antlr_rust::error_strategy::{DefaultErrorStrategy, ErrorStrategy};
use antlr_rust::errors::*;
use antlr_rust::int_stream::EOF;
use antlr_rust::parser::{BaseParser, Parser, ParserNodeType, ParserRecog};
use antlr_rust::parser_atn_simulator::ParserATNSimulator;
use antlr_rust::parser_rule_context::{BaseParserRuleContext, ParserRuleContext, cast, cast_mut};
use antlr_rust::recognizer::{Actions, Recognizer};
use antlr_rust::rule_context::{BaseRuleContext, CustomRuleContext, RuleContext};
use antlr_rust::token::{OwningToken, TOKEN_EOF, Token};
use antlr_rust::token_factory::{CommonTokenFactory, TokenAware, TokenFactory};
use antlr_rust::token_stream::TokenStream;
use antlr_rust::tree::*;
use antlr_rust::vocabulary::{Vocabulary, VocabularyImpl};

use antlr_rust::lazy_static;
use antlr_rust::{TidAble, TidExt};

use std::any::{Any, TypeId};
use std::borrow::{Borrow, BorrowMut};
use std::cell::RefCell;
use std::convert::TryFrom;
use std::marker::PhantomData;
use std::ops::{Deref, DerefMut};
use std::rc::Rc;
use std::sync::Arc;

pub const ISA: isize = 1;
pub const LOOP: isize = 2;
pub const ADD: isize = 3;
pub const CODE: isize = 4;
pub const AT: isize = 5;
pub const SPEED: isize = 6;
pub const OF: isize = 7;
pub const MOVE: isize = 8;
pub const FORWARD: isize = 9;
pub const BACKWARD: isize = 10;
pub const BACK: isize = 11;
pub const LEFT: isize = 12;
pub const RIGHT: isize = 13;
pub const UP: isize = 14;
pub const DOWN: isize = 15;
pub const IN: isize = 16;
pub const FOR: isize = 17;
pub const SECONDS: isize = 18;
pub const TO: isize = 19;
pub const TURN: isize = 20;
pub const ROTATE: isize = 21;
pub const RUN: isize = 22;
pub const EVERY: isize = 23;
pub const ASYNC: isize = 24;
pub const ANIMATION: isize = 25;
pub const PLAY: isize = 26;
pub const FRAME: isize = 27;
pub const CHARACTER: isize = 28;
pub const JUMP: isize = 29;
pub const PHYSICS: isize = 30;
pub const IMPULSE: isize = 31;
pub const WHEN: isize = 32;
pub const TRUE: isize = 33;
pub const FALSE: isize = 34;
pub const DOT: isize = 35;
pub const LPAREN: isize = 36;
pub const RPAREN: isize = 37;
pub const COMMA: isize = 38;
pub const OR: isize = 39;
pub const AND: isize = 40;
pub const EQUALS: isize = 41;
pub const NOTEQUALS: isize = 42;
pub const LTEQ: isize = 43;
pub const GTEQ: isize = 44;
pub const LT: isize = 45;
pub const GT: isize = 46;
pub const PLUS: isize = 47;
pub const MINUS: isize = 48;
pub const MULT: isize = 49;
pub const DIV: isize = 50;
pub const MOD: isize = 51;
pub const NOT: isize = 52;
pub const DECIMAL: isize = 53;
pub const IDENT: isize = 54;
pub const QUOTED_STRING: isize = 55;
pub const LINE_COMMENT: isize = 56;
pub const BLOCK_COMMENT: isize = 57;
pub const WS: isize = 58;
pub const RULE_program: usize = 0;
pub const RULE_statement: usize = 1;
pub const RULE_model_decl: usize = 2;
pub const RULE_animate_stmt: usize = 3;
pub const RULE_speed_clause: usize = 4;
pub const RULE_move_stmt: usize = 5;
pub const RULE_move_to_stmt: usize = 6;
pub const RULE_move_to_destination: usize = 7;
pub const RULE_turn_stmt: usize = 8;
pub const RULE_rotate_stmt: usize = 9;
pub const RULE_move_direction: usize = 10;
pub const RULE_duration_clause: usize = 11;
pub const RULE_run_function_stmt: usize = 12;
pub const RULE_run_every_stmt: usize = 13;
pub const RULE_function_call: usize = 14;
pub const RULE_animation_speed_stmt: usize = 15;
pub const RULE_sprite_play_stmt: usize = 16;
pub const RULE_character_jump_stmt: usize = 17;
pub const RULE_physics_impulse_stmt: usize = 18;
pub const RULE_add_code_stmt: usize = 19;
pub const RULE_qualified_name: usize = 20;
pub const RULE_qualified_name_part: usize = 21;
pub const RULE_animation_name: usize = 22;
pub const RULE_keyword_identifier: usize = 23;
pub const RULE_number: usize = 24;
pub const RULE_logical_expression: usize = 25;
pub const RULE_boolean_and_expression: usize = 26;
pub const RULE_relational_expression: usize = 27;
pub const RULE_additive_expression: usize = 28;
pub const RULE_multiplicative_expression: usize = 29;
pub const RULE_unary_expression: usize = 30;
pub const RULE_primary_expression: usize = 31;
pub const RULE_function_value: usize = 32;
pub const RULE_value: usize = 33;
pub const ruleNames: [&'static str; 34] = [
    "program",
    "statement",
    "model_decl",
    "animate_stmt",
    "speed_clause",
    "move_stmt",
    "move_to_stmt",
    "move_to_destination",
    "turn_stmt",
    "rotate_stmt",
    "move_direction",
    "duration_clause",
    "run_function_stmt",
    "run_every_stmt",
    "function_call",
    "animation_speed_stmt",
    "sprite_play_stmt",
    "character_jump_stmt",
    "physics_impulse_stmt",
    "add_code_stmt",
    "qualified_name",
    "qualified_name_part",
    "animation_name",
    "keyword_identifier",
    "number",
    "logical_expression",
    "boolean_and_expression",
    "relational_expression",
    "additive_expression",
    "multiplicative_expression",
    "unary_expression",
    "primary_expression",
    "function_value",
    "value",
];

pub const _LITERAL_NAMES: [Option<&'static str>; 52] = [
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    None,
    Some("'.'"),
    Some("'('"),
    Some("')'"),
    Some("','"),
    None,
    None,
    Some("'=='"),
    None,
    Some("'<='"),
    Some("'>='"),
    Some("'<'"),
    Some("'>'"),
    Some("'+'"),
    Some("'-'"),
    Some("'*'"),
    Some("'/'"),
    Some("'%'"),
];
pub const _SYMBOLIC_NAMES: [Option<&'static str>; 59] = [
    None,
    Some("ISA"),
    Some("LOOP"),
    Some("ADD"),
    Some("CODE"),
    Some("AT"),
    Some("SPEED"),
    Some("OF"),
    Some("MOVE"),
    Some("FORWARD"),
    Some("BACKWARD"),
    Some("BACK"),
    Some("LEFT"),
    Some("RIGHT"),
    Some("UP"),
    Some("DOWN"),
    Some("IN"),
    Some("FOR"),
    Some("SECONDS"),
    Some("TO"),
    Some("TURN"),
    Some("ROTATE"),
    Some("RUN"),
    Some("EVERY"),
    Some("ASYNC"),
    Some("ANIMATION"),
    Some("PLAY"),
    Some("FRAME"),
    Some("CHARACTER"),
    Some("JUMP"),
    Some("PHYSICS"),
    Some("IMPULSE"),
    Some("WHEN"),
    Some("TRUE"),
    Some("FALSE"),
    Some("DOT"),
    Some("LPAREN"),
    Some("RPAREN"),
    Some("COMMA"),
    Some("OR"),
    Some("AND"),
    Some("EQUALS"),
    Some("NOTEQUALS"),
    Some("LTEQ"),
    Some("GTEQ"),
    Some("LT"),
    Some("GT"),
    Some("PLUS"),
    Some("MINUS"),
    Some("MULT"),
    Some("DIV"),
    Some("MOD"),
    Some("NOT"),
    Some("DECIMAL"),
    Some("IDENT"),
    Some("QUOTED_STRING"),
    Some("LINE_COMMENT"),
    Some("BLOCK_COMMENT"),
    Some("WS"),
];
lazy_static! {
    static ref _shared_context_cache: Arc<PredictionContextCache> =
        Arc::new(PredictionContextCache::new());
    static ref VOCABULARY: Box<dyn Vocabulary> = Box::new(VocabularyImpl::new(
        _LITERAL_NAMES.iter(),
        _SYMBOLIC_NAMES.iter(),
        None
    ));
}

type BaseParserType<'input, I> = BaseParser<
    'input,
    SceneMaxNextGenParserExt<'input>,
    I,
    SceneMaxNextGenParserContextType,
    dyn SceneMaxNextGenListener<'input> + 'input,
>;

type TokenType<'input> = <LocalTokenFactory<'input> as TokenFactory<'input>>::Tok;
pub type LocalTokenFactory<'input> = CommonTokenFactory;

pub type SceneMaxNextGenTreeWalker<'input, 'a> = ParseTreeWalker<
    'input,
    'a,
    SceneMaxNextGenParserContextType,
    dyn SceneMaxNextGenListener<'input> + 'a,
>;

/// Parser for SceneMaxNextGen grammar
pub struct SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input>> + TidAble<'input>,
    H: ErrorStrategy<'input, BaseParserType<'input, I>>,
{
    base: BaseParserType<'input, I>,
    interpreter: Arc<ParserATNSimulator>,
    _shared_context_cache: Box<PredictionContextCache>,
    pub err_handler: H,
}

impl<'input, I, H> SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input>> + TidAble<'input>,
    H: ErrorStrategy<'input, BaseParserType<'input, I>>,
{
    pub fn get_serialized_atn() -> &'static str {
        _serializedATN
    }

    pub fn set_error_strategy(&mut self, strategy: H) {
        self.err_handler = strategy
    }

    pub fn with_strategy(input: I, strategy: H) -> Self {
        antlr_rust::recognizer::check_version("0", "3");
        let interpreter = Arc::new(ParserATNSimulator::new(
            _ATN.clone(),
            _decision_to_DFA.clone(),
            _shared_context_cache.clone(),
        ));
        Self {
            base: BaseParser::new_base_parser(
                input,
                Arc::clone(&interpreter),
                SceneMaxNextGenParserExt {
                    _pd: Default::default(),
                },
            ),
            interpreter,
            _shared_context_cache: Box::new(PredictionContextCache::new()),
            err_handler: strategy,
        }
    }
}

type DynStrategy<'input, I> = Box<dyn ErrorStrategy<'input, BaseParserType<'input, I>> + 'input>;

impl<'input, I> SceneMaxNextGenParser<'input, I, DynStrategy<'input, I>>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input>> + TidAble<'input>,
{
    pub fn with_dyn_strategy(input: I) -> Self {
        Self::with_strategy(input, Box::new(DefaultErrorStrategy::new()))
    }
}

impl<'input, I>
    SceneMaxNextGenParser<'input, I, DefaultErrorStrategy<'input, SceneMaxNextGenParserContextType>>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input>> + TidAble<'input>,
{
    pub fn new(input: I) -> Self {
        Self::with_strategy(input, DefaultErrorStrategy::new())
    }
}

/// Trait for monomorphized trait object that corresponds to the nodes of parse tree generated for SceneMaxNextGenParser
pub trait SceneMaxNextGenParserContext<'input>:
    for<'x> Listenable<dyn SceneMaxNextGenListener<'input> + 'x>
    + for<'x> Visitable<dyn SceneMaxNextGenVisitor<'input> + 'x>
    + ParserRuleContext<
        'input,
        TF = LocalTokenFactory<'input>,
        Ctx = SceneMaxNextGenParserContextType,
    >
{
}

antlr_rust::coerce_from! { 'input : SceneMaxNextGenParserContext<'input> }

impl<'input, 'x, T> VisitableDyn<T> for dyn SceneMaxNextGenParserContext<'input> + 'input
where
    T: SceneMaxNextGenVisitor<'input> + 'x,
{
    fn accept_dyn(&self, visitor: &mut T) {
        self.accept(visitor as &mut (dyn SceneMaxNextGenVisitor<'input> + 'x))
    }
}

impl<'input> SceneMaxNextGenParserContext<'input>
    for TerminalNode<'input, SceneMaxNextGenParserContextType>
{
}
impl<'input> SceneMaxNextGenParserContext<'input>
    for ErrorNode<'input, SceneMaxNextGenParserContextType>
{
}

antlr_rust::tid! { impl<'input> TidAble<'input> for dyn SceneMaxNextGenParserContext<'input> + 'input }

antlr_rust::tid! { impl<'input> TidAble<'input> for dyn SceneMaxNextGenListener<'input> + 'input }

pub struct SceneMaxNextGenParserContextType;
antlr_rust::tid! {SceneMaxNextGenParserContextType}

impl<'input> ParserNodeType<'input> for SceneMaxNextGenParserContextType {
    type TF = LocalTokenFactory<'input>;
    type Type = dyn SceneMaxNextGenParserContext<'input> + 'input;
}

impl<'input, I, H> Deref for SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input>> + TidAble<'input>,
    H: ErrorStrategy<'input, BaseParserType<'input, I>>,
{
    type Target = BaseParserType<'input, I>;

    fn deref(&self) -> &Self::Target {
        &self.base
    }
}

impl<'input, I, H> DerefMut for SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input>> + TidAble<'input>,
    H: ErrorStrategy<'input, BaseParserType<'input, I>>,
{
    fn deref_mut(&mut self) -> &mut Self::Target {
        &mut self.base
    }
}

pub struct SceneMaxNextGenParserExt<'input> {
    _pd: PhantomData<&'input str>,
}

impl<'input> SceneMaxNextGenParserExt<'input> {}
antlr_rust::tid! { SceneMaxNextGenParserExt<'a> }

impl<'input> TokenAware<'input> for SceneMaxNextGenParserExt<'input> {
    type TF = LocalTokenFactory<'input>;
}

impl<'input, I: TokenStream<'input, TF = LocalTokenFactory<'input>> + TidAble<'input>>
    ParserRecog<'input, BaseParserType<'input, I>> for SceneMaxNextGenParserExt<'input>
{
}

impl<'input, I: TokenStream<'input, TF = LocalTokenFactory<'input>> + TidAble<'input>>
    Actions<'input, BaseParserType<'input, I>> for SceneMaxNextGenParserExt<'input>
{
    fn get_grammar_file_name(&self) -> &str {
        "SceneMaxNextGen.g4"
    }

    fn get_rule_names(&self) -> &[&str] {
        &ruleNames
    }

    fn get_vocabulary(&self) -> &dyn Vocabulary {
        &**VOCABULARY
    }
}
//------------------- program ----------------
pub type ProgramContextAll<'input> = ProgramContext<'input>;

pub type ProgramContext<'input> = BaseParserRuleContext<'input, ProgramContextExt<'input>>;

#[derive(Clone)]
pub struct ProgramContextExt<'input> {
    ph: PhantomData<&'input str>,
}

impl<'input> SceneMaxNextGenParserContext<'input> for ProgramContext<'input> {}

impl<'input, 'a> Listenable<dyn SceneMaxNextGenListener<'input> + 'a> for ProgramContext<'input> {
    fn enter(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.enter_every_rule(self);
        listener.enter_program(self);
    }
    fn exit(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.exit_program(self);
        listener.exit_every_rule(self);
    }
}

impl<'input, 'a> Visitable<dyn SceneMaxNextGenVisitor<'input> + 'a> for ProgramContext<'input> {
    fn accept(&self, visitor: &mut (dyn SceneMaxNextGenVisitor<'input> + 'a)) {
        visitor.visit_program(self);
    }
}

impl<'input> CustomRuleContext<'input> for ProgramContextExt<'input> {
    type TF = LocalTokenFactory<'input>;
    type Ctx = SceneMaxNextGenParserContextType;
    fn get_rule_index(&self) -> usize {
        RULE_program
    }
    //fn type_rule_index() -> usize where Self: Sized { RULE_program }
}
antlr_rust::tid! {ProgramContextExt<'a>}

impl<'input> ProgramContextExt<'input> {
    fn new(
        parent: Option<Rc<dyn SceneMaxNextGenParserContext<'input> + 'input>>,
        invoking_state: isize,
    ) -> Rc<ProgramContextAll<'input>> {
        Rc::new(BaseParserRuleContext::new_parser_ctx(
            parent,
            invoking_state,
            ProgramContextExt { ph: PhantomData },
        ))
    }
}

pub trait ProgramContextAttrs<'input>:
    SceneMaxNextGenParserContext<'input> + BorrowMut<ProgramContextExt<'input>>
{
    /// Retrieves first TerminalNode corresponding to token EOF
    /// Returns `None` if there is no child corresponding to token EOF
    fn EOF(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(EOF, 0)
    }
    fn statement_all(&self) -> Vec<Rc<StatementContextAll<'input>>>
    where
        Self: Sized,
    {
        self.children_of_type()
    }
    fn statement(&self, i: usize) -> Option<Rc<StatementContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(i)
    }
}

impl<'input> ProgramContextAttrs<'input> for ProgramContext<'input> {}

impl<'input, I, H> SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input>> + TidAble<'input>,
    H: ErrorStrategy<'input, BaseParserType<'input, I>>,
{
    pub fn program(&mut self) -> Result<Rc<ProgramContextAll<'input>>, ANTLRError> {
        let mut recog = self;
        let _parentctx = recog.ctx.take();
        let mut _localctx = ProgramContextExt::new(_parentctx.clone(), recog.base.get_state());
        recog.base.enter_rule(_localctx.clone(), 0, RULE_program);
        let mut _localctx: Rc<ProgramContextAll> = _localctx;
        let mut _la: isize = -1;
        let result: Result<(), ANTLRError> = (|| {
            //recog.base.enter_outer_alt(_localctx.clone(), 1);
            recog.base.enter_outer_alt(None, 1);
            {
                recog.base.set_state(71);
                recog.err_handler.sync(&mut recog.base)?;
                _la = recog.base.input.la(1);
                while _la == ADD || _la == RUN || _la == IDENT {
                    {
                        {
                            /*InvokeRule statement*/
                            recog.base.set_state(68);
                            recog.statement()?;
                        }
                    }
                    recog.base.set_state(73);
                    recog.err_handler.sync(&mut recog.base)?;
                    _la = recog.base.input.la(1);
                }
                recog.base.set_state(74);
                recog.base.match_token(EOF, &mut recog.err_handler)?;
            }
            Ok(())
        })();
        match result {
            Ok(_) => {}
            Err(e @ ANTLRError::FallThrough(_)) => return Err(e),
            Err(ref re) => {
                //_localctx.exception = re;
                recog.err_handler.report_error(&mut recog.base, re);
                recog.err_handler.recover(&mut recog.base, re)?;
            }
        }
        recog.base.exit_rule();

        Ok(_localctx)
    }
}
//------------------- statement ----------------
pub type StatementContextAll<'input> = StatementContext<'input>;

pub type StatementContext<'input> = BaseParserRuleContext<'input, StatementContextExt<'input>>;

#[derive(Clone)]
pub struct StatementContextExt<'input> {
    ph: PhantomData<&'input str>,
}

impl<'input> SceneMaxNextGenParserContext<'input> for StatementContext<'input> {}

impl<'input, 'a> Listenable<dyn SceneMaxNextGenListener<'input> + 'a> for StatementContext<'input> {
    fn enter(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.enter_every_rule(self);
        listener.enter_statement(self);
    }
    fn exit(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.exit_statement(self);
        listener.exit_every_rule(self);
    }
}

impl<'input, 'a> Visitable<dyn SceneMaxNextGenVisitor<'input> + 'a> for StatementContext<'input> {
    fn accept(&self, visitor: &mut (dyn SceneMaxNextGenVisitor<'input> + 'a)) {
        visitor.visit_statement(self);
    }
}

impl<'input> CustomRuleContext<'input> for StatementContextExt<'input> {
    type TF = LocalTokenFactory<'input>;
    type Ctx = SceneMaxNextGenParserContextType;
    fn get_rule_index(&self) -> usize {
        RULE_statement
    }
    //fn type_rule_index() -> usize where Self: Sized { RULE_statement }
}
antlr_rust::tid! {StatementContextExt<'a>}

impl<'input> StatementContextExt<'input> {
    fn new(
        parent: Option<Rc<dyn SceneMaxNextGenParserContext<'input> + 'input>>,
        invoking_state: isize,
    ) -> Rc<StatementContextAll<'input>> {
        Rc::new(BaseParserRuleContext::new_parser_ctx(
            parent,
            invoking_state,
            StatementContextExt { ph: PhantomData },
        ))
    }
}

pub trait StatementContextAttrs<'input>:
    SceneMaxNextGenParserContext<'input> + BorrowMut<StatementContextExt<'input>>
{
    fn model_decl(&self) -> Option<Rc<Model_declContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(0)
    }
    fn animate_stmt(&self) -> Option<Rc<Animate_stmtContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(0)
    }
    fn move_stmt(&self) -> Option<Rc<Move_stmtContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(0)
    }
    fn move_to_stmt(&self) -> Option<Rc<Move_to_stmtContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(0)
    }
    fn turn_stmt(&self) -> Option<Rc<Turn_stmtContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(0)
    }
    fn rotate_stmt(&self) -> Option<Rc<Rotate_stmtContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(0)
    }
    fn run_function_stmt(&self) -> Option<Rc<Run_function_stmtContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(0)
    }
    fn run_every_stmt(&self) -> Option<Rc<Run_every_stmtContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(0)
    }
    fn animation_speed_stmt(&self) -> Option<Rc<Animation_speed_stmtContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(0)
    }
    fn sprite_play_stmt(&self) -> Option<Rc<Sprite_play_stmtContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(0)
    }
    fn character_jump_stmt(&self) -> Option<Rc<Character_jump_stmtContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(0)
    }
    fn physics_impulse_stmt(&self) -> Option<Rc<Physics_impulse_stmtContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(0)
    }
    fn add_code_stmt(&self) -> Option<Rc<Add_code_stmtContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(0)
    }
}

impl<'input> StatementContextAttrs<'input> for StatementContext<'input> {}

impl<'input, I, H> SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input>> + TidAble<'input>,
    H: ErrorStrategy<'input, BaseParserType<'input, I>>,
{
    pub fn statement(&mut self) -> Result<Rc<StatementContextAll<'input>>, ANTLRError> {
        let mut recog = self;
        let _parentctx = recog.ctx.take();
        let mut _localctx = StatementContextExt::new(_parentctx.clone(), recog.base.get_state());
        recog.base.enter_rule(_localctx.clone(), 2, RULE_statement);
        let mut _localctx: Rc<StatementContextAll> = _localctx;
        let result: Result<(), ANTLRError> = (|| {
            recog.base.set_state(89);
            recog.err_handler.sync(&mut recog.base)?;
            match recog.interpreter.adaptive_predict(1, &mut recog.base)? {
                1 => {
                    //recog.base.enter_outer_alt(_localctx.clone(), 1);
                    recog.base.enter_outer_alt(None, 1);
                    {
                        /*InvokeRule model_decl*/
                        recog.base.set_state(76);
                        recog.model_decl()?;
                    }
                }
                2 => {
                    //recog.base.enter_outer_alt(_localctx.clone(), 2);
                    recog.base.enter_outer_alt(None, 2);
                    {
                        /*InvokeRule animate_stmt*/
                        recog.base.set_state(77);
                        recog.animate_stmt()?;
                    }
                }
                3 => {
                    //recog.base.enter_outer_alt(_localctx.clone(), 3);
                    recog.base.enter_outer_alt(None, 3);
                    {
                        /*InvokeRule move_stmt*/
                        recog.base.set_state(78);
                        recog.move_stmt()?;
                    }
                }
                4 => {
                    //recog.base.enter_outer_alt(_localctx.clone(), 4);
                    recog.base.enter_outer_alt(None, 4);
                    {
                        /*InvokeRule move_to_stmt*/
                        recog.base.set_state(79);
                        recog.move_to_stmt()?;
                    }
                }
                5 => {
                    //recog.base.enter_outer_alt(_localctx.clone(), 5);
                    recog.base.enter_outer_alt(None, 5);
                    {
                        /*InvokeRule turn_stmt*/
                        recog.base.set_state(80);
                        recog.turn_stmt()?;
                    }
                }
                6 => {
                    //recog.base.enter_outer_alt(_localctx.clone(), 6);
                    recog.base.enter_outer_alt(None, 6);
                    {
                        /*InvokeRule rotate_stmt*/
                        recog.base.set_state(81);
                        recog.rotate_stmt()?;
                    }
                }
                7 => {
                    //recog.base.enter_outer_alt(_localctx.clone(), 7);
                    recog.base.enter_outer_alt(None, 7);
                    {
                        /*InvokeRule run_function_stmt*/
                        recog.base.set_state(82);
                        recog.run_function_stmt()?;
                    }
                }
                8 => {
                    //recog.base.enter_outer_alt(_localctx.clone(), 8);
                    recog.base.enter_outer_alt(None, 8);
                    {
                        /*InvokeRule run_every_stmt*/
                        recog.base.set_state(83);
                        recog.run_every_stmt()?;
                    }
                }
                9 => {
                    //recog.base.enter_outer_alt(_localctx.clone(), 9);
                    recog.base.enter_outer_alt(None, 9);
                    {
                        /*InvokeRule animation_speed_stmt*/
                        recog.base.set_state(84);
                        recog.animation_speed_stmt()?;
                    }
                }
                10 => {
                    //recog.base.enter_outer_alt(_localctx.clone(), 10);
                    recog.base.enter_outer_alt(None, 10);
                    {
                        /*InvokeRule sprite_play_stmt*/
                        recog.base.set_state(85);
                        recog.sprite_play_stmt()?;
                    }
                }
                11 => {
                    //recog.base.enter_outer_alt(_localctx.clone(), 11);
                    recog.base.enter_outer_alt(None, 11);
                    {
                        /*InvokeRule character_jump_stmt*/
                        recog.base.set_state(86);
                        recog.character_jump_stmt()?;
                    }
                }
                12 => {
                    //recog.base.enter_outer_alt(_localctx.clone(), 12);
                    recog.base.enter_outer_alt(None, 12);
                    {
                        /*InvokeRule physics_impulse_stmt*/
                        recog.base.set_state(87);
                        recog.physics_impulse_stmt()?;
                    }
                }
                13 => {
                    //recog.base.enter_outer_alt(_localctx.clone(), 13);
                    recog.base.enter_outer_alt(None, 13);
                    {
                        /*InvokeRule add_code_stmt*/
                        recog.base.set_state(88);
                        recog.add_code_stmt()?;
                    }
                }

                _ => {}
            }
            Ok(())
        })();
        match result {
            Ok(_) => {}
            Err(e @ ANTLRError::FallThrough(_)) => return Err(e),
            Err(ref re) => {
                //_localctx.exception = re;
                recog.err_handler.report_error(&mut recog.base, re);
                recog.err_handler.recover(&mut recog.base, re)?;
            }
        }
        recog.base.exit_rule();

        Ok(_localctx)
    }
}
//------------------- model_decl ----------------
pub type Model_declContextAll<'input> = Model_declContext<'input>;

pub type Model_declContext<'input> = BaseParserRuleContext<'input, Model_declContextExt<'input>>;

#[derive(Clone)]
pub struct Model_declContextExt<'input> {
    pub target: Option<TokenType<'input>>,
    pub resource: Option<Rc<Qualified_nameContextAll<'input>>>,
    ph: PhantomData<&'input str>,
}

impl<'input> SceneMaxNextGenParserContext<'input> for Model_declContext<'input> {}

impl<'input, 'a> Listenable<dyn SceneMaxNextGenListener<'input> + 'a>
    for Model_declContext<'input>
{
    fn enter(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.enter_every_rule(self);
        listener.enter_model_decl(self);
    }
    fn exit(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.exit_model_decl(self);
        listener.exit_every_rule(self);
    }
}

impl<'input, 'a> Visitable<dyn SceneMaxNextGenVisitor<'input> + 'a> for Model_declContext<'input> {
    fn accept(&self, visitor: &mut (dyn SceneMaxNextGenVisitor<'input> + 'a)) {
        visitor.visit_model_decl(self);
    }
}

impl<'input> CustomRuleContext<'input> for Model_declContextExt<'input> {
    type TF = LocalTokenFactory<'input>;
    type Ctx = SceneMaxNextGenParserContextType;
    fn get_rule_index(&self) -> usize {
        RULE_model_decl
    }
    //fn type_rule_index() -> usize where Self: Sized { RULE_model_decl }
}
antlr_rust::tid! {Model_declContextExt<'a>}

impl<'input> Model_declContextExt<'input> {
    fn new(
        parent: Option<Rc<dyn SceneMaxNextGenParserContext<'input> + 'input>>,
        invoking_state: isize,
    ) -> Rc<Model_declContextAll<'input>> {
        Rc::new(BaseParserRuleContext::new_parser_ctx(
            parent,
            invoking_state,
            Model_declContextExt {
                target: None,
                resource: None,
                ph: PhantomData,
            },
        ))
    }
}

pub trait Model_declContextAttrs<'input>:
    SceneMaxNextGenParserContext<'input> + BorrowMut<Model_declContextExt<'input>>
{
    /// Retrieves first TerminalNode corresponding to token ISA
    /// Returns `None` if there is no child corresponding to token ISA
    fn ISA(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(ISA, 0)
    }
    /// Retrieves first TerminalNode corresponding to token IDENT
    /// Returns `None` if there is no child corresponding to token IDENT
    fn IDENT(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(IDENT, 0)
    }
    fn qualified_name(&self) -> Option<Rc<Qualified_nameContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(0)
    }
}

impl<'input> Model_declContextAttrs<'input> for Model_declContext<'input> {}

impl<'input, I, H> SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input>> + TidAble<'input>,
    H: ErrorStrategy<'input, BaseParserType<'input, I>>,
{
    pub fn model_decl(&mut self) -> Result<Rc<Model_declContextAll<'input>>, ANTLRError> {
        let mut recog = self;
        let _parentctx = recog.ctx.take();
        let mut _localctx = Model_declContextExt::new(_parentctx.clone(), recog.base.get_state());
        recog.base.enter_rule(_localctx.clone(), 4, RULE_model_decl);
        let mut _localctx: Rc<Model_declContextAll> = _localctx;
        let result: Result<(), ANTLRError> = (|| {
            //recog.base.enter_outer_alt(_localctx.clone(), 1);
            recog.base.enter_outer_alt(None, 1);
            {
                recog.base.set_state(91);
                let tmp = recog.base.match_token(IDENT, &mut recog.err_handler)?;
                cast_mut::<_, Model_declContext>(&mut _localctx).target = Some(tmp.clone());

                recog.base.set_state(92);
                recog.base.match_token(ISA, &mut recog.err_handler)?;

                /*InvokeRule qualified_name*/
                recog.base.set_state(93);
                let tmp = recog.qualified_name()?;
                cast_mut::<_, Model_declContext>(&mut _localctx).resource = Some(tmp.clone());
            }
            Ok(())
        })();
        match result {
            Ok(_) => {}
            Err(e @ ANTLRError::FallThrough(_)) => return Err(e),
            Err(ref re) => {
                //_localctx.exception = re;
                recog.err_handler.report_error(&mut recog.base, re);
                recog.err_handler.recover(&mut recog.base, re)?;
            }
        }
        recog.base.exit_rule();

        Ok(_localctx)
    }
}
//------------------- animate_stmt ----------------
pub type Animate_stmtContextAll<'input> = Animate_stmtContext<'input>;

pub type Animate_stmtContext<'input> =
    BaseParserRuleContext<'input, Animate_stmtContextExt<'input>>;

#[derive(Clone)]
pub struct Animate_stmtContextExt<'input> {
    pub target: Option<TokenType<'input>>,
    pub animation: Option<Rc<Animation_nameContextAll<'input>>>,
    ph: PhantomData<&'input str>,
}

impl<'input> SceneMaxNextGenParserContext<'input> for Animate_stmtContext<'input> {}

impl<'input, 'a> Listenable<dyn SceneMaxNextGenListener<'input> + 'a>
    for Animate_stmtContext<'input>
{
    fn enter(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.enter_every_rule(self);
        listener.enter_animate_stmt(self);
    }
    fn exit(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.exit_animate_stmt(self);
        listener.exit_every_rule(self);
    }
}

impl<'input, 'a> Visitable<dyn SceneMaxNextGenVisitor<'input> + 'a>
    for Animate_stmtContext<'input>
{
    fn accept(&self, visitor: &mut (dyn SceneMaxNextGenVisitor<'input> + 'a)) {
        visitor.visit_animate_stmt(self);
    }
}

impl<'input> CustomRuleContext<'input> for Animate_stmtContextExt<'input> {
    type TF = LocalTokenFactory<'input>;
    type Ctx = SceneMaxNextGenParserContextType;
    fn get_rule_index(&self) -> usize {
        RULE_animate_stmt
    }
    //fn type_rule_index() -> usize where Self: Sized { RULE_animate_stmt }
}
antlr_rust::tid! {Animate_stmtContextExt<'a>}

impl<'input> Animate_stmtContextExt<'input> {
    fn new(
        parent: Option<Rc<dyn SceneMaxNextGenParserContext<'input> + 'input>>,
        invoking_state: isize,
    ) -> Rc<Animate_stmtContextAll<'input>> {
        Rc::new(BaseParserRuleContext::new_parser_ctx(
            parent,
            invoking_state,
            Animate_stmtContextExt {
                target: None,
                animation: None,
                ph: PhantomData,
            },
        ))
    }
}

pub trait Animate_stmtContextAttrs<'input>:
    SceneMaxNextGenParserContext<'input> + BorrowMut<Animate_stmtContextExt<'input>>
{
    /// Retrieves first TerminalNode corresponding to token DOT
    /// Returns `None` if there is no child corresponding to token DOT
    fn DOT(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(DOT, 0)
    }
    /// Retrieves first TerminalNode corresponding to token IDENT
    /// Returns `None` if there is no child corresponding to token IDENT
    fn IDENT(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(IDENT, 0)
    }
    fn animation_name(&self) -> Option<Rc<Animation_nameContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(0)
    }
    fn speed_clause(&self) -> Option<Rc<Speed_clauseContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(0)
    }
    /// Retrieves first TerminalNode corresponding to token LOOP
    /// Returns `None` if there is no child corresponding to token LOOP
    fn LOOP(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(LOOP, 0)
    }
}

impl<'input> Animate_stmtContextAttrs<'input> for Animate_stmtContext<'input> {}

impl<'input, I, H> SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input>> + TidAble<'input>,
    H: ErrorStrategy<'input, BaseParserType<'input, I>>,
{
    pub fn animate_stmt(&mut self) -> Result<Rc<Animate_stmtContextAll<'input>>, ANTLRError> {
        let mut recog = self;
        let _parentctx = recog.ctx.take();
        let mut _localctx = Animate_stmtContextExt::new(_parentctx.clone(), recog.base.get_state());
        recog
            .base
            .enter_rule(_localctx.clone(), 6, RULE_animate_stmt);
        let mut _localctx: Rc<Animate_stmtContextAll> = _localctx;
        let mut _la: isize = -1;
        let result: Result<(), ANTLRError> = (|| {
            //recog.base.enter_outer_alt(_localctx.clone(), 1);
            recog.base.enter_outer_alt(None, 1);
            {
                recog.base.set_state(95);
                let tmp = recog.base.match_token(IDENT, &mut recog.err_handler)?;
                cast_mut::<_, Animate_stmtContext>(&mut _localctx).target = Some(tmp.clone());

                recog.base.set_state(96);
                recog.base.match_token(DOT, &mut recog.err_handler)?;

                /*InvokeRule animation_name*/
                recog.base.set_state(97);
                let tmp = recog.animation_name()?;
                cast_mut::<_, Animate_stmtContext>(&mut _localctx).animation = Some(tmp.clone());

                recog.base.set_state(99);
                recog.err_handler.sync(&mut recog.base)?;
                _la = recog.base.input.la(1);
                if _la == AT {
                    {
                        /*InvokeRule speed_clause*/
                        recog.base.set_state(98);
                        recog.speed_clause()?;
                    }
                }

                recog.base.set_state(102);
                recog.err_handler.sync(&mut recog.base)?;
                _la = recog.base.input.la(1);
                if _la == LOOP {
                    {
                        recog.base.set_state(101);
                        recog.base.match_token(LOOP, &mut recog.err_handler)?;
                    }
                }
            }
            Ok(())
        })();
        match result {
            Ok(_) => {}
            Err(e @ ANTLRError::FallThrough(_)) => return Err(e),
            Err(ref re) => {
                //_localctx.exception = re;
                recog.err_handler.report_error(&mut recog.base, re);
                recog.err_handler.recover(&mut recog.base, re)?;
            }
        }
        recog.base.exit_rule();

        Ok(_localctx)
    }
}
//------------------- speed_clause ----------------
pub type Speed_clauseContextAll<'input> = Speed_clauseContext<'input>;

pub type Speed_clauseContext<'input> =
    BaseParserRuleContext<'input, Speed_clauseContextExt<'input>>;

#[derive(Clone)]
pub struct Speed_clauseContextExt<'input> {
    ph: PhantomData<&'input str>,
}

impl<'input> SceneMaxNextGenParserContext<'input> for Speed_clauseContext<'input> {}

impl<'input, 'a> Listenable<dyn SceneMaxNextGenListener<'input> + 'a>
    for Speed_clauseContext<'input>
{
    fn enter(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.enter_every_rule(self);
        listener.enter_speed_clause(self);
    }
    fn exit(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.exit_speed_clause(self);
        listener.exit_every_rule(self);
    }
}

impl<'input, 'a> Visitable<dyn SceneMaxNextGenVisitor<'input> + 'a>
    for Speed_clauseContext<'input>
{
    fn accept(&self, visitor: &mut (dyn SceneMaxNextGenVisitor<'input> + 'a)) {
        visitor.visit_speed_clause(self);
    }
}

impl<'input> CustomRuleContext<'input> for Speed_clauseContextExt<'input> {
    type TF = LocalTokenFactory<'input>;
    type Ctx = SceneMaxNextGenParserContextType;
    fn get_rule_index(&self) -> usize {
        RULE_speed_clause
    }
    //fn type_rule_index() -> usize where Self: Sized { RULE_speed_clause }
}
antlr_rust::tid! {Speed_clauseContextExt<'a>}

impl<'input> Speed_clauseContextExt<'input> {
    fn new(
        parent: Option<Rc<dyn SceneMaxNextGenParserContext<'input> + 'input>>,
        invoking_state: isize,
    ) -> Rc<Speed_clauseContextAll<'input>> {
        Rc::new(BaseParserRuleContext::new_parser_ctx(
            parent,
            invoking_state,
            Speed_clauseContextExt { ph: PhantomData },
        ))
    }
}

pub trait Speed_clauseContextAttrs<'input>:
    SceneMaxNextGenParserContext<'input> + BorrowMut<Speed_clauseContextExt<'input>>
{
    /// Retrieves first TerminalNode corresponding to token AT
    /// Returns `None` if there is no child corresponding to token AT
    fn AT(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(AT, 0)
    }
    /// Retrieves first TerminalNode corresponding to token SPEED
    /// Returns `None` if there is no child corresponding to token SPEED
    fn SPEED(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(SPEED, 0)
    }
    /// Retrieves first TerminalNode corresponding to token OF
    /// Returns `None` if there is no child corresponding to token OF
    fn OF(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(OF, 0)
    }
    fn logical_expression(&self) -> Option<Rc<Logical_expressionContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(0)
    }
}

impl<'input> Speed_clauseContextAttrs<'input> for Speed_clauseContext<'input> {}

impl<'input, I, H> SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input>> + TidAble<'input>,
    H: ErrorStrategy<'input, BaseParserType<'input, I>>,
{
    pub fn speed_clause(&mut self) -> Result<Rc<Speed_clauseContextAll<'input>>, ANTLRError> {
        let mut recog = self;
        let _parentctx = recog.ctx.take();
        let mut _localctx = Speed_clauseContextExt::new(_parentctx.clone(), recog.base.get_state());
        recog
            .base
            .enter_rule(_localctx.clone(), 8, RULE_speed_clause);
        let mut _localctx: Rc<Speed_clauseContextAll> = _localctx;
        let result: Result<(), ANTLRError> = (|| {
            //recog.base.enter_outer_alt(_localctx.clone(), 1);
            recog.base.enter_outer_alt(None, 1);
            {
                recog.base.set_state(104);
                recog.base.match_token(AT, &mut recog.err_handler)?;

                recog.base.set_state(105);
                recog.base.match_token(SPEED, &mut recog.err_handler)?;

                recog.base.set_state(106);
                recog.base.match_token(OF, &mut recog.err_handler)?;

                /*InvokeRule logical_expression*/
                recog.base.set_state(107);
                recog.logical_expression()?;
            }
            Ok(())
        })();
        match result {
            Ok(_) => {}
            Err(e @ ANTLRError::FallThrough(_)) => return Err(e),
            Err(ref re) => {
                //_localctx.exception = re;
                recog.err_handler.report_error(&mut recog.base, re);
                recog.err_handler.recover(&mut recog.base, re)?;
            }
        }
        recog.base.exit_rule();

        Ok(_localctx)
    }
}
//------------------- move_stmt ----------------
pub type Move_stmtContextAll<'input> = Move_stmtContext<'input>;

pub type Move_stmtContext<'input> = BaseParserRuleContext<'input, Move_stmtContextExt<'input>>;

#[derive(Clone)]
pub struct Move_stmtContextExt<'input> {
    pub target: Option<TokenType<'input>>,
    pub direction: Option<Rc<Move_directionContextAll<'input>>>,
    pub distance: Option<Rc<Logical_expressionContextAll<'input>>>,
    ph: PhantomData<&'input str>,
}

impl<'input> SceneMaxNextGenParserContext<'input> for Move_stmtContext<'input> {}

impl<'input, 'a> Listenable<dyn SceneMaxNextGenListener<'input> + 'a> for Move_stmtContext<'input> {
    fn enter(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.enter_every_rule(self);
        listener.enter_move_stmt(self);
    }
    fn exit(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.exit_move_stmt(self);
        listener.exit_every_rule(self);
    }
}

impl<'input, 'a> Visitable<dyn SceneMaxNextGenVisitor<'input> + 'a> for Move_stmtContext<'input> {
    fn accept(&self, visitor: &mut (dyn SceneMaxNextGenVisitor<'input> + 'a)) {
        visitor.visit_move_stmt(self);
    }
}

impl<'input> CustomRuleContext<'input> for Move_stmtContextExt<'input> {
    type TF = LocalTokenFactory<'input>;
    type Ctx = SceneMaxNextGenParserContextType;
    fn get_rule_index(&self) -> usize {
        RULE_move_stmt
    }
    //fn type_rule_index() -> usize where Self: Sized { RULE_move_stmt }
}
antlr_rust::tid! {Move_stmtContextExt<'a>}

impl<'input> Move_stmtContextExt<'input> {
    fn new(
        parent: Option<Rc<dyn SceneMaxNextGenParserContext<'input> + 'input>>,
        invoking_state: isize,
    ) -> Rc<Move_stmtContextAll<'input>> {
        Rc::new(BaseParserRuleContext::new_parser_ctx(
            parent,
            invoking_state,
            Move_stmtContextExt {
                target: None,
                direction: None,
                distance: None,
                ph: PhantomData,
            },
        ))
    }
}

pub trait Move_stmtContextAttrs<'input>:
    SceneMaxNextGenParserContext<'input> + BorrowMut<Move_stmtContextExt<'input>>
{
    /// Retrieves first TerminalNode corresponding to token DOT
    /// Returns `None` if there is no child corresponding to token DOT
    fn DOT(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(DOT, 0)
    }
    /// Retrieves first TerminalNode corresponding to token MOVE
    /// Returns `None` if there is no child corresponding to token MOVE
    fn MOVE(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(MOVE, 0)
    }
    /// Retrieves first TerminalNode corresponding to token IDENT
    /// Returns `None` if there is no child corresponding to token IDENT
    fn IDENT(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(IDENT, 0)
    }
    fn move_direction(&self) -> Option<Rc<Move_directionContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(0)
    }
    fn logical_expression(&self) -> Option<Rc<Logical_expressionContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(0)
    }
    fn duration_clause(&self) -> Option<Rc<Duration_clauseContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(0)
    }
    /// Retrieves first TerminalNode corresponding to token LOOP
    /// Returns `None` if there is no child corresponding to token LOOP
    fn LOOP(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(LOOP, 0)
    }
}

impl<'input> Move_stmtContextAttrs<'input> for Move_stmtContext<'input> {}

impl<'input, I, H> SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input>> + TidAble<'input>,
    H: ErrorStrategy<'input, BaseParserType<'input, I>>,
{
    pub fn move_stmt(&mut self) -> Result<Rc<Move_stmtContextAll<'input>>, ANTLRError> {
        let mut recog = self;
        let _parentctx = recog.ctx.take();
        let mut _localctx = Move_stmtContextExt::new(_parentctx.clone(), recog.base.get_state());
        recog.base.enter_rule(_localctx.clone(), 10, RULE_move_stmt);
        let mut _localctx: Rc<Move_stmtContextAll> = _localctx;
        let mut _la: isize = -1;
        let result: Result<(), ANTLRError> = (|| {
            //recog.base.enter_outer_alt(_localctx.clone(), 1);
            recog.base.enter_outer_alt(None, 1);
            {
                recog.base.set_state(109);
                let tmp = recog.base.match_token(IDENT, &mut recog.err_handler)?;
                cast_mut::<_, Move_stmtContext>(&mut _localctx).target = Some(tmp.clone());

                recog.base.set_state(110);
                recog.base.match_token(DOT, &mut recog.err_handler)?;

                recog.base.set_state(111);
                recog.base.match_token(MOVE, &mut recog.err_handler)?;

                /*InvokeRule move_direction*/
                recog.base.set_state(112);
                let tmp = recog.move_direction()?;
                cast_mut::<_, Move_stmtContext>(&mut _localctx).direction = Some(tmp.clone());

                /*InvokeRule logical_expression*/
                recog.base.set_state(113);
                let tmp = recog.logical_expression()?;
                cast_mut::<_, Move_stmtContext>(&mut _localctx).distance = Some(tmp.clone());

                recog.base.set_state(115);
                recog.err_handler.sync(&mut recog.base)?;
                _la = recog.base.input.la(1);
                if _la == IN || _la == FOR {
                    {
                        /*InvokeRule duration_clause*/
                        recog.base.set_state(114);
                        recog.duration_clause()?;
                    }
                }

                recog.base.set_state(118);
                recog.err_handler.sync(&mut recog.base)?;
                _la = recog.base.input.la(1);
                if _la == LOOP {
                    {
                        recog.base.set_state(117);
                        recog.base.match_token(LOOP, &mut recog.err_handler)?;
                    }
                }
            }
            Ok(())
        })();
        match result {
            Ok(_) => {}
            Err(e @ ANTLRError::FallThrough(_)) => return Err(e),
            Err(ref re) => {
                //_localctx.exception = re;
                recog.err_handler.report_error(&mut recog.base, re);
                recog.err_handler.recover(&mut recog.base, re)?;
            }
        }
        recog.base.exit_rule();

        Ok(_localctx)
    }
}
//------------------- move_to_stmt ----------------
pub type Move_to_stmtContextAll<'input> = Move_to_stmtContext<'input>;

pub type Move_to_stmtContext<'input> =
    BaseParserRuleContext<'input, Move_to_stmtContextExt<'input>>;

#[derive(Clone)]
pub struct Move_to_stmtContextExt<'input> {
    pub target: Option<TokenType<'input>>,
    pub destination: Option<Rc<Move_to_destinationContextAll<'input>>>,
    ph: PhantomData<&'input str>,
}

impl<'input> SceneMaxNextGenParserContext<'input> for Move_to_stmtContext<'input> {}

impl<'input, 'a> Listenable<dyn SceneMaxNextGenListener<'input> + 'a>
    for Move_to_stmtContext<'input>
{
    fn enter(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.enter_every_rule(self);
        listener.enter_move_to_stmt(self);
    }
    fn exit(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.exit_move_to_stmt(self);
        listener.exit_every_rule(self);
    }
}

impl<'input, 'a> Visitable<dyn SceneMaxNextGenVisitor<'input> + 'a>
    for Move_to_stmtContext<'input>
{
    fn accept(&self, visitor: &mut (dyn SceneMaxNextGenVisitor<'input> + 'a)) {
        visitor.visit_move_to_stmt(self);
    }
}

impl<'input> CustomRuleContext<'input> for Move_to_stmtContextExt<'input> {
    type TF = LocalTokenFactory<'input>;
    type Ctx = SceneMaxNextGenParserContextType;
    fn get_rule_index(&self) -> usize {
        RULE_move_to_stmt
    }
    //fn type_rule_index() -> usize where Self: Sized { RULE_move_to_stmt }
}
antlr_rust::tid! {Move_to_stmtContextExt<'a>}

impl<'input> Move_to_stmtContextExt<'input> {
    fn new(
        parent: Option<Rc<dyn SceneMaxNextGenParserContext<'input> + 'input>>,
        invoking_state: isize,
    ) -> Rc<Move_to_stmtContextAll<'input>> {
        Rc::new(BaseParserRuleContext::new_parser_ctx(
            parent,
            invoking_state,
            Move_to_stmtContextExt {
                target: None,
                destination: None,
                ph: PhantomData,
            },
        ))
    }
}

pub trait Move_to_stmtContextAttrs<'input>:
    SceneMaxNextGenParserContext<'input> + BorrowMut<Move_to_stmtContextExt<'input>>
{
    /// Retrieves first TerminalNode corresponding to token DOT
    /// Returns `None` if there is no child corresponding to token DOT
    fn DOT(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(DOT, 0)
    }
    /// Retrieves first TerminalNode corresponding to token MOVE
    /// Returns `None` if there is no child corresponding to token MOVE
    fn MOVE(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(MOVE, 0)
    }
    /// Retrieves first TerminalNode corresponding to token TO
    /// Returns `None` if there is no child corresponding to token TO
    fn TO(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(TO, 0)
    }
    /// Retrieves first TerminalNode corresponding to token LPAREN
    /// Returns `None` if there is no child corresponding to token LPAREN
    fn LPAREN(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(LPAREN, 0)
    }
    /// Retrieves first TerminalNode corresponding to token RPAREN
    /// Returns `None` if there is no child corresponding to token RPAREN
    fn RPAREN(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(RPAREN, 0)
    }
    /// Retrieves first TerminalNode corresponding to token IDENT
    /// Returns `None` if there is no child corresponding to token IDENT
    fn IDENT(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(IDENT, 0)
    }
    fn move_to_destination(&self) -> Option<Rc<Move_to_destinationContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(0)
    }
    fn duration_clause(&self) -> Option<Rc<Duration_clauseContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(0)
    }
    /// Retrieves first TerminalNode corresponding to token ASYNC
    /// Returns `None` if there is no child corresponding to token ASYNC
    fn ASYNC(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(ASYNC, 0)
    }
}

impl<'input> Move_to_stmtContextAttrs<'input> for Move_to_stmtContext<'input> {}

impl<'input, I, H> SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input>> + TidAble<'input>,
    H: ErrorStrategy<'input, BaseParserType<'input, I>>,
{
    pub fn move_to_stmt(&mut self) -> Result<Rc<Move_to_stmtContextAll<'input>>, ANTLRError> {
        let mut recog = self;
        let _parentctx = recog.ctx.take();
        let mut _localctx = Move_to_stmtContextExt::new(_parentctx.clone(), recog.base.get_state());
        recog
            .base
            .enter_rule(_localctx.clone(), 12, RULE_move_to_stmt);
        let mut _localctx: Rc<Move_to_stmtContextAll> = _localctx;
        let mut _la: isize = -1;
        let result: Result<(), ANTLRError> = (|| {
            //recog.base.enter_outer_alt(_localctx.clone(), 1);
            recog.base.enter_outer_alt(None, 1);
            {
                recog.base.set_state(120);
                let tmp = recog.base.match_token(IDENT, &mut recog.err_handler)?;
                cast_mut::<_, Move_to_stmtContext>(&mut _localctx).target = Some(tmp.clone());

                recog.base.set_state(121);
                recog.base.match_token(DOT, &mut recog.err_handler)?;

                recog.base.set_state(122);
                recog.base.match_token(MOVE, &mut recog.err_handler)?;

                recog.base.set_state(123);
                recog.base.match_token(TO, &mut recog.err_handler)?;

                recog.base.set_state(124);
                recog.base.match_token(LPAREN, &mut recog.err_handler)?;

                /*InvokeRule move_to_destination*/
                recog.base.set_state(125);
                let tmp = recog.move_to_destination()?;
                cast_mut::<_, Move_to_stmtContext>(&mut _localctx).destination = Some(tmp.clone());

                recog.base.set_state(126);
                recog.base.match_token(RPAREN, &mut recog.err_handler)?;

                recog.base.set_state(128);
                recog.err_handler.sync(&mut recog.base)?;
                _la = recog.base.input.la(1);
                if _la == IN || _la == FOR {
                    {
                        /*InvokeRule duration_clause*/
                        recog.base.set_state(127);
                        recog.duration_clause()?;
                    }
                }

                recog.base.set_state(131);
                recog.err_handler.sync(&mut recog.base)?;
                _la = recog.base.input.la(1);
                if _la == ASYNC {
                    {
                        recog.base.set_state(130);
                        recog.base.match_token(ASYNC, &mut recog.err_handler)?;
                    }
                }
            }
            Ok(())
        })();
        match result {
            Ok(_) => {}
            Err(e @ ANTLRError::FallThrough(_)) => return Err(e),
            Err(ref re) => {
                //_localctx.exception = re;
                recog.err_handler.report_error(&mut recog.base, re);
                recog.err_handler.recover(&mut recog.base, re)?;
            }
        }
        recog.base.exit_rule();

        Ok(_localctx)
    }
}
//------------------- move_to_destination ----------------
pub type Move_to_destinationContextAll<'input> = Move_to_destinationContext<'input>;

pub type Move_to_destinationContext<'input> =
    BaseParserRuleContext<'input, Move_to_destinationContextExt<'input>>;

#[derive(Clone)]
pub struct Move_to_destinationContextExt<'input> {
    ph: PhantomData<&'input str>,
}

impl<'input> SceneMaxNextGenParserContext<'input> for Move_to_destinationContext<'input> {}

impl<'input, 'a> Listenable<dyn SceneMaxNextGenListener<'input> + 'a>
    for Move_to_destinationContext<'input>
{
    fn enter(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.enter_every_rule(self);
        listener.enter_move_to_destination(self);
    }
    fn exit(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.exit_move_to_destination(self);
        listener.exit_every_rule(self);
    }
}

impl<'input, 'a> Visitable<dyn SceneMaxNextGenVisitor<'input> + 'a>
    for Move_to_destinationContext<'input>
{
    fn accept(&self, visitor: &mut (dyn SceneMaxNextGenVisitor<'input> + 'a)) {
        visitor.visit_move_to_destination(self);
    }
}

impl<'input> CustomRuleContext<'input> for Move_to_destinationContextExt<'input> {
    type TF = LocalTokenFactory<'input>;
    type Ctx = SceneMaxNextGenParserContextType;
    fn get_rule_index(&self) -> usize {
        RULE_move_to_destination
    }
    //fn type_rule_index() -> usize where Self: Sized { RULE_move_to_destination }
}
antlr_rust::tid! {Move_to_destinationContextExt<'a>}

impl<'input> Move_to_destinationContextExt<'input> {
    fn new(
        parent: Option<Rc<dyn SceneMaxNextGenParserContext<'input> + 'input>>,
        invoking_state: isize,
    ) -> Rc<Move_to_destinationContextAll<'input>> {
        Rc::new(BaseParserRuleContext::new_parser_ctx(
            parent,
            invoking_state,
            Move_to_destinationContextExt { ph: PhantomData },
        ))
    }
}

pub trait Move_to_destinationContextAttrs<'input>:
    SceneMaxNextGenParserContext<'input> + BorrowMut<Move_to_destinationContextExt<'input>>
{
    fn logical_expression_all(&self) -> Vec<Rc<Logical_expressionContextAll<'input>>>
    where
        Self: Sized,
    {
        self.children_of_type()
    }
    fn logical_expression(&self, i: usize) -> Option<Rc<Logical_expressionContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(i)
    }
    /// Retrieves all `TerminalNode`s corresponding to token COMMA in current rule
    fn COMMA_all(&self) -> Vec<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.children_of_type()
    }
    /// Retrieves 'i's TerminalNode corresponding to token COMMA, starting from 0.
    /// Returns `None` if number of children corresponding to token COMMA is less or equal than `i`.
    fn COMMA(&self, i: usize) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(COMMA, i)
    }
    fn qualified_name(&self) -> Option<Rc<Qualified_nameContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(0)
    }
    /// Retrieves first TerminalNode corresponding to token FORWARD
    /// Returns `None` if there is no child corresponding to token FORWARD
    fn FORWARD(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(FORWARD, 0)
    }
}

impl<'input> Move_to_destinationContextAttrs<'input> for Move_to_destinationContext<'input> {}

impl<'input, I, H> SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input>> + TidAble<'input>,
    H: ErrorStrategy<'input, BaseParserType<'input, I>>,
{
    pub fn move_to_destination(
        &mut self,
    ) -> Result<Rc<Move_to_destinationContextAll<'input>>, ANTLRError> {
        let mut recog = self;
        let _parentctx = recog.ctx.take();
        let mut _localctx =
            Move_to_destinationContextExt::new(_parentctx.clone(), recog.base.get_state());
        recog
            .base
            .enter_rule(_localctx.clone(), 14, RULE_move_to_destination);
        let mut _localctx: Rc<Move_to_destinationContextAll> = _localctx;
        let result: Result<(), ANTLRError> = (|| {
            recog.base.set_state(144);
            recog.err_handler.sync(&mut recog.base)?;
            match recog.interpreter.adaptive_predict(8, &mut recog.base)? {
                1 => {
                    //recog.base.enter_outer_alt(_localctx.clone(), 1);
                    recog.base.enter_outer_alt(None, 1);
                    {
                        /*InvokeRule logical_expression*/
                        recog.base.set_state(133);
                        recog.logical_expression()?;

                        recog.base.set_state(134);
                        recog.base.match_token(COMMA, &mut recog.err_handler)?;

                        /*InvokeRule logical_expression*/
                        recog.base.set_state(135);
                        recog.logical_expression()?;

                        recog.base.set_state(136);
                        recog.base.match_token(COMMA, &mut recog.err_handler)?;

                        /*InvokeRule logical_expression*/
                        recog.base.set_state(137);
                        recog.logical_expression()?;
                    }
                }
                2 => {
                    //recog.base.enter_outer_alt(_localctx.clone(), 2);
                    recog.base.enter_outer_alt(None, 2);
                    {
                        /*InvokeRule qualified_name*/
                        recog.base.set_state(139);
                        recog.qualified_name()?;

                        recog.base.set_state(140);
                        recog.base.match_token(FORWARD, &mut recog.err_handler)?;

                        /*InvokeRule logical_expression*/
                        recog.base.set_state(141);
                        recog.logical_expression()?;
                    }
                }
                3 => {
                    //recog.base.enter_outer_alt(_localctx.clone(), 3);
                    recog.base.enter_outer_alt(None, 3);
                    {
                        /*InvokeRule qualified_name*/
                        recog.base.set_state(143);
                        recog.qualified_name()?;
                    }
                }

                _ => {}
            }
            Ok(())
        })();
        match result {
            Ok(_) => {}
            Err(e @ ANTLRError::FallThrough(_)) => return Err(e),
            Err(ref re) => {
                //_localctx.exception = re;
                recog.err_handler.report_error(&mut recog.base, re);
                recog.err_handler.recover(&mut recog.base, re)?;
            }
        }
        recog.base.exit_rule();

        Ok(_localctx)
    }
}
//------------------- turn_stmt ----------------
pub type Turn_stmtContextAll<'input> = Turn_stmtContext<'input>;

pub type Turn_stmtContext<'input> = BaseParserRuleContext<'input, Turn_stmtContextExt<'input>>;

#[derive(Clone)]
pub struct Turn_stmtContextExt<'input> {
    pub target: Option<TokenType<'input>>,
    ph: PhantomData<&'input str>,
}

impl<'input> SceneMaxNextGenParserContext<'input> for Turn_stmtContext<'input> {}

impl<'input, 'a> Listenable<dyn SceneMaxNextGenListener<'input> + 'a> for Turn_stmtContext<'input> {
    fn enter(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.enter_every_rule(self);
        listener.enter_turn_stmt(self);
    }
    fn exit(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.exit_turn_stmt(self);
        listener.exit_every_rule(self);
    }
}

impl<'input, 'a> Visitable<dyn SceneMaxNextGenVisitor<'input> + 'a> for Turn_stmtContext<'input> {
    fn accept(&self, visitor: &mut (dyn SceneMaxNextGenVisitor<'input> + 'a)) {
        visitor.visit_turn_stmt(self);
    }
}

impl<'input> CustomRuleContext<'input> for Turn_stmtContextExt<'input> {
    type TF = LocalTokenFactory<'input>;
    type Ctx = SceneMaxNextGenParserContextType;
    fn get_rule_index(&self) -> usize {
        RULE_turn_stmt
    }
    //fn type_rule_index() -> usize where Self: Sized { RULE_turn_stmt }
}
antlr_rust::tid! {Turn_stmtContextExt<'a>}

impl<'input> Turn_stmtContextExt<'input> {
    fn new(
        parent: Option<Rc<dyn SceneMaxNextGenParserContext<'input> + 'input>>,
        invoking_state: isize,
    ) -> Rc<Turn_stmtContextAll<'input>> {
        Rc::new(BaseParserRuleContext::new_parser_ctx(
            parent,
            invoking_state,
            Turn_stmtContextExt {
                target: None,
                ph: PhantomData,
            },
        ))
    }
}

pub trait Turn_stmtContextAttrs<'input>:
    SceneMaxNextGenParserContext<'input> + BorrowMut<Turn_stmtContextExt<'input>>
{
    /// Retrieves first TerminalNode corresponding to token DOT
    /// Returns `None` if there is no child corresponding to token DOT
    fn DOT(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(DOT, 0)
    }
    /// Retrieves first TerminalNode corresponding to token TURN
    /// Returns `None` if there is no child corresponding to token TURN
    fn TURN(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(TURN, 0)
    }
    fn logical_expression(&self) -> Option<Rc<Logical_expressionContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(0)
    }
    /// Retrieves first TerminalNode corresponding to token IDENT
    /// Returns `None` if there is no child corresponding to token IDENT
    fn IDENT(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(IDENT, 0)
    }
    fn duration_clause(&self) -> Option<Rc<Duration_clauseContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(0)
    }
    /// Retrieves first TerminalNode corresponding to token LOOP
    /// Returns `None` if there is no child corresponding to token LOOP
    fn LOOP(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(LOOP, 0)
    }
    /// Retrieves first TerminalNode corresponding to token ASYNC
    /// Returns `None` if there is no child corresponding to token ASYNC
    fn ASYNC(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(ASYNC, 0)
    }
    /// Retrieves first TerminalNode corresponding to token LEFT
    /// Returns `None` if there is no child corresponding to token LEFT
    fn LEFT(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(LEFT, 0)
    }
    /// Retrieves first TerminalNode corresponding to token RIGHT
    /// Returns `None` if there is no child corresponding to token RIGHT
    fn RIGHT(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(RIGHT, 0)
    }
}

impl<'input> Turn_stmtContextAttrs<'input> for Turn_stmtContext<'input> {}

impl<'input, I, H> SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input>> + TidAble<'input>,
    H: ErrorStrategy<'input, BaseParserType<'input, I>>,
{
    pub fn turn_stmt(&mut self) -> Result<Rc<Turn_stmtContextAll<'input>>, ANTLRError> {
        let mut recog = self;
        let _parentctx = recog.ctx.take();
        let mut _localctx = Turn_stmtContextExt::new(_parentctx.clone(), recog.base.get_state());
        recog.base.enter_rule(_localctx.clone(), 16, RULE_turn_stmt);
        let mut _localctx: Rc<Turn_stmtContextAll> = _localctx;
        let mut _la: isize = -1;
        let result: Result<(), ANTLRError> = (|| {
            //recog.base.enter_outer_alt(_localctx.clone(), 1);
            recog.base.enter_outer_alt(None, 1);
            {
                recog.base.set_state(146);
                let tmp = recog.base.match_token(IDENT, &mut recog.err_handler)?;
                cast_mut::<_, Turn_stmtContext>(&mut _localctx).target = Some(tmp.clone());

                recog.base.set_state(147);
                recog.base.match_token(DOT, &mut recog.err_handler)?;

                recog.base.set_state(148);
                recog.base.match_token(TURN, &mut recog.err_handler)?;

                recog.base.set_state(150);
                recog.err_handler.sync(&mut recog.base)?;
                match recog.interpreter.adaptive_predict(9, &mut recog.base)? {
                    x if x == 1 => {
                        recog.base.set_state(149);
                        _la = recog.base.input.la(1);
                        if { !(_la == LEFT || _la == RIGHT) } {
                            recog.err_handler.recover_inline(&mut recog.base)?;
                        } else {
                            if recog.base.input.la(1) == TOKEN_EOF {
                                recog.base.matched_eof = true
                            };
                            recog.err_handler.report_match(&mut recog.base);
                            recog.base.consume(&mut recog.err_handler);
                        }
                    }

                    _ => {}
                }
                /*InvokeRule logical_expression*/
                recog.base.set_state(152);
                recog.logical_expression()?;

                recog.base.set_state(154);
                recog.err_handler.sync(&mut recog.base)?;
                _la = recog.base.input.la(1);
                if _la == IN || _la == FOR {
                    {
                        /*InvokeRule duration_clause*/
                        recog.base.set_state(153);
                        recog.duration_clause()?;
                    }
                }

                recog.base.set_state(157);
                recog.err_handler.sync(&mut recog.base)?;
                _la = recog.base.input.la(1);
                if _la == LOOP {
                    {
                        recog.base.set_state(156);
                        recog.base.match_token(LOOP, &mut recog.err_handler)?;
                    }
                }

                recog.base.set_state(160);
                recog.err_handler.sync(&mut recog.base)?;
                _la = recog.base.input.la(1);
                if _la == ASYNC {
                    {
                        recog.base.set_state(159);
                        recog.base.match_token(ASYNC, &mut recog.err_handler)?;
                    }
                }
            }
            Ok(())
        })();
        match result {
            Ok(_) => {}
            Err(e @ ANTLRError::FallThrough(_)) => return Err(e),
            Err(ref re) => {
                //_localctx.exception = re;
                recog.err_handler.report_error(&mut recog.base, re);
                recog.err_handler.recover(&mut recog.base, re)?;
            }
        }
        recog.base.exit_rule();

        Ok(_localctx)
    }
}
//------------------- rotate_stmt ----------------
pub type Rotate_stmtContextAll<'input> = Rotate_stmtContext<'input>;

pub type Rotate_stmtContext<'input> = BaseParserRuleContext<'input, Rotate_stmtContextExt<'input>>;

#[derive(Clone)]
pub struct Rotate_stmtContextExt<'input> {
    pub target: Option<TokenType<'input>>,
    ph: PhantomData<&'input str>,
}

impl<'input> SceneMaxNextGenParserContext<'input> for Rotate_stmtContext<'input> {}

impl<'input, 'a> Listenable<dyn SceneMaxNextGenListener<'input> + 'a>
    for Rotate_stmtContext<'input>
{
    fn enter(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.enter_every_rule(self);
        listener.enter_rotate_stmt(self);
    }
    fn exit(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.exit_rotate_stmt(self);
        listener.exit_every_rule(self);
    }
}

impl<'input, 'a> Visitable<dyn SceneMaxNextGenVisitor<'input> + 'a> for Rotate_stmtContext<'input> {
    fn accept(&self, visitor: &mut (dyn SceneMaxNextGenVisitor<'input> + 'a)) {
        visitor.visit_rotate_stmt(self);
    }
}

impl<'input> CustomRuleContext<'input> for Rotate_stmtContextExt<'input> {
    type TF = LocalTokenFactory<'input>;
    type Ctx = SceneMaxNextGenParserContextType;
    fn get_rule_index(&self) -> usize {
        RULE_rotate_stmt
    }
    //fn type_rule_index() -> usize where Self: Sized { RULE_rotate_stmt }
}
antlr_rust::tid! {Rotate_stmtContextExt<'a>}

impl<'input> Rotate_stmtContextExt<'input> {
    fn new(
        parent: Option<Rc<dyn SceneMaxNextGenParserContext<'input> + 'input>>,
        invoking_state: isize,
    ) -> Rc<Rotate_stmtContextAll<'input>> {
        Rc::new(BaseParserRuleContext::new_parser_ctx(
            parent,
            invoking_state,
            Rotate_stmtContextExt {
                target: None,
                ph: PhantomData,
            },
        ))
    }
}

pub trait Rotate_stmtContextAttrs<'input>:
    SceneMaxNextGenParserContext<'input> + BorrowMut<Rotate_stmtContextExt<'input>>
{
    /// Retrieves first TerminalNode corresponding to token DOT
    /// Returns `None` if there is no child corresponding to token DOT
    fn DOT(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(DOT, 0)
    }
    /// Retrieves first TerminalNode corresponding to token ROTATE
    /// Returns `None` if there is no child corresponding to token ROTATE
    fn ROTATE(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(ROTATE, 0)
    }
    /// Retrieves first TerminalNode corresponding to token LPAREN
    /// Returns `None` if there is no child corresponding to token LPAREN
    fn LPAREN(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(LPAREN, 0)
    }
    fn qualified_name(&self) -> Option<Rc<Qualified_nameContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(0)
    }
    fn logical_expression(&self) -> Option<Rc<Logical_expressionContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(0)
    }
    /// Retrieves first TerminalNode corresponding to token RPAREN
    /// Returns `None` if there is no child corresponding to token RPAREN
    fn RPAREN(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(RPAREN, 0)
    }
    /// Retrieves first TerminalNode corresponding to token IDENT
    /// Returns `None` if there is no child corresponding to token IDENT
    fn IDENT(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(IDENT, 0)
    }
    /// Retrieves first TerminalNode corresponding to token PLUS
    /// Returns `None` if there is no child corresponding to token PLUS
    fn PLUS(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(PLUS, 0)
    }
    /// Retrieves first TerminalNode corresponding to token MINUS
    /// Returns `None` if there is no child corresponding to token MINUS
    fn MINUS(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(MINUS, 0)
    }
    fn duration_clause(&self) -> Option<Rc<Duration_clauseContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(0)
    }
    /// Retrieves first TerminalNode corresponding to token LOOP
    /// Returns `None` if there is no child corresponding to token LOOP
    fn LOOP(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(LOOP, 0)
    }
    /// Retrieves first TerminalNode corresponding to token ASYNC
    /// Returns `None` if there is no child corresponding to token ASYNC
    fn ASYNC(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(ASYNC, 0)
    }
}

impl<'input> Rotate_stmtContextAttrs<'input> for Rotate_stmtContext<'input> {}

impl<'input, I, H> SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input>> + TidAble<'input>,
    H: ErrorStrategy<'input, BaseParserType<'input, I>>,
{
    pub fn rotate_stmt(&mut self) -> Result<Rc<Rotate_stmtContextAll<'input>>, ANTLRError> {
        let mut recog = self;
        let _parentctx = recog.ctx.take();
        let mut _localctx = Rotate_stmtContextExt::new(_parentctx.clone(), recog.base.get_state());
        recog
            .base
            .enter_rule(_localctx.clone(), 18, RULE_rotate_stmt);
        let mut _localctx: Rc<Rotate_stmtContextAll> = _localctx;
        let mut _la: isize = -1;
        let result: Result<(), ANTLRError> = (|| {
            //recog.base.enter_outer_alt(_localctx.clone(), 1);
            recog.base.enter_outer_alt(None, 1);
            {
                recog.base.set_state(162);
                let tmp = recog.base.match_token(IDENT, &mut recog.err_handler)?;
                cast_mut::<_, Rotate_stmtContext>(&mut _localctx).target = Some(tmp.clone());

                recog.base.set_state(163);
                recog.base.match_token(DOT, &mut recog.err_handler)?;

                recog.base.set_state(164);
                recog.base.match_token(ROTATE, &mut recog.err_handler)?;

                recog.base.set_state(165);
                recog.base.match_token(LPAREN, &mut recog.err_handler)?;

                /*InvokeRule qualified_name*/
                recog.base.set_state(166);
                recog.qualified_name()?;

                recog.base.set_state(167);
                _la = recog.base.input.la(1);
                if { !(_la == PLUS || _la == MINUS) } {
                    recog.err_handler.recover_inline(&mut recog.base)?;
                } else {
                    if recog.base.input.la(1) == TOKEN_EOF {
                        recog.base.matched_eof = true
                    };
                    recog.err_handler.report_match(&mut recog.base);
                    recog.base.consume(&mut recog.err_handler);
                }
                /*InvokeRule logical_expression*/
                recog.base.set_state(168);
                recog.logical_expression()?;

                recog.base.set_state(169);
                recog.base.match_token(RPAREN, &mut recog.err_handler)?;

                recog.base.set_state(171);
                recog.err_handler.sync(&mut recog.base)?;
                _la = recog.base.input.la(1);
                if _la == IN || _la == FOR {
                    {
                        /*InvokeRule duration_clause*/
                        recog.base.set_state(170);
                        recog.duration_clause()?;
                    }
                }

                recog.base.set_state(174);
                recog.err_handler.sync(&mut recog.base)?;
                _la = recog.base.input.la(1);
                if _la == LOOP {
                    {
                        recog.base.set_state(173);
                        recog.base.match_token(LOOP, &mut recog.err_handler)?;
                    }
                }

                recog.base.set_state(177);
                recog.err_handler.sync(&mut recog.base)?;
                _la = recog.base.input.la(1);
                if _la == ASYNC {
                    {
                        recog.base.set_state(176);
                        recog.base.match_token(ASYNC, &mut recog.err_handler)?;
                    }
                }
            }
            Ok(())
        })();
        match result {
            Ok(_) => {}
            Err(e @ ANTLRError::FallThrough(_)) => return Err(e),
            Err(ref re) => {
                //_localctx.exception = re;
                recog.err_handler.report_error(&mut recog.base, re);
                recog.err_handler.recover(&mut recog.base, re)?;
            }
        }
        recog.base.exit_rule();

        Ok(_localctx)
    }
}
//------------------- move_direction ----------------
pub type Move_directionContextAll<'input> = Move_directionContext<'input>;

pub type Move_directionContext<'input> =
    BaseParserRuleContext<'input, Move_directionContextExt<'input>>;

#[derive(Clone)]
pub struct Move_directionContextExt<'input> {
    ph: PhantomData<&'input str>,
}

impl<'input> SceneMaxNextGenParserContext<'input> for Move_directionContext<'input> {}

impl<'input, 'a> Listenable<dyn SceneMaxNextGenListener<'input> + 'a>
    for Move_directionContext<'input>
{
    fn enter(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.enter_every_rule(self);
        listener.enter_move_direction(self);
    }
    fn exit(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.exit_move_direction(self);
        listener.exit_every_rule(self);
    }
}

impl<'input, 'a> Visitable<dyn SceneMaxNextGenVisitor<'input> + 'a>
    for Move_directionContext<'input>
{
    fn accept(&self, visitor: &mut (dyn SceneMaxNextGenVisitor<'input> + 'a)) {
        visitor.visit_move_direction(self);
    }
}

impl<'input> CustomRuleContext<'input> for Move_directionContextExt<'input> {
    type TF = LocalTokenFactory<'input>;
    type Ctx = SceneMaxNextGenParserContextType;
    fn get_rule_index(&self) -> usize {
        RULE_move_direction
    }
    //fn type_rule_index() -> usize where Self: Sized { RULE_move_direction }
}
antlr_rust::tid! {Move_directionContextExt<'a>}

impl<'input> Move_directionContextExt<'input> {
    fn new(
        parent: Option<Rc<dyn SceneMaxNextGenParserContext<'input> + 'input>>,
        invoking_state: isize,
    ) -> Rc<Move_directionContextAll<'input>> {
        Rc::new(BaseParserRuleContext::new_parser_ctx(
            parent,
            invoking_state,
            Move_directionContextExt { ph: PhantomData },
        ))
    }
}

pub trait Move_directionContextAttrs<'input>:
    SceneMaxNextGenParserContext<'input> + BorrowMut<Move_directionContextExt<'input>>
{
    /// Retrieves first TerminalNode corresponding to token FORWARD
    /// Returns `None` if there is no child corresponding to token FORWARD
    fn FORWARD(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(FORWARD, 0)
    }
    /// Retrieves first TerminalNode corresponding to token BACKWARD
    /// Returns `None` if there is no child corresponding to token BACKWARD
    fn BACKWARD(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(BACKWARD, 0)
    }
    /// Retrieves first TerminalNode corresponding to token BACK
    /// Returns `None` if there is no child corresponding to token BACK
    fn BACK(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(BACK, 0)
    }
    /// Retrieves first TerminalNode corresponding to token LEFT
    /// Returns `None` if there is no child corresponding to token LEFT
    fn LEFT(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(LEFT, 0)
    }
    /// Retrieves first TerminalNode corresponding to token RIGHT
    /// Returns `None` if there is no child corresponding to token RIGHT
    fn RIGHT(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(RIGHT, 0)
    }
    /// Retrieves first TerminalNode corresponding to token UP
    /// Returns `None` if there is no child corresponding to token UP
    fn UP(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(UP, 0)
    }
    /// Retrieves first TerminalNode corresponding to token DOWN
    /// Returns `None` if there is no child corresponding to token DOWN
    fn DOWN(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(DOWN, 0)
    }
}

impl<'input> Move_directionContextAttrs<'input> for Move_directionContext<'input> {}

impl<'input, I, H> SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input>> + TidAble<'input>,
    H: ErrorStrategy<'input, BaseParserType<'input, I>>,
{
    pub fn move_direction(&mut self) -> Result<Rc<Move_directionContextAll<'input>>, ANTLRError> {
        let mut recog = self;
        let _parentctx = recog.ctx.take();
        let mut _localctx =
            Move_directionContextExt::new(_parentctx.clone(), recog.base.get_state());
        recog
            .base
            .enter_rule(_localctx.clone(), 20, RULE_move_direction);
        let mut _localctx: Rc<Move_directionContextAll> = _localctx;
        let mut _la: isize = -1;
        let result: Result<(), ANTLRError> = (|| {
            //recog.base.enter_outer_alt(_localctx.clone(), 1);
            recog.base.enter_outer_alt(None, 1);
            {
                recog.base.set_state(179);
                _la = recog.base.input.la(1);
                if {
                    !(((_la) & !0x3f) == 0
                        && ((1usize << _la)
                            & ((1usize << FORWARD)
                                | (1usize << BACKWARD)
                                | (1usize << BACK)
                                | (1usize << LEFT)
                                | (1usize << RIGHT)
                                | (1usize << UP)
                                | (1usize << DOWN)))
                            != 0)
                } {
                    recog.err_handler.recover_inline(&mut recog.base)?;
                } else {
                    if recog.base.input.la(1) == TOKEN_EOF {
                        recog.base.matched_eof = true
                    };
                    recog.err_handler.report_match(&mut recog.base);
                    recog.base.consume(&mut recog.err_handler);
                }
            }
            Ok(())
        })();
        match result {
            Ok(_) => {}
            Err(e @ ANTLRError::FallThrough(_)) => return Err(e),
            Err(ref re) => {
                //_localctx.exception = re;
                recog.err_handler.report_error(&mut recog.base, re);
                recog.err_handler.recover(&mut recog.base, re)?;
            }
        }
        recog.base.exit_rule();

        Ok(_localctx)
    }
}
//------------------- duration_clause ----------------
pub type Duration_clauseContextAll<'input> = Duration_clauseContext<'input>;

pub type Duration_clauseContext<'input> =
    BaseParserRuleContext<'input, Duration_clauseContextExt<'input>>;

#[derive(Clone)]
pub struct Duration_clauseContextExt<'input> {
    ph: PhantomData<&'input str>,
}

impl<'input> SceneMaxNextGenParserContext<'input> for Duration_clauseContext<'input> {}

impl<'input, 'a> Listenable<dyn SceneMaxNextGenListener<'input> + 'a>
    for Duration_clauseContext<'input>
{
    fn enter(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.enter_every_rule(self);
        listener.enter_duration_clause(self);
    }
    fn exit(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.exit_duration_clause(self);
        listener.exit_every_rule(self);
    }
}

impl<'input, 'a> Visitable<dyn SceneMaxNextGenVisitor<'input> + 'a>
    for Duration_clauseContext<'input>
{
    fn accept(&self, visitor: &mut (dyn SceneMaxNextGenVisitor<'input> + 'a)) {
        visitor.visit_duration_clause(self);
    }
}

impl<'input> CustomRuleContext<'input> for Duration_clauseContextExt<'input> {
    type TF = LocalTokenFactory<'input>;
    type Ctx = SceneMaxNextGenParserContextType;
    fn get_rule_index(&self) -> usize {
        RULE_duration_clause
    }
    //fn type_rule_index() -> usize where Self: Sized { RULE_duration_clause }
}
antlr_rust::tid! {Duration_clauseContextExt<'a>}

impl<'input> Duration_clauseContextExt<'input> {
    fn new(
        parent: Option<Rc<dyn SceneMaxNextGenParserContext<'input> + 'input>>,
        invoking_state: isize,
    ) -> Rc<Duration_clauseContextAll<'input>> {
        Rc::new(BaseParserRuleContext::new_parser_ctx(
            parent,
            invoking_state,
            Duration_clauseContextExt { ph: PhantomData },
        ))
    }
}

pub trait Duration_clauseContextAttrs<'input>:
    SceneMaxNextGenParserContext<'input> + BorrowMut<Duration_clauseContextExt<'input>>
{
    fn logical_expression(&self) -> Option<Rc<Logical_expressionContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(0)
    }
    /// Retrieves first TerminalNode corresponding to token SECONDS
    /// Returns `None` if there is no child corresponding to token SECONDS
    fn SECONDS(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(SECONDS, 0)
    }
    /// Retrieves first TerminalNode corresponding to token IN
    /// Returns `None` if there is no child corresponding to token IN
    fn IN(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(IN, 0)
    }
    /// Retrieves first TerminalNode corresponding to token FOR
    /// Returns `None` if there is no child corresponding to token FOR
    fn FOR(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(FOR, 0)
    }
}

impl<'input> Duration_clauseContextAttrs<'input> for Duration_clauseContext<'input> {}

impl<'input, I, H> SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input>> + TidAble<'input>,
    H: ErrorStrategy<'input, BaseParserType<'input, I>>,
{
    pub fn duration_clause(&mut self) -> Result<Rc<Duration_clauseContextAll<'input>>, ANTLRError> {
        let mut recog = self;
        let _parentctx = recog.ctx.take();
        let mut _localctx =
            Duration_clauseContextExt::new(_parentctx.clone(), recog.base.get_state());
        recog
            .base
            .enter_rule(_localctx.clone(), 22, RULE_duration_clause);
        let mut _localctx: Rc<Duration_clauseContextAll> = _localctx;
        let mut _la: isize = -1;
        let result: Result<(), ANTLRError> = (|| {
            //recog.base.enter_outer_alt(_localctx.clone(), 1);
            recog.base.enter_outer_alt(None, 1);
            {
                recog.base.set_state(181);
                _la = recog.base.input.la(1);
                if { !(_la == IN || _la == FOR) } {
                    recog.err_handler.recover_inline(&mut recog.base)?;
                } else {
                    if recog.base.input.la(1) == TOKEN_EOF {
                        recog.base.matched_eof = true
                    };
                    recog.err_handler.report_match(&mut recog.base);
                    recog.base.consume(&mut recog.err_handler);
                }
                /*InvokeRule logical_expression*/
                recog.base.set_state(182);
                recog.logical_expression()?;

                recog.base.set_state(183);
                recog.base.match_token(SECONDS, &mut recog.err_handler)?;
            }
            Ok(())
        })();
        match result {
            Ok(_) => {}
            Err(e @ ANTLRError::FallThrough(_)) => return Err(e),
            Err(ref re) => {
                //_localctx.exception = re;
                recog.err_handler.report_error(&mut recog.base, re);
                recog.err_handler.recover(&mut recog.base, re)?;
            }
        }
        recog.base.exit_rule();

        Ok(_localctx)
    }
}
//------------------- run_function_stmt ----------------
pub type Run_function_stmtContextAll<'input> = Run_function_stmtContext<'input>;

pub type Run_function_stmtContext<'input> =
    BaseParserRuleContext<'input, Run_function_stmtContextExt<'input>>;

#[derive(Clone)]
pub struct Run_function_stmtContextExt<'input> {
    ph: PhantomData<&'input str>,
}

impl<'input> SceneMaxNextGenParserContext<'input> for Run_function_stmtContext<'input> {}

impl<'input, 'a> Listenable<dyn SceneMaxNextGenListener<'input> + 'a>
    for Run_function_stmtContext<'input>
{
    fn enter(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.enter_every_rule(self);
        listener.enter_run_function_stmt(self);
    }
    fn exit(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.exit_run_function_stmt(self);
        listener.exit_every_rule(self);
    }
}

impl<'input, 'a> Visitable<dyn SceneMaxNextGenVisitor<'input> + 'a>
    for Run_function_stmtContext<'input>
{
    fn accept(&self, visitor: &mut (dyn SceneMaxNextGenVisitor<'input> + 'a)) {
        visitor.visit_run_function_stmt(self);
    }
}

impl<'input> CustomRuleContext<'input> for Run_function_stmtContextExt<'input> {
    type TF = LocalTokenFactory<'input>;
    type Ctx = SceneMaxNextGenParserContextType;
    fn get_rule_index(&self) -> usize {
        RULE_run_function_stmt
    }
    //fn type_rule_index() -> usize where Self: Sized { RULE_run_function_stmt }
}
antlr_rust::tid! {Run_function_stmtContextExt<'a>}

impl<'input> Run_function_stmtContextExt<'input> {
    fn new(
        parent: Option<Rc<dyn SceneMaxNextGenParserContext<'input> + 'input>>,
        invoking_state: isize,
    ) -> Rc<Run_function_stmtContextAll<'input>> {
        Rc::new(BaseParserRuleContext::new_parser_ctx(
            parent,
            invoking_state,
            Run_function_stmtContextExt { ph: PhantomData },
        ))
    }
}

pub trait Run_function_stmtContextAttrs<'input>:
    SceneMaxNextGenParserContext<'input> + BorrowMut<Run_function_stmtContextExt<'input>>
{
    /// Retrieves first TerminalNode corresponding to token RUN
    /// Returns `None` if there is no child corresponding to token RUN
    fn RUN(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(RUN, 0)
    }
    fn function_call(&self) -> Option<Rc<Function_callContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(0)
    }
    /// Retrieves first TerminalNode corresponding to token ASYNC
    /// Returns `None` if there is no child corresponding to token ASYNC
    fn ASYNC(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(ASYNC, 0)
    }
}

impl<'input> Run_function_stmtContextAttrs<'input> for Run_function_stmtContext<'input> {}

impl<'input, I, H> SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input>> + TidAble<'input>,
    H: ErrorStrategy<'input, BaseParserType<'input, I>>,
{
    pub fn run_function_stmt(
        &mut self,
    ) -> Result<Rc<Run_function_stmtContextAll<'input>>, ANTLRError> {
        let mut recog = self;
        let _parentctx = recog.ctx.take();
        let mut _localctx =
            Run_function_stmtContextExt::new(_parentctx.clone(), recog.base.get_state());
        recog
            .base
            .enter_rule(_localctx.clone(), 24, RULE_run_function_stmt);
        let mut _localctx: Rc<Run_function_stmtContextAll> = _localctx;
        let mut _la: isize = -1;
        let result: Result<(), ANTLRError> = (|| {
            //recog.base.enter_outer_alt(_localctx.clone(), 1);
            recog.base.enter_outer_alt(None, 1);
            {
                recog.base.set_state(185);
                recog.base.match_token(RUN, &mut recog.err_handler)?;

                /*InvokeRule function_call*/
                recog.base.set_state(186);
                recog.function_call()?;

                recog.base.set_state(188);
                recog.err_handler.sync(&mut recog.base)?;
                _la = recog.base.input.la(1);
                if _la == ASYNC {
                    {
                        recog.base.set_state(187);
                        recog.base.match_token(ASYNC, &mut recog.err_handler)?;
                    }
                }
            }
            Ok(())
        })();
        match result {
            Ok(_) => {}
            Err(e @ ANTLRError::FallThrough(_)) => return Err(e),
            Err(ref re) => {
                //_localctx.exception = re;
                recog.err_handler.report_error(&mut recog.base, re);
                recog.err_handler.recover(&mut recog.base, re)?;
            }
        }
        recog.base.exit_rule();

        Ok(_localctx)
    }
}
//------------------- run_every_stmt ----------------
pub type Run_every_stmtContextAll<'input> = Run_every_stmtContext<'input>;

pub type Run_every_stmtContext<'input> =
    BaseParserRuleContext<'input, Run_every_stmtContextExt<'input>>;

#[derive(Clone)]
pub struct Run_every_stmtContextExt<'input> {
    ph: PhantomData<&'input str>,
}

impl<'input> SceneMaxNextGenParserContext<'input> for Run_every_stmtContext<'input> {}

impl<'input, 'a> Listenable<dyn SceneMaxNextGenListener<'input> + 'a>
    for Run_every_stmtContext<'input>
{
    fn enter(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.enter_every_rule(self);
        listener.enter_run_every_stmt(self);
    }
    fn exit(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.exit_run_every_stmt(self);
        listener.exit_every_rule(self);
    }
}

impl<'input, 'a> Visitable<dyn SceneMaxNextGenVisitor<'input> + 'a>
    for Run_every_stmtContext<'input>
{
    fn accept(&self, visitor: &mut (dyn SceneMaxNextGenVisitor<'input> + 'a)) {
        visitor.visit_run_every_stmt(self);
    }
}

impl<'input> CustomRuleContext<'input> for Run_every_stmtContextExt<'input> {
    type TF = LocalTokenFactory<'input>;
    type Ctx = SceneMaxNextGenParserContextType;
    fn get_rule_index(&self) -> usize {
        RULE_run_every_stmt
    }
    //fn type_rule_index() -> usize where Self: Sized { RULE_run_every_stmt }
}
antlr_rust::tid! {Run_every_stmtContextExt<'a>}

impl<'input> Run_every_stmtContextExt<'input> {
    fn new(
        parent: Option<Rc<dyn SceneMaxNextGenParserContext<'input> + 'input>>,
        invoking_state: isize,
    ) -> Rc<Run_every_stmtContextAll<'input>> {
        Rc::new(BaseParserRuleContext::new_parser_ctx(
            parent,
            invoking_state,
            Run_every_stmtContextExt { ph: PhantomData },
        ))
    }
}

pub trait Run_every_stmtContextAttrs<'input>:
    SceneMaxNextGenParserContext<'input> + BorrowMut<Run_every_stmtContextExt<'input>>
{
    /// Retrieves first TerminalNode corresponding to token RUN
    /// Returns `None` if there is no child corresponding to token RUN
    fn RUN(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(RUN, 0)
    }
    fn function_call(&self) -> Option<Rc<Function_callContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(0)
    }
    /// Retrieves first TerminalNode corresponding to token EVERY
    /// Returns `None` if there is no child corresponding to token EVERY
    fn EVERY(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(EVERY, 0)
    }
    fn logical_expression(&self) -> Option<Rc<Logical_expressionContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(0)
    }
    /// Retrieves first TerminalNode corresponding to token SECONDS
    /// Returns `None` if there is no child corresponding to token SECONDS
    fn SECONDS(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(SECONDS, 0)
    }
}

impl<'input> Run_every_stmtContextAttrs<'input> for Run_every_stmtContext<'input> {}

impl<'input, I, H> SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input>> + TidAble<'input>,
    H: ErrorStrategy<'input, BaseParserType<'input, I>>,
{
    pub fn run_every_stmt(&mut self) -> Result<Rc<Run_every_stmtContextAll<'input>>, ANTLRError> {
        let mut recog = self;
        let _parentctx = recog.ctx.take();
        let mut _localctx =
            Run_every_stmtContextExt::new(_parentctx.clone(), recog.base.get_state());
        recog
            .base
            .enter_rule(_localctx.clone(), 26, RULE_run_every_stmt);
        let mut _localctx: Rc<Run_every_stmtContextAll> = _localctx;
        let result: Result<(), ANTLRError> = (|| {
            //recog.base.enter_outer_alt(_localctx.clone(), 1);
            recog.base.enter_outer_alt(None, 1);
            {
                recog.base.set_state(190);
                recog.base.match_token(RUN, &mut recog.err_handler)?;

                /*InvokeRule function_call*/
                recog.base.set_state(191);
                recog.function_call()?;

                recog.base.set_state(192);
                recog.base.match_token(EVERY, &mut recog.err_handler)?;

                /*InvokeRule logical_expression*/
                recog.base.set_state(193);
                recog.logical_expression()?;

                recog.base.set_state(194);
                recog.base.match_token(SECONDS, &mut recog.err_handler)?;
            }
            Ok(())
        })();
        match result {
            Ok(_) => {}
            Err(e @ ANTLRError::FallThrough(_)) => return Err(e),
            Err(ref re) => {
                //_localctx.exception = re;
                recog.err_handler.report_error(&mut recog.base, re);
                recog.err_handler.recover(&mut recog.base, re)?;
            }
        }
        recog.base.exit_rule();

        Ok(_localctx)
    }
}
//------------------- function_call ----------------
pub type Function_callContextAll<'input> = Function_callContext<'input>;

pub type Function_callContext<'input> =
    BaseParserRuleContext<'input, Function_callContextExt<'input>>;

#[derive(Clone)]
pub struct Function_callContextExt<'input> {
    ph: PhantomData<&'input str>,
}

impl<'input> SceneMaxNextGenParserContext<'input> for Function_callContext<'input> {}

impl<'input, 'a> Listenable<dyn SceneMaxNextGenListener<'input> + 'a>
    for Function_callContext<'input>
{
    fn enter(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.enter_every_rule(self);
        listener.enter_function_call(self);
    }
    fn exit(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.exit_function_call(self);
        listener.exit_every_rule(self);
    }
}

impl<'input, 'a> Visitable<dyn SceneMaxNextGenVisitor<'input> + 'a>
    for Function_callContext<'input>
{
    fn accept(&self, visitor: &mut (dyn SceneMaxNextGenVisitor<'input> + 'a)) {
        visitor.visit_function_call(self);
    }
}

impl<'input> CustomRuleContext<'input> for Function_callContextExt<'input> {
    type TF = LocalTokenFactory<'input>;
    type Ctx = SceneMaxNextGenParserContextType;
    fn get_rule_index(&self) -> usize {
        RULE_function_call
    }
    //fn type_rule_index() -> usize where Self: Sized { RULE_function_call }
}
antlr_rust::tid! {Function_callContextExt<'a>}

impl<'input> Function_callContextExt<'input> {
    fn new(
        parent: Option<Rc<dyn SceneMaxNextGenParserContext<'input> + 'input>>,
        invoking_state: isize,
    ) -> Rc<Function_callContextAll<'input>> {
        Rc::new(BaseParserRuleContext::new_parser_ctx(
            parent,
            invoking_state,
            Function_callContextExt { ph: PhantomData },
        ))
    }
}

pub trait Function_callContextAttrs<'input>:
    SceneMaxNextGenParserContext<'input> + BorrowMut<Function_callContextExt<'input>>
{
    fn qualified_name(&self) -> Option<Rc<Qualified_nameContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(0)
    }
    /// Retrieves first TerminalNode corresponding to token LPAREN
    /// Returns `None` if there is no child corresponding to token LPAREN
    fn LPAREN(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(LPAREN, 0)
    }
    /// Retrieves first TerminalNode corresponding to token RPAREN
    /// Returns `None` if there is no child corresponding to token RPAREN
    fn RPAREN(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(RPAREN, 0)
    }
    fn logical_expression_all(&self) -> Vec<Rc<Logical_expressionContextAll<'input>>>
    where
        Self: Sized,
    {
        self.children_of_type()
    }
    fn logical_expression(&self, i: usize) -> Option<Rc<Logical_expressionContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(i)
    }
    /// Retrieves all `TerminalNode`s corresponding to token COMMA in current rule
    fn COMMA_all(&self) -> Vec<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.children_of_type()
    }
    /// Retrieves 'i's TerminalNode corresponding to token COMMA, starting from 0.
    /// Returns `None` if number of children corresponding to token COMMA is less or equal than `i`.
    fn COMMA(&self, i: usize) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(COMMA, i)
    }
}

impl<'input> Function_callContextAttrs<'input> for Function_callContext<'input> {}

impl<'input, I, H> SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input>> + TidAble<'input>,
    H: ErrorStrategy<'input, BaseParserType<'input, I>>,
{
    pub fn function_call(&mut self) -> Result<Rc<Function_callContextAll<'input>>, ANTLRError> {
        let mut recog = self;
        let _parentctx = recog.ctx.take();
        let mut _localctx =
            Function_callContextExt::new(_parentctx.clone(), recog.base.get_state());
        recog
            .base
            .enter_rule(_localctx.clone(), 28, RULE_function_call);
        let mut _localctx: Rc<Function_callContextAll> = _localctx;
        let mut _la: isize = -1;
        let result: Result<(), ANTLRError> = (|| {
            recog.base.set_state(211);
            recog.err_handler.sync(&mut recog.base)?;
            match recog.interpreter.adaptive_predict(19, &mut recog.base)? {
                1 => {
                    //recog.base.enter_outer_alt(_localctx.clone(), 1);
                    recog.base.enter_outer_alt(None, 1);
                    {
                        /*InvokeRule qualified_name*/
                        recog.base.set_state(196);
                        recog.qualified_name()?;

                        recog.base.set_state(197);
                        recog.base.match_token(LPAREN, &mut recog.err_handler)?;

                        recog.base.set_state(206);
                        recog.err_handler.sync(&mut recog.base)?;
                        _la = recog.base.input.la(1);
                        if (((_la) & !0x3f) == 0
                            && ((1usize << _la)
                                & ((1usize << LOOP)
                                    | (1usize << ADD)
                                    | (1usize << CODE)
                                    | (1usize << AT)
                                    | (1usize << SPEED)
                                    | (1usize << OF)
                                    | (1usize << MOVE)
                                    | (1usize << FORWARD)
                                    | (1usize << BACKWARD)
                                    | (1usize << BACK)
                                    | (1usize << LEFT)
                                    | (1usize << RIGHT)
                                    | (1usize << UP)
                                    | (1usize << DOWN)
                                    | (1usize << IN)
                                    | (1usize << FOR)
                                    | (1usize << SECONDS)
                                    | (1usize << TO)
                                    | (1usize << TURN)
                                    | (1usize << ROTATE)
                                    | (1usize << RUN)
                                    | (1usize << EVERY)
                                    | (1usize << ASYNC)
                                    | (1usize << ANIMATION)
                                    | (1usize << PLAY)
                                    | (1usize << FRAME)
                                    | (1usize << CHARACTER)
                                    | (1usize << JUMP)
                                    | (1usize << PHYSICS)
                                    | (1usize << IMPULSE)))
                                != 0)
                            || (((_la - 32) & !0x3f) == 0
                                && ((1usize << (_la - 32))
                                    & ((1usize << (WHEN - 32))
                                        | (1usize << (TRUE - 32))
                                        | (1usize << (FALSE - 32))
                                        | (1usize << (LPAREN - 32))
                                        | (1usize << (PLUS - 32))
                                        | (1usize << (MINUS - 32))
                                        | (1usize << (NOT - 32))
                                        | (1usize << (DECIMAL - 32))
                                        | (1usize << (IDENT - 32))
                                        | (1usize << (QUOTED_STRING - 32))))
                                    != 0)
                        {
                            {
                                /*InvokeRule logical_expression*/
                                recog.base.set_state(198);
                                recog.logical_expression()?;

                                recog.base.set_state(203);
                                recog.err_handler.sync(&mut recog.base)?;
                                _la = recog.base.input.la(1);
                                while _la == COMMA {
                                    {
                                        {
                                            recog.base.set_state(199);
                                            recog
                                                .base
                                                .match_token(COMMA, &mut recog.err_handler)?;

                                            /*InvokeRule logical_expression*/
                                            recog.base.set_state(200);
                                            recog.logical_expression()?;
                                        }
                                    }
                                    recog.base.set_state(205);
                                    recog.err_handler.sync(&mut recog.base)?;
                                    _la = recog.base.input.la(1);
                                }
                            }
                        }

                        recog.base.set_state(208);
                        recog.base.match_token(RPAREN, &mut recog.err_handler)?;
                    }
                }
                2 => {
                    //recog.base.enter_outer_alt(_localctx.clone(), 2);
                    recog.base.enter_outer_alt(None, 2);
                    {
                        /*InvokeRule qualified_name*/
                        recog.base.set_state(210);
                        recog.qualified_name()?;
                    }
                }

                _ => {}
            }
            Ok(())
        })();
        match result {
            Ok(_) => {}
            Err(e @ ANTLRError::FallThrough(_)) => return Err(e),
            Err(ref re) => {
                //_localctx.exception = re;
                recog.err_handler.report_error(&mut recog.base, re);
                recog.err_handler.recover(&mut recog.base, re)?;
            }
        }
        recog.base.exit_rule();

        Ok(_localctx)
    }
}
//------------------- animation_speed_stmt ----------------
pub type Animation_speed_stmtContextAll<'input> = Animation_speed_stmtContext<'input>;

pub type Animation_speed_stmtContext<'input> =
    BaseParserRuleContext<'input, Animation_speed_stmtContextExt<'input>>;

#[derive(Clone)]
pub struct Animation_speed_stmtContextExt<'input> {
    pub target: Option<TokenType<'input>>,
    ph: PhantomData<&'input str>,
}

impl<'input> SceneMaxNextGenParserContext<'input> for Animation_speed_stmtContext<'input> {}

impl<'input, 'a> Listenable<dyn SceneMaxNextGenListener<'input> + 'a>
    for Animation_speed_stmtContext<'input>
{
    fn enter(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.enter_every_rule(self);
        listener.enter_animation_speed_stmt(self);
    }
    fn exit(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.exit_animation_speed_stmt(self);
        listener.exit_every_rule(self);
    }
}

impl<'input, 'a> Visitable<dyn SceneMaxNextGenVisitor<'input> + 'a>
    for Animation_speed_stmtContext<'input>
{
    fn accept(&self, visitor: &mut (dyn SceneMaxNextGenVisitor<'input> + 'a)) {
        visitor.visit_animation_speed_stmt(self);
    }
}

impl<'input> CustomRuleContext<'input> for Animation_speed_stmtContextExt<'input> {
    type TF = LocalTokenFactory<'input>;
    type Ctx = SceneMaxNextGenParserContextType;
    fn get_rule_index(&self) -> usize {
        RULE_animation_speed_stmt
    }
    //fn type_rule_index() -> usize where Self: Sized { RULE_animation_speed_stmt }
}
antlr_rust::tid! {Animation_speed_stmtContextExt<'a>}

impl<'input> Animation_speed_stmtContextExt<'input> {
    fn new(
        parent: Option<Rc<dyn SceneMaxNextGenParserContext<'input> + 'input>>,
        invoking_state: isize,
    ) -> Rc<Animation_speed_stmtContextAll<'input>> {
        Rc::new(BaseParserRuleContext::new_parser_ctx(
            parent,
            invoking_state,
            Animation_speed_stmtContextExt {
                target: None,
                ph: PhantomData,
            },
        ))
    }
}

pub trait Animation_speed_stmtContextAttrs<'input>:
    SceneMaxNextGenParserContext<'input> + BorrowMut<Animation_speed_stmtContextExt<'input>>
{
    /// Retrieves first TerminalNode corresponding to token DOT
    /// Returns `None` if there is no child corresponding to token DOT
    fn DOT(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(DOT, 0)
    }
    /// Retrieves first TerminalNode corresponding to token ANIMATION
    /// Returns `None` if there is no child corresponding to token ANIMATION
    fn ANIMATION(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(ANIMATION, 0)
    }
    /// Retrieves first TerminalNode corresponding to token SPEED
    /// Returns `None` if there is no child corresponding to token SPEED
    fn SPEED(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(SPEED, 0)
    }
    fn logical_expression_all(&self) -> Vec<Rc<Logical_expressionContextAll<'input>>>
    where
        Self: Sized,
    {
        self.children_of_type()
    }
    fn logical_expression(&self, i: usize) -> Option<Rc<Logical_expressionContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(i)
    }
    /// Retrieves first TerminalNode corresponding to token IDENT
    /// Returns `None` if there is no child corresponding to token IDENT
    fn IDENT(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(IDENT, 0)
    }
    /// Retrieves first TerminalNode corresponding to token FOR
    /// Returns `None` if there is no child corresponding to token FOR
    fn FOR(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(FOR, 0)
    }
    /// Retrieves first TerminalNode corresponding to token SECONDS
    /// Returns `None` if there is no child corresponding to token SECONDS
    fn SECONDS(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(SECONDS, 0)
    }
    /// Retrieves first TerminalNode corresponding to token WHEN
    /// Returns `None` if there is no child corresponding to token WHEN
    fn WHEN(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(WHEN, 0)
    }
}

impl<'input> Animation_speed_stmtContextAttrs<'input> for Animation_speed_stmtContext<'input> {}

impl<'input, I, H> SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input>> + TidAble<'input>,
    H: ErrorStrategy<'input, BaseParserType<'input, I>>,
{
    pub fn animation_speed_stmt(
        &mut self,
    ) -> Result<Rc<Animation_speed_stmtContextAll<'input>>, ANTLRError> {
        let mut recog = self;
        let _parentctx = recog.ctx.take();
        let mut _localctx =
            Animation_speed_stmtContextExt::new(_parentctx.clone(), recog.base.get_state());
        recog
            .base
            .enter_rule(_localctx.clone(), 30, RULE_animation_speed_stmt);
        let mut _localctx: Rc<Animation_speed_stmtContextAll> = _localctx;
        let mut _la: isize = -1;
        let result: Result<(), ANTLRError> = (|| {
            //recog.base.enter_outer_alt(_localctx.clone(), 1);
            recog.base.enter_outer_alt(None, 1);
            {
                recog.base.set_state(213);
                let tmp = recog.base.match_token(IDENT, &mut recog.err_handler)?;
                cast_mut::<_, Animation_speed_stmtContext>(&mut _localctx).target =
                    Some(tmp.clone());

                recog.base.set_state(214);
                recog.base.match_token(DOT, &mut recog.err_handler)?;

                recog.base.set_state(215);
                recog.base.match_token(ANIMATION, &mut recog.err_handler)?;

                recog.base.set_state(216);
                recog.base.match_token(SPEED, &mut recog.err_handler)?;

                /*InvokeRule logical_expression*/
                recog.base.set_state(217);
                recog.logical_expression()?;

                recog.base.set_state(222);
                recog.err_handler.sync(&mut recog.base)?;
                _la = recog.base.input.la(1);
                if _la == FOR {
                    {
                        recog.base.set_state(218);
                        recog.base.match_token(FOR, &mut recog.err_handler)?;

                        /*InvokeRule logical_expression*/
                        recog.base.set_state(219);
                        recog.logical_expression()?;

                        recog.base.set_state(220);
                        recog.base.match_token(SECONDS, &mut recog.err_handler)?;
                    }
                }

                recog.base.set_state(226);
                recog.err_handler.sync(&mut recog.base)?;
                _la = recog.base.input.la(1);
                if _la == WHEN {
                    {
                        recog.base.set_state(224);
                        recog.base.match_token(WHEN, &mut recog.err_handler)?;

                        /*InvokeRule logical_expression*/
                        recog.base.set_state(225);
                        recog.logical_expression()?;
                    }
                }
            }
            Ok(())
        })();
        match result {
            Ok(_) => {}
            Err(e @ ANTLRError::FallThrough(_)) => return Err(e),
            Err(ref re) => {
                //_localctx.exception = re;
                recog.err_handler.report_error(&mut recog.base, re);
                recog.err_handler.recover(&mut recog.base, re)?;
            }
        }
        recog.base.exit_rule();

        Ok(_localctx)
    }
}
//------------------- sprite_play_stmt ----------------
pub type Sprite_play_stmtContextAll<'input> = Sprite_play_stmtContext<'input>;

pub type Sprite_play_stmtContext<'input> =
    BaseParserRuleContext<'input, Sprite_play_stmtContextExt<'input>>;

#[derive(Clone)]
pub struct Sprite_play_stmtContextExt<'input> {
    pub target: Option<TokenType<'input>>,
    ph: PhantomData<&'input str>,
}

impl<'input> SceneMaxNextGenParserContext<'input> for Sprite_play_stmtContext<'input> {}

impl<'input, 'a> Listenable<dyn SceneMaxNextGenListener<'input> + 'a>
    for Sprite_play_stmtContext<'input>
{
    fn enter(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.enter_every_rule(self);
        listener.enter_sprite_play_stmt(self);
    }
    fn exit(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.exit_sprite_play_stmt(self);
        listener.exit_every_rule(self);
    }
}

impl<'input, 'a> Visitable<dyn SceneMaxNextGenVisitor<'input> + 'a>
    for Sprite_play_stmtContext<'input>
{
    fn accept(&self, visitor: &mut (dyn SceneMaxNextGenVisitor<'input> + 'a)) {
        visitor.visit_sprite_play_stmt(self);
    }
}

impl<'input> CustomRuleContext<'input> for Sprite_play_stmtContextExt<'input> {
    type TF = LocalTokenFactory<'input>;
    type Ctx = SceneMaxNextGenParserContextType;
    fn get_rule_index(&self) -> usize {
        RULE_sprite_play_stmt
    }
    //fn type_rule_index() -> usize where Self: Sized { RULE_sprite_play_stmt }
}
antlr_rust::tid! {Sprite_play_stmtContextExt<'a>}

impl<'input> Sprite_play_stmtContextExt<'input> {
    fn new(
        parent: Option<Rc<dyn SceneMaxNextGenParserContext<'input> + 'input>>,
        invoking_state: isize,
    ) -> Rc<Sprite_play_stmtContextAll<'input>> {
        Rc::new(BaseParserRuleContext::new_parser_ctx(
            parent,
            invoking_state,
            Sprite_play_stmtContextExt {
                target: None,
                ph: PhantomData,
            },
        ))
    }
}

pub trait Sprite_play_stmtContextAttrs<'input>:
    SceneMaxNextGenParserContext<'input> + BorrowMut<Sprite_play_stmtContextExt<'input>>
{
    /// Retrieves first TerminalNode corresponding to token DOT
    /// Returns `None` if there is no child corresponding to token DOT
    fn DOT(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(DOT, 0)
    }
    /// Retrieves first TerminalNode corresponding to token PLAY
    /// Returns `None` if there is no child corresponding to token PLAY
    fn PLAY(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(PLAY, 0)
    }
    /// Retrieves first TerminalNode corresponding to token FRAME
    /// Returns `None` if there is no child corresponding to token FRAME
    fn FRAME(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(FRAME, 0)
    }
    fn logical_expression_all(&self) -> Vec<Rc<Logical_expressionContextAll<'input>>>
    where
        Self: Sized,
    {
        self.children_of_type()
    }
    fn logical_expression(&self, i: usize) -> Option<Rc<Logical_expressionContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(i)
    }
    /// Retrieves first TerminalNode corresponding to token TO
    /// Returns `None` if there is no child corresponding to token TO
    fn TO(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(TO, 0)
    }
    /// Retrieves first TerminalNode corresponding to token IDENT
    /// Returns `None` if there is no child corresponding to token IDENT
    fn IDENT(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(IDENT, 0)
    }
    fn duration_clause(&self) -> Option<Rc<Duration_clauseContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(0)
    }
    /// Retrieves first TerminalNode corresponding to token LOOP
    /// Returns `None` if there is no child corresponding to token LOOP
    fn LOOP(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(LOOP, 0)
    }
}

impl<'input> Sprite_play_stmtContextAttrs<'input> for Sprite_play_stmtContext<'input> {}

impl<'input, I, H> SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input>> + TidAble<'input>,
    H: ErrorStrategy<'input, BaseParserType<'input, I>>,
{
    pub fn sprite_play_stmt(
        &mut self,
    ) -> Result<Rc<Sprite_play_stmtContextAll<'input>>, ANTLRError> {
        let mut recog = self;
        let _parentctx = recog.ctx.take();
        let mut _localctx =
            Sprite_play_stmtContextExt::new(_parentctx.clone(), recog.base.get_state());
        recog
            .base
            .enter_rule(_localctx.clone(), 32, RULE_sprite_play_stmt);
        let mut _localctx: Rc<Sprite_play_stmtContextAll> = _localctx;
        let mut _la: isize = -1;
        let result: Result<(), ANTLRError> = (|| {
            //recog.base.enter_outer_alt(_localctx.clone(), 1);
            recog.base.enter_outer_alt(None, 1);
            {
                recog.base.set_state(228);
                let tmp = recog.base.match_token(IDENT, &mut recog.err_handler)?;
                cast_mut::<_, Sprite_play_stmtContext>(&mut _localctx).target = Some(tmp.clone());

                recog.base.set_state(229);
                recog.base.match_token(DOT, &mut recog.err_handler)?;

                recog.base.set_state(230);
                recog.base.match_token(PLAY, &mut recog.err_handler)?;

                recog.base.set_state(231);
                recog.base.match_token(FRAME, &mut recog.err_handler)?;

                /*InvokeRule logical_expression*/
                recog.base.set_state(232);
                recog.logical_expression()?;

                recog.base.set_state(233);
                recog.base.match_token(TO, &mut recog.err_handler)?;

                /*InvokeRule logical_expression*/
                recog.base.set_state(234);
                recog.logical_expression()?;

                recog.base.set_state(236);
                recog.err_handler.sync(&mut recog.base)?;
                _la = recog.base.input.la(1);
                if _la == IN || _la == FOR {
                    {
                        /*InvokeRule duration_clause*/
                        recog.base.set_state(235);
                        recog.duration_clause()?;
                    }
                }

                recog.base.set_state(239);
                recog.err_handler.sync(&mut recog.base)?;
                _la = recog.base.input.la(1);
                if _la == LOOP {
                    {
                        recog.base.set_state(238);
                        recog.base.match_token(LOOP, &mut recog.err_handler)?;
                    }
                }
            }
            Ok(())
        })();
        match result {
            Ok(_) => {}
            Err(e @ ANTLRError::FallThrough(_)) => return Err(e),
            Err(ref re) => {
                //_localctx.exception = re;
                recog.err_handler.report_error(&mut recog.base, re);
                recog.err_handler.recover(&mut recog.base, re)?;
            }
        }
        recog.base.exit_rule();

        Ok(_localctx)
    }
}
//------------------- character_jump_stmt ----------------
pub type Character_jump_stmtContextAll<'input> = Character_jump_stmtContext<'input>;

pub type Character_jump_stmtContext<'input> =
    BaseParserRuleContext<'input, Character_jump_stmtContextExt<'input>>;

#[derive(Clone)]
pub struct Character_jump_stmtContextExt<'input> {
    pub target: Option<TokenType<'input>>,
    ph: PhantomData<&'input str>,
}

impl<'input> SceneMaxNextGenParserContext<'input> for Character_jump_stmtContext<'input> {}

impl<'input, 'a> Listenable<dyn SceneMaxNextGenListener<'input> + 'a>
    for Character_jump_stmtContext<'input>
{
    fn enter(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.enter_every_rule(self);
        listener.enter_character_jump_stmt(self);
    }
    fn exit(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.exit_character_jump_stmt(self);
        listener.exit_every_rule(self);
    }
}

impl<'input, 'a> Visitable<dyn SceneMaxNextGenVisitor<'input> + 'a>
    for Character_jump_stmtContext<'input>
{
    fn accept(&self, visitor: &mut (dyn SceneMaxNextGenVisitor<'input> + 'a)) {
        visitor.visit_character_jump_stmt(self);
    }
}

impl<'input> CustomRuleContext<'input> for Character_jump_stmtContextExt<'input> {
    type TF = LocalTokenFactory<'input>;
    type Ctx = SceneMaxNextGenParserContextType;
    fn get_rule_index(&self) -> usize {
        RULE_character_jump_stmt
    }
    //fn type_rule_index() -> usize where Self: Sized { RULE_character_jump_stmt }
}
antlr_rust::tid! {Character_jump_stmtContextExt<'a>}

impl<'input> Character_jump_stmtContextExt<'input> {
    fn new(
        parent: Option<Rc<dyn SceneMaxNextGenParserContext<'input> + 'input>>,
        invoking_state: isize,
    ) -> Rc<Character_jump_stmtContextAll<'input>> {
        Rc::new(BaseParserRuleContext::new_parser_ctx(
            parent,
            invoking_state,
            Character_jump_stmtContextExt {
                target: None,
                ph: PhantomData,
            },
        ))
    }
}

pub trait Character_jump_stmtContextAttrs<'input>:
    SceneMaxNextGenParserContext<'input> + BorrowMut<Character_jump_stmtContextExt<'input>>
{
    /// Retrieves all `TerminalNode`s corresponding to token DOT in current rule
    fn DOT_all(&self) -> Vec<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.children_of_type()
    }
    /// Retrieves 'i's TerminalNode corresponding to token DOT, starting from 0.
    /// Returns `None` if number of children corresponding to token DOT is less or equal than `i`.
    fn DOT(&self, i: usize) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(DOT, i)
    }
    /// Retrieves first TerminalNode corresponding to token CHARACTER
    /// Returns `None` if there is no child corresponding to token CHARACTER
    fn CHARACTER(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(CHARACTER, 0)
    }
    /// Retrieves first TerminalNode corresponding to token JUMP
    /// Returns `None` if there is no child corresponding to token JUMP
    fn JUMP(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(JUMP, 0)
    }
    fn logical_expression(&self) -> Option<Rc<Logical_expressionContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(0)
    }
    /// Retrieves first TerminalNode corresponding to token IDENT
    /// Returns `None` if there is no child corresponding to token IDENT
    fn IDENT(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(IDENT, 0)
    }
    /// Retrieves first TerminalNode corresponding to token AT
    /// Returns `None` if there is no child corresponding to token AT
    fn AT(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(AT, 0)
    }
    /// Retrieves first TerminalNode corresponding to token SPEED
    /// Returns `None` if there is no child corresponding to token SPEED
    fn SPEED(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(SPEED, 0)
    }
    /// Retrieves first TerminalNode corresponding to token OF
    /// Returns `None` if there is no child corresponding to token OF
    fn OF(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(OF, 0)
    }
    /// Retrieves first TerminalNode corresponding to token ASYNC
    /// Returns `None` if there is no child corresponding to token ASYNC
    fn ASYNC(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(ASYNC, 0)
    }
}

impl<'input> Character_jump_stmtContextAttrs<'input> for Character_jump_stmtContext<'input> {}

impl<'input, I, H> SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input>> + TidAble<'input>,
    H: ErrorStrategy<'input, BaseParserType<'input, I>>,
{
    pub fn character_jump_stmt(
        &mut self,
    ) -> Result<Rc<Character_jump_stmtContextAll<'input>>, ANTLRError> {
        let mut recog = self;
        let _parentctx = recog.ctx.take();
        let mut _localctx =
            Character_jump_stmtContextExt::new(_parentctx.clone(), recog.base.get_state());
        recog
            .base
            .enter_rule(_localctx.clone(), 34, RULE_character_jump_stmt);
        let mut _localctx: Rc<Character_jump_stmtContextAll> = _localctx;
        let mut _la: isize = -1;
        let result: Result<(), ANTLRError> = (|| {
            //recog.base.enter_outer_alt(_localctx.clone(), 1);
            recog.base.enter_outer_alt(None, 1);
            {
                recog.base.set_state(241);
                let tmp = recog.base.match_token(IDENT, &mut recog.err_handler)?;
                cast_mut::<_, Character_jump_stmtContext>(&mut _localctx).target =
                    Some(tmp.clone());

                recog.base.set_state(242);
                recog.base.match_token(DOT, &mut recog.err_handler)?;

                recog.base.set_state(243);
                recog.base.match_token(CHARACTER, &mut recog.err_handler)?;

                recog.base.set_state(244);
                recog.base.match_token(DOT, &mut recog.err_handler)?;

                recog.base.set_state(245);
                recog.base.match_token(JUMP, &mut recog.err_handler)?;

                recog.base.set_state(252);
                recog.err_handler.sync(&mut recog.base)?;
                match recog.interpreter.adaptive_predict(24, &mut recog.base)? {
                    1 => {
                        recog.base.set_state(246);
                        recog.base.match_token(AT, &mut recog.err_handler)?;

                        recog.base.set_state(247);
                        recog.base.match_token(SPEED, &mut recog.err_handler)?;

                        recog.base.set_state(248);
                        recog.base.match_token(OF, &mut recog.err_handler)?;
                    }
                    2 => {
                        recog.base.set_state(249);
                        recog.base.match_token(SPEED, &mut recog.err_handler)?;

                        recog.base.set_state(250);
                        recog.base.match_token(OF, &mut recog.err_handler)?;
                    }
                    3 => {
                        recog.base.set_state(251);
                        recog.base.match_token(SPEED, &mut recog.err_handler)?;
                    }

                    _ => {}
                }
                /*InvokeRule logical_expression*/
                recog.base.set_state(254);
                recog.logical_expression()?;

                recog.base.set_state(256);
                recog.err_handler.sync(&mut recog.base)?;
                _la = recog.base.input.la(1);
                if _la == ASYNC {
                    {
                        recog.base.set_state(255);
                        recog.base.match_token(ASYNC, &mut recog.err_handler)?;
                    }
                }
            }
            Ok(())
        })();
        match result {
            Ok(_) => {}
            Err(e @ ANTLRError::FallThrough(_)) => return Err(e),
            Err(ref re) => {
                //_localctx.exception = re;
                recog.err_handler.report_error(&mut recog.base, re);
                recog.err_handler.recover(&mut recog.base, re)?;
            }
        }
        recog.base.exit_rule();

        Ok(_localctx)
    }
}
//------------------- physics_impulse_stmt ----------------
pub type Physics_impulse_stmtContextAll<'input> = Physics_impulse_stmtContext<'input>;

pub type Physics_impulse_stmtContext<'input> =
    BaseParserRuleContext<'input, Physics_impulse_stmtContextExt<'input>>;

#[derive(Clone)]
pub struct Physics_impulse_stmtContextExt<'input> {
    pub target: Option<TokenType<'input>>,
    pub direction: Option<Rc<Move_directionContextAll<'input>>>,
    ph: PhantomData<&'input str>,
}

impl<'input> SceneMaxNextGenParserContext<'input> for Physics_impulse_stmtContext<'input> {}

impl<'input, 'a> Listenable<dyn SceneMaxNextGenListener<'input> + 'a>
    for Physics_impulse_stmtContext<'input>
{
    fn enter(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.enter_every_rule(self);
        listener.enter_physics_impulse_stmt(self);
    }
    fn exit(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.exit_physics_impulse_stmt(self);
        listener.exit_every_rule(self);
    }
}

impl<'input, 'a> Visitable<dyn SceneMaxNextGenVisitor<'input> + 'a>
    for Physics_impulse_stmtContext<'input>
{
    fn accept(&self, visitor: &mut (dyn SceneMaxNextGenVisitor<'input> + 'a)) {
        visitor.visit_physics_impulse_stmt(self);
    }
}

impl<'input> CustomRuleContext<'input> for Physics_impulse_stmtContextExt<'input> {
    type TF = LocalTokenFactory<'input>;
    type Ctx = SceneMaxNextGenParserContextType;
    fn get_rule_index(&self) -> usize {
        RULE_physics_impulse_stmt
    }
    //fn type_rule_index() -> usize where Self: Sized { RULE_physics_impulse_stmt }
}
antlr_rust::tid! {Physics_impulse_stmtContextExt<'a>}

impl<'input> Physics_impulse_stmtContextExt<'input> {
    fn new(
        parent: Option<Rc<dyn SceneMaxNextGenParserContext<'input> + 'input>>,
        invoking_state: isize,
    ) -> Rc<Physics_impulse_stmtContextAll<'input>> {
        Rc::new(BaseParserRuleContext::new_parser_ctx(
            parent,
            invoking_state,
            Physics_impulse_stmtContextExt {
                target: None,
                direction: None,
                ph: PhantomData,
            },
        ))
    }
}

pub trait Physics_impulse_stmtContextAttrs<'input>:
    SceneMaxNextGenParserContext<'input> + BorrowMut<Physics_impulse_stmtContextExt<'input>>
{
    /// Retrieves first TerminalNode corresponding to token DOT
    /// Returns `None` if there is no child corresponding to token DOT
    fn DOT(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(DOT, 0)
    }
    /// Retrieves first TerminalNode corresponding to token PHYSICS
    /// Returns `None` if there is no child corresponding to token PHYSICS
    fn PHYSICS(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(PHYSICS, 0)
    }
    /// Retrieves first TerminalNode corresponding to token IMPULSE
    /// Returns `None` if there is no child corresponding to token IMPULSE
    fn IMPULSE(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(IMPULSE, 0)
    }
    fn logical_expression(&self) -> Option<Rc<Logical_expressionContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(0)
    }
    /// Retrieves first TerminalNode corresponding to token IDENT
    /// Returns `None` if there is no child corresponding to token IDENT
    fn IDENT(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(IDENT, 0)
    }
    fn move_direction(&self) -> Option<Rc<Move_directionContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(0)
    }
}

impl<'input> Physics_impulse_stmtContextAttrs<'input> for Physics_impulse_stmtContext<'input> {}

impl<'input, I, H> SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input>> + TidAble<'input>,
    H: ErrorStrategy<'input, BaseParserType<'input, I>>,
{
    pub fn physics_impulse_stmt(
        &mut self,
    ) -> Result<Rc<Physics_impulse_stmtContextAll<'input>>, ANTLRError> {
        let mut recog = self;
        let _parentctx = recog.ctx.take();
        let mut _localctx =
            Physics_impulse_stmtContextExt::new(_parentctx.clone(), recog.base.get_state());
        recog
            .base
            .enter_rule(_localctx.clone(), 36, RULE_physics_impulse_stmt);
        let mut _localctx: Rc<Physics_impulse_stmtContextAll> = _localctx;
        let result: Result<(), ANTLRError> = (|| {
            //recog.base.enter_outer_alt(_localctx.clone(), 1);
            recog.base.enter_outer_alt(None, 1);
            {
                recog.base.set_state(258);
                let tmp = recog.base.match_token(IDENT, &mut recog.err_handler)?;
                cast_mut::<_, Physics_impulse_stmtContext>(&mut _localctx).target =
                    Some(tmp.clone());

                recog.base.set_state(259);
                recog.base.match_token(DOT, &mut recog.err_handler)?;

                recog.base.set_state(260);
                recog.base.match_token(PHYSICS, &mut recog.err_handler)?;

                recog.base.set_state(261);
                recog.base.match_token(IMPULSE, &mut recog.err_handler)?;

                /*InvokeRule move_direction*/
                recog.base.set_state(262);
                let tmp = recog.move_direction()?;
                cast_mut::<_, Physics_impulse_stmtContext>(&mut _localctx).direction =
                    Some(tmp.clone());

                /*InvokeRule logical_expression*/
                recog.base.set_state(263);
                recog.logical_expression()?;
            }
            Ok(())
        })();
        match result {
            Ok(_) => {}
            Err(e @ ANTLRError::FallThrough(_)) => return Err(e),
            Err(ref re) => {
                //_localctx.exception = re;
                recog.err_handler.report_error(&mut recog.base, re);
                recog.err_handler.recover(&mut recog.base, re)?;
            }
        }
        recog.base.exit_rule();

        Ok(_localctx)
    }
}
//------------------- add_code_stmt ----------------
pub type Add_code_stmtContextAll<'input> = Add_code_stmtContext<'input>;

pub type Add_code_stmtContext<'input> =
    BaseParserRuleContext<'input, Add_code_stmtContextExt<'input>>;

#[derive(Clone)]
pub struct Add_code_stmtContextExt<'input> {
    ph: PhantomData<&'input str>,
}

impl<'input> SceneMaxNextGenParserContext<'input> for Add_code_stmtContext<'input> {}

impl<'input, 'a> Listenable<dyn SceneMaxNextGenListener<'input> + 'a>
    for Add_code_stmtContext<'input>
{
    fn enter(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.enter_every_rule(self);
        listener.enter_add_code_stmt(self);
    }
    fn exit(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.exit_add_code_stmt(self);
        listener.exit_every_rule(self);
    }
}

impl<'input, 'a> Visitable<dyn SceneMaxNextGenVisitor<'input> + 'a>
    for Add_code_stmtContext<'input>
{
    fn accept(&self, visitor: &mut (dyn SceneMaxNextGenVisitor<'input> + 'a)) {
        visitor.visit_add_code_stmt(self);
    }
}

impl<'input> CustomRuleContext<'input> for Add_code_stmtContextExt<'input> {
    type TF = LocalTokenFactory<'input>;
    type Ctx = SceneMaxNextGenParserContextType;
    fn get_rule_index(&self) -> usize {
        RULE_add_code_stmt
    }
    //fn type_rule_index() -> usize where Self: Sized { RULE_add_code_stmt }
}
antlr_rust::tid! {Add_code_stmtContextExt<'a>}

impl<'input> Add_code_stmtContextExt<'input> {
    fn new(
        parent: Option<Rc<dyn SceneMaxNextGenParserContext<'input> + 'input>>,
        invoking_state: isize,
    ) -> Rc<Add_code_stmtContextAll<'input>> {
        Rc::new(BaseParserRuleContext::new_parser_ctx(
            parent,
            invoking_state,
            Add_code_stmtContextExt { ph: PhantomData },
        ))
    }
}

pub trait Add_code_stmtContextAttrs<'input>:
    SceneMaxNextGenParserContext<'input> + BorrowMut<Add_code_stmtContextExt<'input>>
{
    /// Retrieves first TerminalNode corresponding to token ADD
    /// Returns `None` if there is no child corresponding to token ADD
    fn ADD(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(ADD, 0)
    }
    /// Retrieves first TerminalNode corresponding to token QUOTED_STRING
    /// Returns `None` if there is no child corresponding to token QUOTED_STRING
    fn QUOTED_STRING(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(QUOTED_STRING, 0)
    }
    /// Retrieves first TerminalNode corresponding to token CODE
    /// Returns `None` if there is no child corresponding to token CODE
    fn CODE(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(CODE, 0)
    }
}

impl<'input> Add_code_stmtContextAttrs<'input> for Add_code_stmtContext<'input> {}

impl<'input, I, H> SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input>> + TidAble<'input>,
    H: ErrorStrategy<'input, BaseParserType<'input, I>>,
{
    pub fn add_code_stmt(&mut self) -> Result<Rc<Add_code_stmtContextAll<'input>>, ANTLRError> {
        let mut recog = self;
        let _parentctx = recog.ctx.take();
        let mut _localctx =
            Add_code_stmtContextExt::new(_parentctx.clone(), recog.base.get_state());
        recog
            .base
            .enter_rule(_localctx.clone(), 38, RULE_add_code_stmt);
        let mut _localctx: Rc<Add_code_stmtContextAll> = _localctx;
        let result: Result<(), ANTLRError> = (|| {
            //recog.base.enter_outer_alt(_localctx.clone(), 1);
            recog.base.enter_outer_alt(None, 1);
            {
                recog.base.set_state(265);
                recog.base.match_token(ADD, &mut recog.err_handler)?;

                recog.base.set_state(266);
                recog
                    .base
                    .match_token(QUOTED_STRING, &mut recog.err_handler)?;

                recog.base.set_state(267);
                recog.base.match_token(CODE, &mut recog.err_handler)?;
            }
            Ok(())
        })();
        match result {
            Ok(_) => {}
            Err(e @ ANTLRError::FallThrough(_)) => return Err(e),
            Err(ref re) => {
                //_localctx.exception = re;
                recog.err_handler.report_error(&mut recog.base, re);
                recog.err_handler.recover(&mut recog.base, re)?;
            }
        }
        recog.base.exit_rule();

        Ok(_localctx)
    }
}
//------------------- qualified_name ----------------
pub type Qualified_nameContextAll<'input> = Qualified_nameContext<'input>;

pub type Qualified_nameContext<'input> =
    BaseParserRuleContext<'input, Qualified_nameContextExt<'input>>;

#[derive(Clone)]
pub struct Qualified_nameContextExt<'input> {
    ph: PhantomData<&'input str>,
}

impl<'input> SceneMaxNextGenParserContext<'input> for Qualified_nameContext<'input> {}

impl<'input, 'a> Listenable<dyn SceneMaxNextGenListener<'input> + 'a>
    for Qualified_nameContext<'input>
{
    fn enter(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.enter_every_rule(self);
        listener.enter_qualified_name(self);
    }
    fn exit(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.exit_qualified_name(self);
        listener.exit_every_rule(self);
    }
}

impl<'input, 'a> Visitable<dyn SceneMaxNextGenVisitor<'input> + 'a>
    for Qualified_nameContext<'input>
{
    fn accept(&self, visitor: &mut (dyn SceneMaxNextGenVisitor<'input> + 'a)) {
        visitor.visit_qualified_name(self);
    }
}

impl<'input> CustomRuleContext<'input> for Qualified_nameContextExt<'input> {
    type TF = LocalTokenFactory<'input>;
    type Ctx = SceneMaxNextGenParserContextType;
    fn get_rule_index(&self) -> usize {
        RULE_qualified_name
    }
    //fn type_rule_index() -> usize where Self: Sized { RULE_qualified_name }
}
antlr_rust::tid! {Qualified_nameContextExt<'a>}

impl<'input> Qualified_nameContextExt<'input> {
    fn new(
        parent: Option<Rc<dyn SceneMaxNextGenParserContext<'input> + 'input>>,
        invoking_state: isize,
    ) -> Rc<Qualified_nameContextAll<'input>> {
        Rc::new(BaseParserRuleContext::new_parser_ctx(
            parent,
            invoking_state,
            Qualified_nameContextExt { ph: PhantomData },
        ))
    }
}

pub trait Qualified_nameContextAttrs<'input>:
    SceneMaxNextGenParserContext<'input> + BorrowMut<Qualified_nameContextExt<'input>>
{
    fn qualified_name_part_all(&self) -> Vec<Rc<Qualified_name_partContextAll<'input>>>
    where
        Self: Sized,
    {
        self.children_of_type()
    }
    fn qualified_name_part(&self, i: usize) -> Option<Rc<Qualified_name_partContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(i)
    }
    /// Retrieves all `TerminalNode`s corresponding to token DOT in current rule
    fn DOT_all(&self) -> Vec<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.children_of_type()
    }
    /// Retrieves 'i's TerminalNode corresponding to token DOT, starting from 0.
    /// Returns `None` if number of children corresponding to token DOT is less or equal than `i`.
    fn DOT(&self, i: usize) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(DOT, i)
    }
}

impl<'input> Qualified_nameContextAttrs<'input> for Qualified_nameContext<'input> {}

impl<'input, I, H> SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input>> + TidAble<'input>,
    H: ErrorStrategy<'input, BaseParserType<'input, I>>,
{
    pub fn qualified_name(&mut self) -> Result<Rc<Qualified_nameContextAll<'input>>, ANTLRError> {
        let mut recog = self;
        let _parentctx = recog.ctx.take();
        let mut _localctx =
            Qualified_nameContextExt::new(_parentctx.clone(), recog.base.get_state());
        recog
            .base
            .enter_rule(_localctx.clone(), 40, RULE_qualified_name);
        let mut _localctx: Rc<Qualified_nameContextAll> = _localctx;
        let mut _la: isize = -1;
        let result: Result<(), ANTLRError> = (|| {
            //recog.base.enter_outer_alt(_localctx.clone(), 1);
            recog.base.enter_outer_alt(None, 1);
            {
                /*InvokeRule qualified_name_part*/
                recog.base.set_state(269);
                recog.qualified_name_part()?;

                recog.base.set_state(274);
                recog.err_handler.sync(&mut recog.base)?;
                _la = recog.base.input.la(1);
                while _la == DOT {
                    {
                        {
                            recog.base.set_state(270);
                            recog.base.match_token(DOT, &mut recog.err_handler)?;

                            /*InvokeRule qualified_name_part*/
                            recog.base.set_state(271);
                            recog.qualified_name_part()?;
                        }
                    }
                    recog.base.set_state(276);
                    recog.err_handler.sync(&mut recog.base)?;
                    _la = recog.base.input.la(1);
                }
            }
            Ok(())
        })();
        match result {
            Ok(_) => {}
            Err(e @ ANTLRError::FallThrough(_)) => return Err(e),
            Err(ref re) => {
                //_localctx.exception = re;
                recog.err_handler.report_error(&mut recog.base, re);
                recog.err_handler.recover(&mut recog.base, re)?;
            }
        }
        recog.base.exit_rule();

        Ok(_localctx)
    }
}
//------------------- qualified_name_part ----------------
pub type Qualified_name_partContextAll<'input> = Qualified_name_partContext<'input>;

pub type Qualified_name_partContext<'input> =
    BaseParserRuleContext<'input, Qualified_name_partContextExt<'input>>;

#[derive(Clone)]
pub struct Qualified_name_partContextExt<'input> {
    ph: PhantomData<&'input str>,
}

impl<'input> SceneMaxNextGenParserContext<'input> for Qualified_name_partContext<'input> {}

impl<'input, 'a> Listenable<dyn SceneMaxNextGenListener<'input> + 'a>
    for Qualified_name_partContext<'input>
{
    fn enter(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.enter_every_rule(self);
        listener.enter_qualified_name_part(self);
    }
    fn exit(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.exit_qualified_name_part(self);
        listener.exit_every_rule(self);
    }
}

impl<'input, 'a> Visitable<dyn SceneMaxNextGenVisitor<'input> + 'a>
    for Qualified_name_partContext<'input>
{
    fn accept(&self, visitor: &mut (dyn SceneMaxNextGenVisitor<'input> + 'a)) {
        visitor.visit_qualified_name_part(self);
    }
}

impl<'input> CustomRuleContext<'input> for Qualified_name_partContextExt<'input> {
    type TF = LocalTokenFactory<'input>;
    type Ctx = SceneMaxNextGenParserContextType;
    fn get_rule_index(&self) -> usize {
        RULE_qualified_name_part
    }
    //fn type_rule_index() -> usize where Self: Sized { RULE_qualified_name_part }
}
antlr_rust::tid! {Qualified_name_partContextExt<'a>}

impl<'input> Qualified_name_partContextExt<'input> {
    fn new(
        parent: Option<Rc<dyn SceneMaxNextGenParserContext<'input> + 'input>>,
        invoking_state: isize,
    ) -> Rc<Qualified_name_partContextAll<'input>> {
        Rc::new(BaseParserRuleContext::new_parser_ctx(
            parent,
            invoking_state,
            Qualified_name_partContextExt { ph: PhantomData },
        ))
    }
}

pub trait Qualified_name_partContextAttrs<'input>:
    SceneMaxNextGenParserContext<'input> + BorrowMut<Qualified_name_partContextExt<'input>>
{
    /// Retrieves first TerminalNode corresponding to token IDENT
    /// Returns `None` if there is no child corresponding to token IDENT
    fn IDENT(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(IDENT, 0)
    }
    fn keyword_identifier(&self) -> Option<Rc<Keyword_identifierContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(0)
    }
}

impl<'input> Qualified_name_partContextAttrs<'input> for Qualified_name_partContext<'input> {}

impl<'input, I, H> SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input>> + TidAble<'input>,
    H: ErrorStrategy<'input, BaseParserType<'input, I>>,
{
    pub fn qualified_name_part(
        &mut self,
    ) -> Result<Rc<Qualified_name_partContextAll<'input>>, ANTLRError> {
        let mut recog = self;
        let _parentctx = recog.ctx.take();
        let mut _localctx =
            Qualified_name_partContextExt::new(_parentctx.clone(), recog.base.get_state());
        recog
            .base
            .enter_rule(_localctx.clone(), 42, RULE_qualified_name_part);
        let mut _localctx: Rc<Qualified_name_partContextAll> = _localctx;
        let result: Result<(), ANTLRError> = (|| {
            recog.base.set_state(279);
            recog.err_handler.sync(&mut recog.base)?;
            match recog.base.input.la(1) {
                IDENT => {
                    //recog.base.enter_outer_alt(_localctx.clone(), 1);
                    recog.base.enter_outer_alt(None, 1);
                    {
                        recog.base.set_state(277);
                        recog.base.match_token(IDENT, &mut recog.err_handler)?;
                    }
                }

                LOOP | ADD | CODE | AT | SPEED | OF | MOVE | FORWARD | BACKWARD | BACK | LEFT
                | RIGHT | UP | DOWN | IN | FOR | SECONDS | TO | TURN | ROTATE | RUN | EVERY
                | ASYNC | ANIMATION | PLAY | FRAME | CHARACTER | JUMP | PHYSICS | IMPULSE
                | WHEN | TRUE | FALSE => {
                    //recog.base.enter_outer_alt(_localctx.clone(), 2);
                    recog.base.enter_outer_alt(None, 2);
                    {
                        /*InvokeRule keyword_identifier*/
                        recog.base.set_state(278);
                        recog.keyword_identifier()?;
                    }
                }

                _ => Err(ANTLRError::NoAltError(NoViableAltError::new(
                    &mut recog.base,
                )))?,
            }
            Ok(())
        })();
        match result {
            Ok(_) => {}
            Err(e @ ANTLRError::FallThrough(_)) => return Err(e),
            Err(ref re) => {
                //_localctx.exception = re;
                recog.err_handler.report_error(&mut recog.base, re);
                recog.err_handler.recover(&mut recog.base, re)?;
            }
        }
        recog.base.exit_rule();

        Ok(_localctx)
    }
}
//------------------- animation_name ----------------
pub type Animation_nameContextAll<'input> = Animation_nameContext<'input>;

pub type Animation_nameContext<'input> =
    BaseParserRuleContext<'input, Animation_nameContextExt<'input>>;

#[derive(Clone)]
pub struct Animation_nameContextExt<'input> {
    ph: PhantomData<&'input str>,
}

impl<'input> SceneMaxNextGenParserContext<'input> for Animation_nameContext<'input> {}

impl<'input, 'a> Listenable<dyn SceneMaxNextGenListener<'input> + 'a>
    for Animation_nameContext<'input>
{
    fn enter(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.enter_every_rule(self);
        listener.enter_animation_name(self);
    }
    fn exit(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.exit_animation_name(self);
        listener.exit_every_rule(self);
    }
}

impl<'input, 'a> Visitable<dyn SceneMaxNextGenVisitor<'input> + 'a>
    for Animation_nameContext<'input>
{
    fn accept(&self, visitor: &mut (dyn SceneMaxNextGenVisitor<'input> + 'a)) {
        visitor.visit_animation_name(self);
    }
}

impl<'input> CustomRuleContext<'input> for Animation_nameContextExt<'input> {
    type TF = LocalTokenFactory<'input>;
    type Ctx = SceneMaxNextGenParserContextType;
    fn get_rule_index(&self) -> usize {
        RULE_animation_name
    }
    //fn type_rule_index() -> usize where Self: Sized { RULE_animation_name }
}
antlr_rust::tid! {Animation_nameContextExt<'a>}

impl<'input> Animation_nameContextExt<'input> {
    fn new(
        parent: Option<Rc<dyn SceneMaxNextGenParserContext<'input> + 'input>>,
        invoking_state: isize,
    ) -> Rc<Animation_nameContextAll<'input>> {
        Rc::new(BaseParserRuleContext::new_parser_ctx(
            parent,
            invoking_state,
            Animation_nameContextExt { ph: PhantomData },
        ))
    }
}

pub trait Animation_nameContextAttrs<'input>:
    SceneMaxNextGenParserContext<'input> + BorrowMut<Animation_nameContextExt<'input>>
{
    /// Retrieves first TerminalNode corresponding to token IDENT
    /// Returns `None` if there is no child corresponding to token IDENT
    fn IDENT(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(IDENT, 0)
    }
    /// Retrieves first TerminalNode corresponding to token QUOTED_STRING
    /// Returns `None` if there is no child corresponding to token QUOTED_STRING
    fn QUOTED_STRING(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(QUOTED_STRING, 0)
    }
    fn keyword_identifier(&self) -> Option<Rc<Keyword_identifierContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(0)
    }
}

impl<'input> Animation_nameContextAttrs<'input> for Animation_nameContext<'input> {}

impl<'input, I, H> SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input>> + TidAble<'input>,
    H: ErrorStrategy<'input, BaseParserType<'input, I>>,
{
    pub fn animation_name(&mut self) -> Result<Rc<Animation_nameContextAll<'input>>, ANTLRError> {
        let mut recog = self;
        let _parentctx = recog.ctx.take();
        let mut _localctx =
            Animation_nameContextExt::new(_parentctx.clone(), recog.base.get_state());
        recog
            .base
            .enter_rule(_localctx.clone(), 44, RULE_animation_name);
        let mut _localctx: Rc<Animation_nameContextAll> = _localctx;
        let result: Result<(), ANTLRError> = (|| {
            recog.base.set_state(284);
            recog.err_handler.sync(&mut recog.base)?;
            match recog.base.input.la(1) {
                IDENT => {
                    //recog.base.enter_outer_alt(_localctx.clone(), 1);
                    recog.base.enter_outer_alt(None, 1);
                    {
                        recog.base.set_state(281);
                        recog.base.match_token(IDENT, &mut recog.err_handler)?;
                    }
                }

                QUOTED_STRING => {
                    //recog.base.enter_outer_alt(_localctx.clone(), 2);
                    recog.base.enter_outer_alt(None, 2);
                    {
                        recog.base.set_state(282);
                        recog
                            .base
                            .match_token(QUOTED_STRING, &mut recog.err_handler)?;
                    }
                }

                LOOP | ADD | CODE | AT | SPEED | OF | MOVE | FORWARD | BACKWARD | BACK | LEFT
                | RIGHT | UP | DOWN | IN | FOR | SECONDS | TO | TURN | ROTATE | RUN | EVERY
                | ASYNC | ANIMATION | PLAY | FRAME | CHARACTER | JUMP | PHYSICS | IMPULSE
                | WHEN | TRUE | FALSE => {
                    //recog.base.enter_outer_alt(_localctx.clone(), 3);
                    recog.base.enter_outer_alt(None, 3);
                    {
                        /*InvokeRule keyword_identifier*/
                        recog.base.set_state(283);
                        recog.keyword_identifier()?;
                    }
                }

                _ => Err(ANTLRError::NoAltError(NoViableAltError::new(
                    &mut recog.base,
                )))?,
            }
            Ok(())
        })();
        match result {
            Ok(_) => {}
            Err(e @ ANTLRError::FallThrough(_)) => return Err(e),
            Err(ref re) => {
                //_localctx.exception = re;
                recog.err_handler.report_error(&mut recog.base, re);
                recog.err_handler.recover(&mut recog.base, re)?;
            }
        }
        recog.base.exit_rule();

        Ok(_localctx)
    }
}
//------------------- keyword_identifier ----------------
pub type Keyword_identifierContextAll<'input> = Keyword_identifierContext<'input>;

pub type Keyword_identifierContext<'input> =
    BaseParserRuleContext<'input, Keyword_identifierContextExt<'input>>;

#[derive(Clone)]
pub struct Keyword_identifierContextExt<'input> {
    ph: PhantomData<&'input str>,
}

impl<'input> SceneMaxNextGenParserContext<'input> for Keyword_identifierContext<'input> {}

impl<'input, 'a> Listenable<dyn SceneMaxNextGenListener<'input> + 'a>
    for Keyword_identifierContext<'input>
{
    fn enter(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.enter_every_rule(self);
        listener.enter_keyword_identifier(self);
    }
    fn exit(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.exit_keyword_identifier(self);
        listener.exit_every_rule(self);
    }
}

impl<'input, 'a> Visitable<dyn SceneMaxNextGenVisitor<'input> + 'a>
    for Keyword_identifierContext<'input>
{
    fn accept(&self, visitor: &mut (dyn SceneMaxNextGenVisitor<'input> + 'a)) {
        visitor.visit_keyword_identifier(self);
    }
}

impl<'input> CustomRuleContext<'input> for Keyword_identifierContextExt<'input> {
    type TF = LocalTokenFactory<'input>;
    type Ctx = SceneMaxNextGenParserContextType;
    fn get_rule_index(&self) -> usize {
        RULE_keyword_identifier
    }
    //fn type_rule_index() -> usize where Self: Sized { RULE_keyword_identifier }
}
antlr_rust::tid! {Keyword_identifierContextExt<'a>}

impl<'input> Keyword_identifierContextExt<'input> {
    fn new(
        parent: Option<Rc<dyn SceneMaxNextGenParserContext<'input> + 'input>>,
        invoking_state: isize,
    ) -> Rc<Keyword_identifierContextAll<'input>> {
        Rc::new(BaseParserRuleContext::new_parser_ctx(
            parent,
            invoking_state,
            Keyword_identifierContextExt { ph: PhantomData },
        ))
    }
}

pub trait Keyword_identifierContextAttrs<'input>:
    SceneMaxNextGenParserContext<'input> + BorrowMut<Keyword_identifierContextExt<'input>>
{
    /// Retrieves first TerminalNode corresponding to token LOOP
    /// Returns `None` if there is no child corresponding to token LOOP
    fn LOOP(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(LOOP, 0)
    }
    /// Retrieves first TerminalNode corresponding to token ADD
    /// Returns `None` if there is no child corresponding to token ADD
    fn ADD(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(ADD, 0)
    }
    /// Retrieves first TerminalNode corresponding to token CODE
    /// Returns `None` if there is no child corresponding to token CODE
    fn CODE(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(CODE, 0)
    }
    /// Retrieves first TerminalNode corresponding to token AT
    /// Returns `None` if there is no child corresponding to token AT
    fn AT(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(AT, 0)
    }
    /// Retrieves first TerminalNode corresponding to token SPEED
    /// Returns `None` if there is no child corresponding to token SPEED
    fn SPEED(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(SPEED, 0)
    }
    /// Retrieves first TerminalNode corresponding to token OF
    /// Returns `None` if there is no child corresponding to token OF
    fn OF(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(OF, 0)
    }
    /// Retrieves first TerminalNode corresponding to token MOVE
    /// Returns `None` if there is no child corresponding to token MOVE
    fn MOVE(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(MOVE, 0)
    }
    /// Retrieves first TerminalNode corresponding to token FORWARD
    /// Returns `None` if there is no child corresponding to token FORWARD
    fn FORWARD(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(FORWARD, 0)
    }
    /// Retrieves first TerminalNode corresponding to token BACKWARD
    /// Returns `None` if there is no child corresponding to token BACKWARD
    fn BACKWARD(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(BACKWARD, 0)
    }
    /// Retrieves first TerminalNode corresponding to token BACK
    /// Returns `None` if there is no child corresponding to token BACK
    fn BACK(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(BACK, 0)
    }
    /// Retrieves first TerminalNode corresponding to token LEFT
    /// Returns `None` if there is no child corresponding to token LEFT
    fn LEFT(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(LEFT, 0)
    }
    /// Retrieves first TerminalNode corresponding to token RIGHT
    /// Returns `None` if there is no child corresponding to token RIGHT
    fn RIGHT(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(RIGHT, 0)
    }
    /// Retrieves first TerminalNode corresponding to token UP
    /// Returns `None` if there is no child corresponding to token UP
    fn UP(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(UP, 0)
    }
    /// Retrieves first TerminalNode corresponding to token DOWN
    /// Returns `None` if there is no child corresponding to token DOWN
    fn DOWN(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(DOWN, 0)
    }
    /// Retrieves first TerminalNode corresponding to token IN
    /// Returns `None` if there is no child corresponding to token IN
    fn IN(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(IN, 0)
    }
    /// Retrieves first TerminalNode corresponding to token FOR
    /// Returns `None` if there is no child corresponding to token FOR
    fn FOR(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(FOR, 0)
    }
    /// Retrieves first TerminalNode corresponding to token SECONDS
    /// Returns `None` if there is no child corresponding to token SECONDS
    fn SECONDS(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(SECONDS, 0)
    }
    /// Retrieves first TerminalNode corresponding to token TO
    /// Returns `None` if there is no child corresponding to token TO
    fn TO(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(TO, 0)
    }
    /// Retrieves first TerminalNode corresponding to token TURN
    /// Returns `None` if there is no child corresponding to token TURN
    fn TURN(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(TURN, 0)
    }
    /// Retrieves first TerminalNode corresponding to token ROTATE
    /// Returns `None` if there is no child corresponding to token ROTATE
    fn ROTATE(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(ROTATE, 0)
    }
    /// Retrieves first TerminalNode corresponding to token RUN
    /// Returns `None` if there is no child corresponding to token RUN
    fn RUN(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(RUN, 0)
    }
    /// Retrieves first TerminalNode corresponding to token EVERY
    /// Returns `None` if there is no child corresponding to token EVERY
    fn EVERY(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(EVERY, 0)
    }
    /// Retrieves first TerminalNode corresponding to token ASYNC
    /// Returns `None` if there is no child corresponding to token ASYNC
    fn ASYNC(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(ASYNC, 0)
    }
    /// Retrieves first TerminalNode corresponding to token ANIMATION
    /// Returns `None` if there is no child corresponding to token ANIMATION
    fn ANIMATION(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(ANIMATION, 0)
    }
    /// Retrieves first TerminalNode corresponding to token PLAY
    /// Returns `None` if there is no child corresponding to token PLAY
    fn PLAY(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(PLAY, 0)
    }
    /// Retrieves first TerminalNode corresponding to token FRAME
    /// Returns `None` if there is no child corresponding to token FRAME
    fn FRAME(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(FRAME, 0)
    }
    /// Retrieves first TerminalNode corresponding to token CHARACTER
    /// Returns `None` if there is no child corresponding to token CHARACTER
    fn CHARACTER(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(CHARACTER, 0)
    }
    /// Retrieves first TerminalNode corresponding to token JUMP
    /// Returns `None` if there is no child corresponding to token JUMP
    fn JUMP(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(JUMP, 0)
    }
    /// Retrieves first TerminalNode corresponding to token PHYSICS
    /// Returns `None` if there is no child corresponding to token PHYSICS
    fn PHYSICS(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(PHYSICS, 0)
    }
    /// Retrieves first TerminalNode corresponding to token IMPULSE
    /// Returns `None` if there is no child corresponding to token IMPULSE
    fn IMPULSE(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(IMPULSE, 0)
    }
    /// Retrieves first TerminalNode corresponding to token WHEN
    /// Returns `None` if there is no child corresponding to token WHEN
    fn WHEN(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(WHEN, 0)
    }
    /// Retrieves first TerminalNode corresponding to token TRUE
    /// Returns `None` if there is no child corresponding to token TRUE
    fn TRUE(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(TRUE, 0)
    }
    /// Retrieves first TerminalNode corresponding to token FALSE
    /// Returns `None` if there is no child corresponding to token FALSE
    fn FALSE(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(FALSE, 0)
    }
}

impl<'input> Keyword_identifierContextAttrs<'input> for Keyword_identifierContext<'input> {}

impl<'input, I, H> SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input>> + TidAble<'input>,
    H: ErrorStrategy<'input, BaseParserType<'input, I>>,
{
    pub fn keyword_identifier(
        &mut self,
    ) -> Result<Rc<Keyword_identifierContextAll<'input>>, ANTLRError> {
        let mut recog = self;
        let _parentctx = recog.ctx.take();
        let mut _localctx =
            Keyword_identifierContextExt::new(_parentctx.clone(), recog.base.get_state());
        recog
            .base
            .enter_rule(_localctx.clone(), 46, RULE_keyword_identifier);
        let mut _localctx: Rc<Keyword_identifierContextAll> = _localctx;
        let mut _la: isize = -1;
        let result: Result<(), ANTLRError> = (|| {
            //recog.base.enter_outer_alt(_localctx.clone(), 1);
            recog.base.enter_outer_alt(None, 1);
            {
                recog.base.set_state(286);
                _la = recog.base.input.la(1);
                if {
                    !((((_la) & !0x3f) == 0
                        && ((1usize << _la)
                            & ((1usize << LOOP)
                                | (1usize << ADD)
                                | (1usize << CODE)
                                | (1usize << AT)
                                | (1usize << SPEED)
                                | (1usize << OF)
                                | (1usize << MOVE)
                                | (1usize << FORWARD)
                                | (1usize << BACKWARD)
                                | (1usize << BACK)
                                | (1usize << LEFT)
                                | (1usize << RIGHT)
                                | (1usize << UP)
                                | (1usize << DOWN)
                                | (1usize << IN)
                                | (1usize << FOR)
                                | (1usize << SECONDS)
                                | (1usize << TO)
                                | (1usize << TURN)
                                | (1usize << ROTATE)
                                | (1usize << RUN)
                                | (1usize << EVERY)
                                | (1usize << ASYNC)
                                | (1usize << ANIMATION)
                                | (1usize << PLAY)
                                | (1usize << FRAME)
                                | (1usize << CHARACTER)
                                | (1usize << JUMP)
                                | (1usize << PHYSICS)
                                | (1usize << IMPULSE)))
                            != 0)
                        || (((_la - 32) & !0x3f) == 0
                            && ((1usize << (_la - 32))
                                & ((1usize << (WHEN - 32))
                                    | (1usize << (TRUE - 32))
                                    | (1usize << (FALSE - 32))))
                                != 0))
                } {
                    recog.err_handler.recover_inline(&mut recog.base)?;
                } else {
                    if recog.base.input.la(1) == TOKEN_EOF {
                        recog.base.matched_eof = true
                    };
                    recog.err_handler.report_match(&mut recog.base);
                    recog.base.consume(&mut recog.err_handler);
                }
            }
            Ok(())
        })();
        match result {
            Ok(_) => {}
            Err(e @ ANTLRError::FallThrough(_)) => return Err(e),
            Err(ref re) => {
                //_localctx.exception = re;
                recog.err_handler.report_error(&mut recog.base, re);
                recog.err_handler.recover(&mut recog.base, re)?;
            }
        }
        recog.base.exit_rule();

        Ok(_localctx)
    }
}
//------------------- number ----------------
pub type NumberContextAll<'input> = NumberContext<'input>;

pub type NumberContext<'input> = BaseParserRuleContext<'input, NumberContextExt<'input>>;

#[derive(Clone)]
pub struct NumberContextExt<'input> {
    ph: PhantomData<&'input str>,
}

impl<'input> SceneMaxNextGenParserContext<'input> for NumberContext<'input> {}

impl<'input, 'a> Listenable<dyn SceneMaxNextGenListener<'input> + 'a> for NumberContext<'input> {
    fn enter(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.enter_every_rule(self);
        listener.enter_number(self);
    }
    fn exit(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.exit_number(self);
        listener.exit_every_rule(self);
    }
}

impl<'input, 'a> Visitable<dyn SceneMaxNextGenVisitor<'input> + 'a> for NumberContext<'input> {
    fn accept(&self, visitor: &mut (dyn SceneMaxNextGenVisitor<'input> + 'a)) {
        visitor.visit_number(self);
    }
}

impl<'input> CustomRuleContext<'input> for NumberContextExt<'input> {
    type TF = LocalTokenFactory<'input>;
    type Ctx = SceneMaxNextGenParserContextType;
    fn get_rule_index(&self) -> usize {
        RULE_number
    }
    //fn type_rule_index() -> usize where Self: Sized { RULE_number }
}
antlr_rust::tid! {NumberContextExt<'a>}

impl<'input> NumberContextExt<'input> {
    fn new(
        parent: Option<Rc<dyn SceneMaxNextGenParserContext<'input> + 'input>>,
        invoking_state: isize,
    ) -> Rc<NumberContextAll<'input>> {
        Rc::new(BaseParserRuleContext::new_parser_ctx(
            parent,
            invoking_state,
            NumberContextExt { ph: PhantomData },
        ))
    }
}

pub trait NumberContextAttrs<'input>:
    SceneMaxNextGenParserContext<'input> + BorrowMut<NumberContextExt<'input>>
{
    /// Retrieves first TerminalNode corresponding to token DECIMAL
    /// Returns `None` if there is no child corresponding to token DECIMAL
    fn DECIMAL(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(DECIMAL, 0)
    }
    /// Retrieves first TerminalNode corresponding to token PLUS
    /// Returns `None` if there is no child corresponding to token PLUS
    fn PLUS(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(PLUS, 0)
    }
    /// Retrieves first TerminalNode corresponding to token MINUS
    /// Returns `None` if there is no child corresponding to token MINUS
    fn MINUS(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(MINUS, 0)
    }
}

impl<'input> NumberContextAttrs<'input> for NumberContext<'input> {}

impl<'input, I, H> SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input>> + TidAble<'input>,
    H: ErrorStrategy<'input, BaseParserType<'input, I>>,
{
    pub fn number(&mut self) -> Result<Rc<NumberContextAll<'input>>, ANTLRError> {
        let mut recog = self;
        let _parentctx = recog.ctx.take();
        let mut _localctx = NumberContextExt::new(_parentctx.clone(), recog.base.get_state());
        recog.base.enter_rule(_localctx.clone(), 48, RULE_number);
        let mut _localctx: Rc<NumberContextAll> = _localctx;
        let mut _la: isize = -1;
        let result: Result<(), ANTLRError> = (|| {
            //recog.base.enter_outer_alt(_localctx.clone(), 1);
            recog.base.enter_outer_alt(None, 1);
            {
                recog.base.set_state(289);
                recog.err_handler.sync(&mut recog.base)?;
                _la = recog.base.input.la(1);
                if _la == PLUS || _la == MINUS {
                    {
                        recog.base.set_state(288);
                        _la = recog.base.input.la(1);
                        if { !(_la == PLUS || _la == MINUS) } {
                            recog.err_handler.recover_inline(&mut recog.base)?;
                        } else {
                            if recog.base.input.la(1) == TOKEN_EOF {
                                recog.base.matched_eof = true
                            };
                            recog.err_handler.report_match(&mut recog.base);
                            recog.base.consume(&mut recog.err_handler);
                        }
                    }
                }

                recog.base.set_state(291);
                recog.base.match_token(DECIMAL, &mut recog.err_handler)?;
            }
            Ok(())
        })();
        match result {
            Ok(_) => {}
            Err(e @ ANTLRError::FallThrough(_)) => return Err(e),
            Err(ref re) => {
                //_localctx.exception = re;
                recog.err_handler.report_error(&mut recog.base, re);
                recog.err_handler.recover(&mut recog.base, re)?;
            }
        }
        recog.base.exit_rule();

        Ok(_localctx)
    }
}
//------------------- logical_expression ----------------
pub type Logical_expressionContextAll<'input> = Logical_expressionContext<'input>;

pub type Logical_expressionContext<'input> =
    BaseParserRuleContext<'input, Logical_expressionContextExt<'input>>;

#[derive(Clone)]
pub struct Logical_expressionContextExt<'input> {
    ph: PhantomData<&'input str>,
}

impl<'input> SceneMaxNextGenParserContext<'input> for Logical_expressionContext<'input> {}

impl<'input, 'a> Listenable<dyn SceneMaxNextGenListener<'input> + 'a>
    for Logical_expressionContext<'input>
{
    fn enter(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.enter_every_rule(self);
        listener.enter_logical_expression(self);
    }
    fn exit(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.exit_logical_expression(self);
        listener.exit_every_rule(self);
    }
}

impl<'input, 'a> Visitable<dyn SceneMaxNextGenVisitor<'input> + 'a>
    for Logical_expressionContext<'input>
{
    fn accept(&self, visitor: &mut (dyn SceneMaxNextGenVisitor<'input> + 'a)) {
        visitor.visit_logical_expression(self);
    }
}

impl<'input> CustomRuleContext<'input> for Logical_expressionContextExt<'input> {
    type TF = LocalTokenFactory<'input>;
    type Ctx = SceneMaxNextGenParserContextType;
    fn get_rule_index(&self) -> usize {
        RULE_logical_expression
    }
    //fn type_rule_index() -> usize where Self: Sized { RULE_logical_expression }
}
antlr_rust::tid! {Logical_expressionContextExt<'a>}

impl<'input> Logical_expressionContextExt<'input> {
    fn new(
        parent: Option<Rc<dyn SceneMaxNextGenParserContext<'input> + 'input>>,
        invoking_state: isize,
    ) -> Rc<Logical_expressionContextAll<'input>> {
        Rc::new(BaseParserRuleContext::new_parser_ctx(
            parent,
            invoking_state,
            Logical_expressionContextExt { ph: PhantomData },
        ))
    }
}

pub trait Logical_expressionContextAttrs<'input>:
    SceneMaxNextGenParserContext<'input> + BorrowMut<Logical_expressionContextExt<'input>>
{
    fn boolean_and_expression_all(&self) -> Vec<Rc<Boolean_and_expressionContextAll<'input>>>
    where
        Self: Sized,
    {
        self.children_of_type()
    }
    fn boolean_and_expression(
        &self,
        i: usize,
    ) -> Option<Rc<Boolean_and_expressionContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(i)
    }
    /// Retrieves all `TerminalNode`s corresponding to token OR in current rule
    fn OR_all(&self) -> Vec<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.children_of_type()
    }
    /// Retrieves 'i's TerminalNode corresponding to token OR, starting from 0.
    /// Returns `None` if number of children corresponding to token OR is less or equal than `i`.
    fn OR(&self, i: usize) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(OR, i)
    }
}

impl<'input> Logical_expressionContextAttrs<'input> for Logical_expressionContext<'input> {}

impl<'input, I, H> SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input>> + TidAble<'input>,
    H: ErrorStrategy<'input, BaseParserType<'input, I>>,
{
    pub fn logical_expression(
        &mut self,
    ) -> Result<Rc<Logical_expressionContextAll<'input>>, ANTLRError> {
        let mut recog = self;
        let _parentctx = recog.ctx.take();
        let mut _localctx =
            Logical_expressionContextExt::new(_parentctx.clone(), recog.base.get_state());
        recog
            .base
            .enter_rule(_localctx.clone(), 50, RULE_logical_expression);
        let mut _localctx: Rc<Logical_expressionContextAll> = _localctx;
        let mut _la: isize = -1;
        let result: Result<(), ANTLRError> = (|| {
            //recog.base.enter_outer_alt(_localctx.clone(), 1);
            recog.base.enter_outer_alt(None, 1);
            {
                /*InvokeRule boolean_and_expression*/
                recog.base.set_state(293);
                recog.boolean_and_expression()?;

                recog.base.set_state(298);
                recog.err_handler.sync(&mut recog.base)?;
                _la = recog.base.input.la(1);
                while _la == OR {
                    {
                        {
                            recog.base.set_state(294);
                            recog.base.match_token(OR, &mut recog.err_handler)?;

                            /*InvokeRule boolean_and_expression*/
                            recog.base.set_state(295);
                            recog.boolean_and_expression()?;
                        }
                    }
                    recog.base.set_state(300);
                    recog.err_handler.sync(&mut recog.base)?;
                    _la = recog.base.input.la(1);
                }
            }
            Ok(())
        })();
        match result {
            Ok(_) => {}
            Err(e @ ANTLRError::FallThrough(_)) => return Err(e),
            Err(ref re) => {
                //_localctx.exception = re;
                recog.err_handler.report_error(&mut recog.base, re);
                recog.err_handler.recover(&mut recog.base, re)?;
            }
        }
        recog.base.exit_rule();

        Ok(_localctx)
    }
}
//------------------- boolean_and_expression ----------------
pub type Boolean_and_expressionContextAll<'input> = Boolean_and_expressionContext<'input>;

pub type Boolean_and_expressionContext<'input> =
    BaseParserRuleContext<'input, Boolean_and_expressionContextExt<'input>>;

#[derive(Clone)]
pub struct Boolean_and_expressionContextExt<'input> {
    ph: PhantomData<&'input str>,
}

impl<'input> SceneMaxNextGenParserContext<'input> for Boolean_and_expressionContext<'input> {}

impl<'input, 'a> Listenable<dyn SceneMaxNextGenListener<'input> + 'a>
    for Boolean_and_expressionContext<'input>
{
    fn enter(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.enter_every_rule(self);
        listener.enter_boolean_and_expression(self);
    }
    fn exit(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.exit_boolean_and_expression(self);
        listener.exit_every_rule(self);
    }
}

impl<'input, 'a> Visitable<dyn SceneMaxNextGenVisitor<'input> + 'a>
    for Boolean_and_expressionContext<'input>
{
    fn accept(&self, visitor: &mut (dyn SceneMaxNextGenVisitor<'input> + 'a)) {
        visitor.visit_boolean_and_expression(self);
    }
}

impl<'input> CustomRuleContext<'input> for Boolean_and_expressionContextExt<'input> {
    type TF = LocalTokenFactory<'input>;
    type Ctx = SceneMaxNextGenParserContextType;
    fn get_rule_index(&self) -> usize {
        RULE_boolean_and_expression
    }
    //fn type_rule_index() -> usize where Self: Sized { RULE_boolean_and_expression }
}
antlr_rust::tid! {Boolean_and_expressionContextExt<'a>}

impl<'input> Boolean_and_expressionContextExt<'input> {
    fn new(
        parent: Option<Rc<dyn SceneMaxNextGenParserContext<'input> + 'input>>,
        invoking_state: isize,
    ) -> Rc<Boolean_and_expressionContextAll<'input>> {
        Rc::new(BaseParserRuleContext::new_parser_ctx(
            parent,
            invoking_state,
            Boolean_and_expressionContextExt { ph: PhantomData },
        ))
    }
}

pub trait Boolean_and_expressionContextAttrs<'input>:
    SceneMaxNextGenParserContext<'input> + BorrowMut<Boolean_and_expressionContextExt<'input>>
{
    fn relational_expression_all(&self) -> Vec<Rc<Relational_expressionContextAll<'input>>>
    where
        Self: Sized,
    {
        self.children_of_type()
    }
    fn relational_expression(&self, i: usize) -> Option<Rc<Relational_expressionContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(i)
    }
    /// Retrieves all `TerminalNode`s corresponding to token AND in current rule
    fn AND_all(&self) -> Vec<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.children_of_type()
    }
    /// Retrieves 'i's TerminalNode corresponding to token AND, starting from 0.
    /// Returns `None` if number of children corresponding to token AND is less or equal than `i`.
    fn AND(&self, i: usize) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(AND, i)
    }
}

impl<'input> Boolean_and_expressionContextAttrs<'input> for Boolean_and_expressionContext<'input> {}

impl<'input, I, H> SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input>> + TidAble<'input>,
    H: ErrorStrategy<'input, BaseParserType<'input, I>>,
{
    pub fn boolean_and_expression(
        &mut self,
    ) -> Result<Rc<Boolean_and_expressionContextAll<'input>>, ANTLRError> {
        let mut recog = self;
        let _parentctx = recog.ctx.take();
        let mut _localctx =
            Boolean_and_expressionContextExt::new(_parentctx.clone(), recog.base.get_state());
        recog
            .base
            .enter_rule(_localctx.clone(), 52, RULE_boolean_and_expression);
        let mut _localctx: Rc<Boolean_and_expressionContextAll> = _localctx;
        let mut _la: isize = -1;
        let result: Result<(), ANTLRError> = (|| {
            //recog.base.enter_outer_alt(_localctx.clone(), 1);
            recog.base.enter_outer_alt(None, 1);
            {
                /*InvokeRule relational_expression*/
                recog.base.set_state(301);
                recog.relational_expression()?;

                recog.base.set_state(306);
                recog.err_handler.sync(&mut recog.base)?;
                _la = recog.base.input.la(1);
                while _la == AND {
                    {
                        {
                            recog.base.set_state(302);
                            recog.base.match_token(AND, &mut recog.err_handler)?;

                            /*InvokeRule relational_expression*/
                            recog.base.set_state(303);
                            recog.relational_expression()?;
                        }
                    }
                    recog.base.set_state(308);
                    recog.err_handler.sync(&mut recog.base)?;
                    _la = recog.base.input.la(1);
                }
            }
            Ok(())
        })();
        match result {
            Ok(_) => {}
            Err(e @ ANTLRError::FallThrough(_)) => return Err(e),
            Err(ref re) => {
                //_localctx.exception = re;
                recog.err_handler.report_error(&mut recog.base, re);
                recog.err_handler.recover(&mut recog.base, re)?;
            }
        }
        recog.base.exit_rule();

        Ok(_localctx)
    }
}
//------------------- relational_expression ----------------
pub type Relational_expressionContextAll<'input> = Relational_expressionContext<'input>;

pub type Relational_expressionContext<'input> =
    BaseParserRuleContext<'input, Relational_expressionContextExt<'input>>;

#[derive(Clone)]
pub struct Relational_expressionContextExt<'input> {
    ph: PhantomData<&'input str>,
}

impl<'input> SceneMaxNextGenParserContext<'input> for Relational_expressionContext<'input> {}

impl<'input, 'a> Listenable<dyn SceneMaxNextGenListener<'input> + 'a>
    for Relational_expressionContext<'input>
{
    fn enter(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.enter_every_rule(self);
        listener.enter_relational_expression(self);
    }
    fn exit(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.exit_relational_expression(self);
        listener.exit_every_rule(self);
    }
}

impl<'input, 'a> Visitable<dyn SceneMaxNextGenVisitor<'input> + 'a>
    for Relational_expressionContext<'input>
{
    fn accept(&self, visitor: &mut (dyn SceneMaxNextGenVisitor<'input> + 'a)) {
        visitor.visit_relational_expression(self);
    }
}

impl<'input> CustomRuleContext<'input> for Relational_expressionContextExt<'input> {
    type TF = LocalTokenFactory<'input>;
    type Ctx = SceneMaxNextGenParserContextType;
    fn get_rule_index(&self) -> usize {
        RULE_relational_expression
    }
    //fn type_rule_index() -> usize where Self: Sized { RULE_relational_expression }
}
antlr_rust::tid! {Relational_expressionContextExt<'a>}

impl<'input> Relational_expressionContextExt<'input> {
    fn new(
        parent: Option<Rc<dyn SceneMaxNextGenParserContext<'input> + 'input>>,
        invoking_state: isize,
    ) -> Rc<Relational_expressionContextAll<'input>> {
        Rc::new(BaseParserRuleContext::new_parser_ctx(
            parent,
            invoking_state,
            Relational_expressionContextExt { ph: PhantomData },
        ))
    }
}

pub trait Relational_expressionContextAttrs<'input>:
    SceneMaxNextGenParserContext<'input> + BorrowMut<Relational_expressionContextExt<'input>>
{
    fn additive_expression_all(&self) -> Vec<Rc<Additive_expressionContextAll<'input>>>
    where
        Self: Sized,
    {
        self.children_of_type()
    }
    fn additive_expression(&self, i: usize) -> Option<Rc<Additive_expressionContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(i)
    }
    /// Retrieves all `TerminalNode`s corresponding to token LT in current rule
    fn LT_all(&self) -> Vec<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.children_of_type()
    }
    /// Retrieves 'i's TerminalNode corresponding to token LT, starting from 0.
    /// Returns `None` if number of children corresponding to token LT is less or equal than `i`.
    fn LT(&self, i: usize) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(LT, i)
    }
    /// Retrieves all `TerminalNode`s corresponding to token LTEQ in current rule
    fn LTEQ_all(&self) -> Vec<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.children_of_type()
    }
    /// Retrieves 'i's TerminalNode corresponding to token LTEQ, starting from 0.
    /// Returns `None` if number of children corresponding to token LTEQ is less or equal than `i`.
    fn LTEQ(&self, i: usize) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(LTEQ, i)
    }
    /// Retrieves all `TerminalNode`s corresponding to token GT in current rule
    fn GT_all(&self) -> Vec<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.children_of_type()
    }
    /// Retrieves 'i's TerminalNode corresponding to token GT, starting from 0.
    /// Returns `None` if number of children corresponding to token GT is less or equal than `i`.
    fn GT(&self, i: usize) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(GT, i)
    }
    /// Retrieves all `TerminalNode`s corresponding to token GTEQ in current rule
    fn GTEQ_all(&self) -> Vec<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.children_of_type()
    }
    /// Retrieves 'i's TerminalNode corresponding to token GTEQ, starting from 0.
    /// Returns `None` if number of children corresponding to token GTEQ is less or equal than `i`.
    fn GTEQ(&self, i: usize) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(GTEQ, i)
    }
    /// Retrieves all `TerminalNode`s corresponding to token EQUALS in current rule
    fn EQUALS_all(&self) -> Vec<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.children_of_type()
    }
    /// Retrieves 'i's TerminalNode corresponding to token EQUALS, starting from 0.
    /// Returns `None` if number of children corresponding to token EQUALS is less or equal than `i`.
    fn EQUALS(&self, i: usize) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(EQUALS, i)
    }
    /// Retrieves all `TerminalNode`s corresponding to token NOTEQUALS in current rule
    fn NOTEQUALS_all(&self) -> Vec<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.children_of_type()
    }
    /// Retrieves 'i's TerminalNode corresponding to token NOTEQUALS, starting from 0.
    /// Returns `None` if number of children corresponding to token NOTEQUALS is less or equal than `i`.
    fn NOTEQUALS(
        &self,
        i: usize,
    ) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(NOTEQUALS, i)
    }
}

impl<'input> Relational_expressionContextAttrs<'input> for Relational_expressionContext<'input> {}

impl<'input, I, H> SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input>> + TidAble<'input>,
    H: ErrorStrategy<'input, BaseParserType<'input, I>>,
{
    pub fn relational_expression(
        &mut self,
    ) -> Result<Rc<Relational_expressionContextAll<'input>>, ANTLRError> {
        let mut recog = self;
        let _parentctx = recog.ctx.take();
        let mut _localctx =
            Relational_expressionContextExt::new(_parentctx.clone(), recog.base.get_state());
        recog
            .base
            .enter_rule(_localctx.clone(), 54, RULE_relational_expression);
        let mut _localctx: Rc<Relational_expressionContextAll> = _localctx;
        let mut _la: isize = -1;
        let result: Result<(), ANTLRError> = (|| {
            //recog.base.enter_outer_alt(_localctx.clone(), 1);
            recog.base.enter_outer_alt(None, 1);
            {
                /*InvokeRule additive_expression*/
                recog.base.set_state(309);
                recog.additive_expression()?;

                recog.base.set_state(314);
                recog.err_handler.sync(&mut recog.base)?;
                _la = recog.base.input.la(1);
                while (((_la - 41) & !0x3f) == 0
                    && ((1usize << (_la - 41))
                        & ((1usize << (EQUALS - 41))
                            | (1usize << (NOTEQUALS - 41))
                            | (1usize << (LTEQ - 41))
                            | (1usize << (GTEQ - 41))
                            | (1usize << (LT - 41))
                            | (1usize << (GT - 41))))
                        != 0)
                {
                    {
                        {
                            recog.base.set_state(310);
                            _la = recog.base.input.la(1);
                            if {
                                !(((_la - 41) & !0x3f) == 0
                                    && ((1usize << (_la - 41))
                                        & ((1usize << (EQUALS - 41))
                                            | (1usize << (NOTEQUALS - 41))
                                            | (1usize << (LTEQ - 41))
                                            | (1usize << (GTEQ - 41))
                                            | (1usize << (LT - 41))
                                            | (1usize << (GT - 41))))
                                        != 0)
                            } {
                                recog.err_handler.recover_inline(&mut recog.base)?;
                            } else {
                                if recog.base.input.la(1) == TOKEN_EOF {
                                    recog.base.matched_eof = true
                                };
                                recog.err_handler.report_match(&mut recog.base);
                                recog.base.consume(&mut recog.err_handler);
                            }
                            /*InvokeRule additive_expression*/
                            recog.base.set_state(311);
                            recog.additive_expression()?;
                        }
                    }
                    recog.base.set_state(316);
                    recog.err_handler.sync(&mut recog.base)?;
                    _la = recog.base.input.la(1);
                }
            }
            Ok(())
        })();
        match result {
            Ok(_) => {}
            Err(e @ ANTLRError::FallThrough(_)) => return Err(e),
            Err(ref re) => {
                //_localctx.exception = re;
                recog.err_handler.report_error(&mut recog.base, re);
                recog.err_handler.recover(&mut recog.base, re)?;
            }
        }
        recog.base.exit_rule();

        Ok(_localctx)
    }
}
//------------------- additive_expression ----------------
pub type Additive_expressionContextAll<'input> = Additive_expressionContext<'input>;

pub type Additive_expressionContext<'input> =
    BaseParserRuleContext<'input, Additive_expressionContextExt<'input>>;

#[derive(Clone)]
pub struct Additive_expressionContextExt<'input> {
    ph: PhantomData<&'input str>,
}

impl<'input> SceneMaxNextGenParserContext<'input> for Additive_expressionContext<'input> {}

impl<'input, 'a> Listenable<dyn SceneMaxNextGenListener<'input> + 'a>
    for Additive_expressionContext<'input>
{
    fn enter(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.enter_every_rule(self);
        listener.enter_additive_expression(self);
    }
    fn exit(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.exit_additive_expression(self);
        listener.exit_every_rule(self);
    }
}

impl<'input, 'a> Visitable<dyn SceneMaxNextGenVisitor<'input> + 'a>
    for Additive_expressionContext<'input>
{
    fn accept(&self, visitor: &mut (dyn SceneMaxNextGenVisitor<'input> + 'a)) {
        visitor.visit_additive_expression(self);
    }
}

impl<'input> CustomRuleContext<'input> for Additive_expressionContextExt<'input> {
    type TF = LocalTokenFactory<'input>;
    type Ctx = SceneMaxNextGenParserContextType;
    fn get_rule_index(&self) -> usize {
        RULE_additive_expression
    }
    //fn type_rule_index() -> usize where Self: Sized { RULE_additive_expression }
}
antlr_rust::tid! {Additive_expressionContextExt<'a>}

impl<'input> Additive_expressionContextExt<'input> {
    fn new(
        parent: Option<Rc<dyn SceneMaxNextGenParserContext<'input> + 'input>>,
        invoking_state: isize,
    ) -> Rc<Additive_expressionContextAll<'input>> {
        Rc::new(BaseParserRuleContext::new_parser_ctx(
            parent,
            invoking_state,
            Additive_expressionContextExt { ph: PhantomData },
        ))
    }
}

pub trait Additive_expressionContextAttrs<'input>:
    SceneMaxNextGenParserContext<'input> + BorrowMut<Additive_expressionContextExt<'input>>
{
    fn multiplicative_expression_all(&self) -> Vec<Rc<Multiplicative_expressionContextAll<'input>>>
    where
        Self: Sized,
    {
        self.children_of_type()
    }
    fn multiplicative_expression(
        &self,
        i: usize,
    ) -> Option<Rc<Multiplicative_expressionContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(i)
    }
    /// Retrieves all `TerminalNode`s corresponding to token PLUS in current rule
    fn PLUS_all(&self) -> Vec<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.children_of_type()
    }
    /// Retrieves 'i's TerminalNode corresponding to token PLUS, starting from 0.
    /// Returns `None` if number of children corresponding to token PLUS is less or equal than `i`.
    fn PLUS(&self, i: usize) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(PLUS, i)
    }
    /// Retrieves all `TerminalNode`s corresponding to token MINUS in current rule
    fn MINUS_all(&self) -> Vec<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.children_of_type()
    }
    /// Retrieves 'i's TerminalNode corresponding to token MINUS, starting from 0.
    /// Returns `None` if number of children corresponding to token MINUS is less or equal than `i`.
    fn MINUS(&self, i: usize) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(MINUS, i)
    }
}

impl<'input> Additive_expressionContextAttrs<'input> for Additive_expressionContext<'input> {}

impl<'input, I, H> SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input>> + TidAble<'input>,
    H: ErrorStrategy<'input, BaseParserType<'input, I>>,
{
    pub fn additive_expression(
        &mut self,
    ) -> Result<Rc<Additive_expressionContextAll<'input>>, ANTLRError> {
        let mut recog = self;
        let _parentctx = recog.ctx.take();
        let mut _localctx =
            Additive_expressionContextExt::new(_parentctx.clone(), recog.base.get_state());
        recog
            .base
            .enter_rule(_localctx.clone(), 56, RULE_additive_expression);
        let mut _localctx: Rc<Additive_expressionContextAll> = _localctx;
        let mut _la: isize = -1;
        let result: Result<(), ANTLRError> = (|| {
            //recog.base.enter_outer_alt(_localctx.clone(), 1);
            recog.base.enter_outer_alt(None, 1);
            {
                /*InvokeRule multiplicative_expression*/
                recog.base.set_state(317);
                recog.multiplicative_expression()?;

                recog.base.set_state(322);
                recog.err_handler.sync(&mut recog.base)?;
                _la = recog.base.input.la(1);
                while _la == PLUS || _la == MINUS {
                    {
                        {
                            recog.base.set_state(318);
                            _la = recog.base.input.la(1);
                            if { !(_la == PLUS || _la == MINUS) } {
                                recog.err_handler.recover_inline(&mut recog.base)?;
                            } else {
                                if recog.base.input.la(1) == TOKEN_EOF {
                                    recog.base.matched_eof = true
                                };
                                recog.err_handler.report_match(&mut recog.base);
                                recog.base.consume(&mut recog.err_handler);
                            }
                            /*InvokeRule multiplicative_expression*/
                            recog.base.set_state(319);
                            recog.multiplicative_expression()?;
                        }
                    }
                    recog.base.set_state(324);
                    recog.err_handler.sync(&mut recog.base)?;
                    _la = recog.base.input.la(1);
                }
            }
            Ok(())
        })();
        match result {
            Ok(_) => {}
            Err(e @ ANTLRError::FallThrough(_)) => return Err(e),
            Err(ref re) => {
                //_localctx.exception = re;
                recog.err_handler.report_error(&mut recog.base, re);
                recog.err_handler.recover(&mut recog.base, re)?;
            }
        }
        recog.base.exit_rule();

        Ok(_localctx)
    }
}
//------------------- multiplicative_expression ----------------
pub type Multiplicative_expressionContextAll<'input> = Multiplicative_expressionContext<'input>;

pub type Multiplicative_expressionContext<'input> =
    BaseParserRuleContext<'input, Multiplicative_expressionContextExt<'input>>;

#[derive(Clone)]
pub struct Multiplicative_expressionContextExt<'input> {
    ph: PhantomData<&'input str>,
}

impl<'input> SceneMaxNextGenParserContext<'input> for Multiplicative_expressionContext<'input> {}

impl<'input, 'a> Listenable<dyn SceneMaxNextGenListener<'input> + 'a>
    for Multiplicative_expressionContext<'input>
{
    fn enter(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.enter_every_rule(self);
        listener.enter_multiplicative_expression(self);
    }
    fn exit(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.exit_multiplicative_expression(self);
        listener.exit_every_rule(self);
    }
}

impl<'input, 'a> Visitable<dyn SceneMaxNextGenVisitor<'input> + 'a>
    for Multiplicative_expressionContext<'input>
{
    fn accept(&self, visitor: &mut (dyn SceneMaxNextGenVisitor<'input> + 'a)) {
        visitor.visit_multiplicative_expression(self);
    }
}

impl<'input> CustomRuleContext<'input> for Multiplicative_expressionContextExt<'input> {
    type TF = LocalTokenFactory<'input>;
    type Ctx = SceneMaxNextGenParserContextType;
    fn get_rule_index(&self) -> usize {
        RULE_multiplicative_expression
    }
    //fn type_rule_index() -> usize where Self: Sized { RULE_multiplicative_expression }
}
antlr_rust::tid! {Multiplicative_expressionContextExt<'a>}

impl<'input> Multiplicative_expressionContextExt<'input> {
    fn new(
        parent: Option<Rc<dyn SceneMaxNextGenParserContext<'input> + 'input>>,
        invoking_state: isize,
    ) -> Rc<Multiplicative_expressionContextAll<'input>> {
        Rc::new(BaseParserRuleContext::new_parser_ctx(
            parent,
            invoking_state,
            Multiplicative_expressionContextExt { ph: PhantomData },
        ))
    }
}

pub trait Multiplicative_expressionContextAttrs<'input>:
    SceneMaxNextGenParserContext<'input> + BorrowMut<Multiplicative_expressionContextExt<'input>>
{
    fn unary_expression_all(&self) -> Vec<Rc<Unary_expressionContextAll<'input>>>
    where
        Self: Sized,
    {
        self.children_of_type()
    }
    fn unary_expression(&self, i: usize) -> Option<Rc<Unary_expressionContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(i)
    }
    /// Retrieves all `TerminalNode`s corresponding to token MULT in current rule
    fn MULT_all(&self) -> Vec<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.children_of_type()
    }
    /// Retrieves 'i's TerminalNode corresponding to token MULT, starting from 0.
    /// Returns `None` if number of children corresponding to token MULT is less or equal than `i`.
    fn MULT(&self, i: usize) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(MULT, i)
    }
    /// Retrieves all `TerminalNode`s corresponding to token DIV in current rule
    fn DIV_all(&self) -> Vec<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.children_of_type()
    }
    /// Retrieves 'i's TerminalNode corresponding to token DIV, starting from 0.
    /// Returns `None` if number of children corresponding to token DIV is less or equal than `i`.
    fn DIV(&self, i: usize) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(DIV, i)
    }
    /// Retrieves all `TerminalNode`s corresponding to token MOD in current rule
    fn MOD_all(&self) -> Vec<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.children_of_type()
    }
    /// Retrieves 'i's TerminalNode corresponding to token MOD, starting from 0.
    /// Returns `None` if number of children corresponding to token MOD is less or equal than `i`.
    fn MOD(&self, i: usize) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(MOD, i)
    }
}

impl<'input> Multiplicative_expressionContextAttrs<'input>
    for Multiplicative_expressionContext<'input>
{
}

impl<'input, I, H> SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input>> + TidAble<'input>,
    H: ErrorStrategy<'input, BaseParserType<'input, I>>,
{
    pub fn multiplicative_expression(
        &mut self,
    ) -> Result<Rc<Multiplicative_expressionContextAll<'input>>, ANTLRError> {
        let mut recog = self;
        let _parentctx = recog.ctx.take();
        let mut _localctx =
            Multiplicative_expressionContextExt::new(_parentctx.clone(), recog.base.get_state());
        recog
            .base
            .enter_rule(_localctx.clone(), 58, RULE_multiplicative_expression);
        let mut _localctx: Rc<Multiplicative_expressionContextAll> = _localctx;
        let mut _la: isize = -1;
        let result: Result<(), ANTLRError> = (|| {
            //recog.base.enter_outer_alt(_localctx.clone(), 1);
            recog.base.enter_outer_alt(None, 1);
            {
                /*InvokeRule unary_expression*/
                recog.base.set_state(325);
                recog.unary_expression()?;

                recog.base.set_state(330);
                recog.err_handler.sync(&mut recog.base)?;
                _la = recog.base.input.la(1);
                while (((_la - 49) & !0x3f) == 0
                    && ((1usize << (_la - 49))
                        & ((1usize << (MULT - 49))
                            | (1usize << (DIV - 49))
                            | (1usize << (MOD - 49))))
                        != 0)
                {
                    {
                        {
                            recog.base.set_state(326);
                            _la = recog.base.input.la(1);
                            if {
                                !(((_la - 49) & !0x3f) == 0
                                    && ((1usize << (_la - 49))
                                        & ((1usize << (MULT - 49))
                                            | (1usize << (DIV - 49))
                                            | (1usize << (MOD - 49))))
                                        != 0)
                            } {
                                recog.err_handler.recover_inline(&mut recog.base)?;
                            } else {
                                if recog.base.input.la(1) == TOKEN_EOF {
                                    recog.base.matched_eof = true
                                };
                                recog.err_handler.report_match(&mut recog.base);
                                recog.base.consume(&mut recog.err_handler);
                            }
                            /*InvokeRule unary_expression*/
                            recog.base.set_state(327);
                            recog.unary_expression()?;
                        }
                    }
                    recog.base.set_state(332);
                    recog.err_handler.sync(&mut recog.base)?;
                    _la = recog.base.input.la(1);
                }
            }
            Ok(())
        })();
        match result {
            Ok(_) => {}
            Err(e @ ANTLRError::FallThrough(_)) => return Err(e),
            Err(ref re) => {
                //_localctx.exception = re;
                recog.err_handler.report_error(&mut recog.base, re);
                recog.err_handler.recover(&mut recog.base, re)?;
            }
        }
        recog.base.exit_rule();

        Ok(_localctx)
    }
}
//------------------- unary_expression ----------------
pub type Unary_expressionContextAll<'input> = Unary_expressionContext<'input>;

pub type Unary_expressionContext<'input> =
    BaseParserRuleContext<'input, Unary_expressionContextExt<'input>>;

#[derive(Clone)]
pub struct Unary_expressionContextExt<'input> {
    ph: PhantomData<&'input str>,
}

impl<'input> SceneMaxNextGenParserContext<'input> for Unary_expressionContext<'input> {}

impl<'input, 'a> Listenable<dyn SceneMaxNextGenListener<'input> + 'a>
    for Unary_expressionContext<'input>
{
    fn enter(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.enter_every_rule(self);
        listener.enter_unary_expression(self);
    }
    fn exit(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.exit_unary_expression(self);
        listener.exit_every_rule(self);
    }
}

impl<'input, 'a> Visitable<dyn SceneMaxNextGenVisitor<'input> + 'a>
    for Unary_expressionContext<'input>
{
    fn accept(&self, visitor: &mut (dyn SceneMaxNextGenVisitor<'input> + 'a)) {
        visitor.visit_unary_expression(self);
    }
}

impl<'input> CustomRuleContext<'input> for Unary_expressionContextExt<'input> {
    type TF = LocalTokenFactory<'input>;
    type Ctx = SceneMaxNextGenParserContextType;
    fn get_rule_index(&self) -> usize {
        RULE_unary_expression
    }
    //fn type_rule_index() -> usize where Self: Sized { RULE_unary_expression }
}
antlr_rust::tid! {Unary_expressionContextExt<'a>}

impl<'input> Unary_expressionContextExt<'input> {
    fn new(
        parent: Option<Rc<dyn SceneMaxNextGenParserContext<'input> + 'input>>,
        invoking_state: isize,
    ) -> Rc<Unary_expressionContextAll<'input>> {
        Rc::new(BaseParserRuleContext::new_parser_ctx(
            parent,
            invoking_state,
            Unary_expressionContextExt { ph: PhantomData },
        ))
    }
}

pub trait Unary_expressionContextAttrs<'input>:
    SceneMaxNextGenParserContext<'input> + BorrowMut<Unary_expressionContextExt<'input>>
{
    fn primary_expression(&self) -> Option<Rc<Primary_expressionContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(0)
    }
    /// Retrieves first TerminalNode corresponding to token NOT
    /// Returns `None` if there is no child corresponding to token NOT
    fn NOT(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(NOT, 0)
    }
}

impl<'input> Unary_expressionContextAttrs<'input> for Unary_expressionContext<'input> {}

impl<'input, I, H> SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input>> + TidAble<'input>,
    H: ErrorStrategy<'input, BaseParserType<'input, I>>,
{
    pub fn unary_expression(
        &mut self,
    ) -> Result<Rc<Unary_expressionContextAll<'input>>, ANTLRError> {
        let mut recog = self;
        let _parentctx = recog.ctx.take();
        let mut _localctx =
            Unary_expressionContextExt::new(_parentctx.clone(), recog.base.get_state());
        recog
            .base
            .enter_rule(_localctx.clone(), 60, RULE_unary_expression);
        let mut _localctx: Rc<Unary_expressionContextAll> = _localctx;
        let mut _la: isize = -1;
        let result: Result<(), ANTLRError> = (|| {
            //recog.base.enter_outer_alt(_localctx.clone(), 1);
            recog.base.enter_outer_alt(None, 1);
            {
                recog.base.set_state(334);
                recog.err_handler.sync(&mut recog.base)?;
                _la = recog.base.input.la(1);
                if _la == NOT {
                    {
                        recog.base.set_state(333);
                        recog.base.match_token(NOT, &mut recog.err_handler)?;
                    }
                }

                /*InvokeRule primary_expression*/
                recog.base.set_state(336);
                recog.primary_expression()?;
            }
            Ok(())
        })();
        match result {
            Ok(_) => {}
            Err(e @ ANTLRError::FallThrough(_)) => return Err(e),
            Err(ref re) => {
                //_localctx.exception = re;
                recog.err_handler.report_error(&mut recog.base, re);
                recog.err_handler.recover(&mut recog.base, re)?;
            }
        }
        recog.base.exit_rule();

        Ok(_localctx)
    }
}
//------------------- primary_expression ----------------
pub type Primary_expressionContextAll<'input> = Primary_expressionContext<'input>;

pub type Primary_expressionContext<'input> =
    BaseParserRuleContext<'input, Primary_expressionContextExt<'input>>;

#[derive(Clone)]
pub struct Primary_expressionContextExt<'input> {
    ph: PhantomData<&'input str>,
}

impl<'input> SceneMaxNextGenParserContext<'input> for Primary_expressionContext<'input> {}

impl<'input, 'a> Listenable<dyn SceneMaxNextGenListener<'input> + 'a>
    for Primary_expressionContext<'input>
{
    fn enter(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.enter_every_rule(self);
        listener.enter_primary_expression(self);
    }
    fn exit(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.exit_primary_expression(self);
        listener.exit_every_rule(self);
    }
}

impl<'input, 'a> Visitable<dyn SceneMaxNextGenVisitor<'input> + 'a>
    for Primary_expressionContext<'input>
{
    fn accept(&self, visitor: &mut (dyn SceneMaxNextGenVisitor<'input> + 'a)) {
        visitor.visit_primary_expression(self);
    }
}

impl<'input> CustomRuleContext<'input> for Primary_expressionContextExt<'input> {
    type TF = LocalTokenFactory<'input>;
    type Ctx = SceneMaxNextGenParserContextType;
    fn get_rule_index(&self) -> usize {
        RULE_primary_expression
    }
    //fn type_rule_index() -> usize where Self: Sized { RULE_primary_expression }
}
antlr_rust::tid! {Primary_expressionContextExt<'a>}

impl<'input> Primary_expressionContextExt<'input> {
    fn new(
        parent: Option<Rc<dyn SceneMaxNextGenParserContext<'input> + 'input>>,
        invoking_state: isize,
    ) -> Rc<Primary_expressionContextAll<'input>> {
        Rc::new(BaseParserRuleContext::new_parser_ctx(
            parent,
            invoking_state,
            Primary_expressionContextExt { ph: PhantomData },
        ))
    }
}

pub trait Primary_expressionContextAttrs<'input>:
    SceneMaxNextGenParserContext<'input> + BorrowMut<Primary_expressionContextExt<'input>>
{
    /// Retrieves first TerminalNode corresponding to token LPAREN
    /// Returns `None` if there is no child corresponding to token LPAREN
    fn LPAREN(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(LPAREN, 0)
    }
    fn logical_expression(&self) -> Option<Rc<Logical_expressionContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(0)
    }
    /// Retrieves first TerminalNode corresponding to token RPAREN
    /// Returns `None` if there is no child corresponding to token RPAREN
    fn RPAREN(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(RPAREN, 0)
    }
    fn function_value(&self) -> Option<Rc<Function_valueContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(0)
    }
    fn value(&self) -> Option<Rc<ValueContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(0)
    }
}

impl<'input> Primary_expressionContextAttrs<'input> for Primary_expressionContext<'input> {}

impl<'input, I, H> SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input>> + TidAble<'input>,
    H: ErrorStrategy<'input, BaseParserType<'input, I>>,
{
    pub fn primary_expression(
        &mut self,
    ) -> Result<Rc<Primary_expressionContextAll<'input>>, ANTLRError> {
        let mut recog = self;
        let _parentctx = recog.ctx.take();
        let mut _localctx =
            Primary_expressionContextExt::new(_parentctx.clone(), recog.base.get_state());
        recog
            .base
            .enter_rule(_localctx.clone(), 62, RULE_primary_expression);
        let mut _localctx: Rc<Primary_expressionContextAll> = _localctx;
        let result: Result<(), ANTLRError> = (|| {
            recog.base.set_state(344);
            recog.err_handler.sync(&mut recog.base)?;
            match recog.interpreter.adaptive_predict(36, &mut recog.base)? {
                1 => {
                    //recog.base.enter_outer_alt(_localctx.clone(), 1);
                    recog.base.enter_outer_alt(None, 1);
                    {
                        recog.base.set_state(338);
                        recog.base.match_token(LPAREN, &mut recog.err_handler)?;

                        /*InvokeRule logical_expression*/
                        recog.base.set_state(339);
                        recog.logical_expression()?;

                        recog.base.set_state(340);
                        recog.base.match_token(RPAREN, &mut recog.err_handler)?;
                    }
                }
                2 => {
                    //recog.base.enter_outer_alt(_localctx.clone(), 2);
                    recog.base.enter_outer_alt(None, 2);
                    {
                        /*InvokeRule function_value*/
                        recog.base.set_state(342);
                        recog.function_value()?;
                    }
                }
                3 => {
                    //recog.base.enter_outer_alt(_localctx.clone(), 3);
                    recog.base.enter_outer_alt(None, 3);
                    {
                        /*InvokeRule value*/
                        recog.base.set_state(343);
                        recog.value()?;
                    }
                }

                _ => {}
            }
            Ok(())
        })();
        match result {
            Ok(_) => {}
            Err(e @ ANTLRError::FallThrough(_)) => return Err(e),
            Err(ref re) => {
                //_localctx.exception = re;
                recog.err_handler.report_error(&mut recog.base, re);
                recog.err_handler.recover(&mut recog.base, re)?;
            }
        }
        recog.base.exit_rule();

        Ok(_localctx)
    }
}
//------------------- function_value ----------------
pub type Function_valueContextAll<'input> = Function_valueContext<'input>;

pub type Function_valueContext<'input> =
    BaseParserRuleContext<'input, Function_valueContextExt<'input>>;

#[derive(Clone)]
pub struct Function_valueContextExt<'input> {
    ph: PhantomData<&'input str>,
}

impl<'input> SceneMaxNextGenParserContext<'input> for Function_valueContext<'input> {}

impl<'input, 'a> Listenable<dyn SceneMaxNextGenListener<'input> + 'a>
    for Function_valueContext<'input>
{
    fn enter(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.enter_every_rule(self);
        listener.enter_function_value(self);
    }
    fn exit(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.exit_function_value(self);
        listener.exit_every_rule(self);
    }
}

impl<'input, 'a> Visitable<dyn SceneMaxNextGenVisitor<'input> + 'a>
    for Function_valueContext<'input>
{
    fn accept(&self, visitor: &mut (dyn SceneMaxNextGenVisitor<'input> + 'a)) {
        visitor.visit_function_value(self);
    }
}

impl<'input> CustomRuleContext<'input> for Function_valueContextExt<'input> {
    type TF = LocalTokenFactory<'input>;
    type Ctx = SceneMaxNextGenParserContextType;
    fn get_rule_index(&self) -> usize {
        RULE_function_value
    }
    //fn type_rule_index() -> usize where Self: Sized { RULE_function_value }
}
antlr_rust::tid! {Function_valueContextExt<'a>}

impl<'input> Function_valueContextExt<'input> {
    fn new(
        parent: Option<Rc<dyn SceneMaxNextGenParserContext<'input> + 'input>>,
        invoking_state: isize,
    ) -> Rc<Function_valueContextAll<'input>> {
        Rc::new(BaseParserRuleContext::new_parser_ctx(
            parent,
            invoking_state,
            Function_valueContextExt { ph: PhantomData },
        ))
    }
}

pub trait Function_valueContextAttrs<'input>:
    SceneMaxNextGenParserContext<'input> + BorrowMut<Function_valueContextExt<'input>>
{
    fn qualified_name(&self) -> Option<Rc<Qualified_nameContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(0)
    }
    /// Retrieves first TerminalNode corresponding to token LPAREN
    /// Returns `None` if there is no child corresponding to token LPAREN
    fn LPAREN(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(LPAREN, 0)
    }
    /// Retrieves first TerminalNode corresponding to token RPAREN
    /// Returns `None` if there is no child corresponding to token RPAREN
    fn RPAREN(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(RPAREN, 0)
    }
    fn logical_expression_all(&self) -> Vec<Rc<Logical_expressionContextAll<'input>>>
    where
        Self: Sized,
    {
        self.children_of_type()
    }
    fn logical_expression(&self, i: usize) -> Option<Rc<Logical_expressionContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(i)
    }
    /// Retrieves all `TerminalNode`s corresponding to token COMMA in current rule
    fn COMMA_all(&self) -> Vec<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.children_of_type()
    }
    /// Retrieves 'i's TerminalNode corresponding to token COMMA, starting from 0.
    /// Returns `None` if number of children corresponding to token COMMA is less or equal than `i`.
    fn COMMA(&self, i: usize) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(COMMA, i)
    }
}

impl<'input> Function_valueContextAttrs<'input> for Function_valueContext<'input> {}

impl<'input, I, H> SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input>> + TidAble<'input>,
    H: ErrorStrategy<'input, BaseParserType<'input, I>>,
{
    pub fn function_value(&mut self) -> Result<Rc<Function_valueContextAll<'input>>, ANTLRError> {
        let mut recog = self;
        let _parentctx = recog.ctx.take();
        let mut _localctx =
            Function_valueContextExt::new(_parentctx.clone(), recog.base.get_state());
        recog
            .base
            .enter_rule(_localctx.clone(), 64, RULE_function_value);
        let mut _localctx: Rc<Function_valueContextAll> = _localctx;
        let mut _la: isize = -1;
        let result: Result<(), ANTLRError> = (|| {
            //recog.base.enter_outer_alt(_localctx.clone(), 1);
            recog.base.enter_outer_alt(None, 1);
            {
                /*InvokeRule qualified_name*/
                recog.base.set_state(346);
                recog.qualified_name()?;

                recog.base.set_state(347);
                recog.base.match_token(LPAREN, &mut recog.err_handler)?;

                recog.base.set_state(356);
                recog.err_handler.sync(&mut recog.base)?;
                _la = recog.base.input.la(1);
                if (((_la) & !0x3f) == 0
                    && ((1usize << _la)
                        & ((1usize << LOOP)
                            | (1usize << ADD)
                            | (1usize << CODE)
                            | (1usize << AT)
                            | (1usize << SPEED)
                            | (1usize << OF)
                            | (1usize << MOVE)
                            | (1usize << FORWARD)
                            | (1usize << BACKWARD)
                            | (1usize << BACK)
                            | (1usize << LEFT)
                            | (1usize << RIGHT)
                            | (1usize << UP)
                            | (1usize << DOWN)
                            | (1usize << IN)
                            | (1usize << FOR)
                            | (1usize << SECONDS)
                            | (1usize << TO)
                            | (1usize << TURN)
                            | (1usize << ROTATE)
                            | (1usize << RUN)
                            | (1usize << EVERY)
                            | (1usize << ASYNC)
                            | (1usize << ANIMATION)
                            | (1usize << PLAY)
                            | (1usize << FRAME)
                            | (1usize << CHARACTER)
                            | (1usize << JUMP)
                            | (1usize << PHYSICS)
                            | (1usize << IMPULSE)))
                        != 0)
                    || (((_la - 32) & !0x3f) == 0
                        && ((1usize << (_la - 32))
                            & ((1usize << (WHEN - 32))
                                | (1usize << (TRUE - 32))
                                | (1usize << (FALSE - 32))
                                | (1usize << (LPAREN - 32))
                                | (1usize << (PLUS - 32))
                                | (1usize << (MINUS - 32))
                                | (1usize << (NOT - 32))
                                | (1usize << (DECIMAL - 32))
                                | (1usize << (IDENT - 32))
                                | (1usize << (QUOTED_STRING - 32))))
                            != 0)
                {
                    {
                        /*InvokeRule logical_expression*/
                        recog.base.set_state(348);
                        recog.logical_expression()?;

                        recog.base.set_state(353);
                        recog.err_handler.sync(&mut recog.base)?;
                        _la = recog.base.input.la(1);
                        while _la == COMMA {
                            {
                                {
                                    recog.base.set_state(349);
                                    recog.base.match_token(COMMA, &mut recog.err_handler)?;

                                    /*InvokeRule logical_expression*/
                                    recog.base.set_state(350);
                                    recog.logical_expression()?;
                                }
                            }
                            recog.base.set_state(355);
                            recog.err_handler.sync(&mut recog.base)?;
                            _la = recog.base.input.la(1);
                        }
                    }
                }

                recog.base.set_state(358);
                recog.base.match_token(RPAREN, &mut recog.err_handler)?;
            }
            Ok(())
        })();
        match result {
            Ok(_) => {}
            Err(e @ ANTLRError::FallThrough(_)) => return Err(e),
            Err(ref re) => {
                //_localctx.exception = re;
                recog.err_handler.report_error(&mut recog.base, re);
                recog.err_handler.recover(&mut recog.base, re)?;
            }
        }
        recog.base.exit_rule();

        Ok(_localctx)
    }
}
//------------------- value ----------------
pub type ValueContextAll<'input> = ValueContext<'input>;

pub type ValueContext<'input> = BaseParserRuleContext<'input, ValueContextExt<'input>>;

#[derive(Clone)]
pub struct ValueContextExt<'input> {
    ph: PhantomData<&'input str>,
}

impl<'input> SceneMaxNextGenParserContext<'input> for ValueContext<'input> {}

impl<'input, 'a> Listenable<dyn SceneMaxNextGenListener<'input> + 'a> for ValueContext<'input> {
    fn enter(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.enter_every_rule(self);
        listener.enter_value(self);
    }
    fn exit(&self, listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
        listener.exit_value(self);
        listener.exit_every_rule(self);
    }
}

impl<'input, 'a> Visitable<dyn SceneMaxNextGenVisitor<'input> + 'a> for ValueContext<'input> {
    fn accept(&self, visitor: &mut (dyn SceneMaxNextGenVisitor<'input> + 'a)) {
        visitor.visit_value(self);
    }
}

impl<'input> CustomRuleContext<'input> for ValueContextExt<'input> {
    type TF = LocalTokenFactory<'input>;
    type Ctx = SceneMaxNextGenParserContextType;
    fn get_rule_index(&self) -> usize {
        RULE_value
    }
    //fn type_rule_index() -> usize where Self: Sized { RULE_value }
}
antlr_rust::tid! {ValueContextExt<'a>}

impl<'input> ValueContextExt<'input> {
    fn new(
        parent: Option<Rc<dyn SceneMaxNextGenParserContext<'input> + 'input>>,
        invoking_state: isize,
    ) -> Rc<ValueContextAll<'input>> {
        Rc::new(BaseParserRuleContext::new_parser_ctx(
            parent,
            invoking_state,
            ValueContextExt { ph: PhantomData },
        ))
    }
}

pub trait ValueContextAttrs<'input>:
    SceneMaxNextGenParserContext<'input> + BorrowMut<ValueContextExt<'input>>
{
    fn number(&self) -> Option<Rc<NumberContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(0)
    }
    /// Retrieves first TerminalNode corresponding to token QUOTED_STRING
    /// Returns `None` if there is no child corresponding to token QUOTED_STRING
    fn QUOTED_STRING(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(QUOTED_STRING, 0)
    }
    /// Retrieves first TerminalNode corresponding to token TRUE
    /// Returns `None` if there is no child corresponding to token TRUE
    fn TRUE(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(TRUE, 0)
    }
    /// Retrieves first TerminalNode corresponding to token FALSE
    /// Returns `None` if there is no child corresponding to token FALSE
    fn FALSE(&self) -> Option<Rc<TerminalNode<'input, SceneMaxNextGenParserContextType>>>
    where
        Self: Sized,
    {
        self.get_token(FALSE, 0)
    }
    fn qualified_name(&self) -> Option<Rc<Qualified_nameContextAll<'input>>>
    where
        Self: Sized,
    {
        self.child_of_type(0)
    }
}

impl<'input> ValueContextAttrs<'input> for ValueContext<'input> {}

impl<'input, I, H> SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input>> + TidAble<'input>,
    H: ErrorStrategy<'input, BaseParserType<'input, I>>,
{
    pub fn value(&mut self) -> Result<Rc<ValueContextAll<'input>>, ANTLRError> {
        let mut recog = self;
        let _parentctx = recog.ctx.take();
        let mut _localctx = ValueContextExt::new(_parentctx.clone(), recog.base.get_state());
        recog.base.enter_rule(_localctx.clone(), 66, RULE_value);
        let mut _localctx: Rc<ValueContextAll> = _localctx;
        let result: Result<(), ANTLRError> = (|| {
            recog.base.set_state(365);
            recog.err_handler.sync(&mut recog.base)?;
            match recog.interpreter.adaptive_predict(39, &mut recog.base)? {
                1 => {
                    //recog.base.enter_outer_alt(_localctx.clone(), 1);
                    recog.base.enter_outer_alt(None, 1);
                    {
                        /*InvokeRule number*/
                        recog.base.set_state(360);
                        recog.number()?;
                    }
                }
                2 => {
                    //recog.base.enter_outer_alt(_localctx.clone(), 2);
                    recog.base.enter_outer_alt(None, 2);
                    {
                        recog.base.set_state(361);
                        recog
                            .base
                            .match_token(QUOTED_STRING, &mut recog.err_handler)?;
                    }
                }
                3 => {
                    //recog.base.enter_outer_alt(_localctx.clone(), 3);
                    recog.base.enter_outer_alt(None, 3);
                    {
                        recog.base.set_state(362);
                        recog.base.match_token(TRUE, &mut recog.err_handler)?;
                    }
                }
                4 => {
                    //recog.base.enter_outer_alt(_localctx.clone(), 4);
                    recog.base.enter_outer_alt(None, 4);
                    {
                        recog.base.set_state(363);
                        recog.base.match_token(FALSE, &mut recog.err_handler)?;
                    }
                }
                5 => {
                    //recog.base.enter_outer_alt(_localctx.clone(), 5);
                    recog.base.enter_outer_alt(None, 5);
                    {
                        /*InvokeRule qualified_name*/
                        recog.base.set_state(364);
                        recog.qualified_name()?;
                    }
                }

                _ => {}
            }
            Ok(())
        })();
        match result {
            Ok(_) => {}
            Err(e @ ANTLRError::FallThrough(_)) => return Err(e),
            Err(ref re) => {
                //_localctx.exception = re;
                recog.err_handler.report_error(&mut recog.base, re);
                recog.err_handler.recover(&mut recog.base, re)?;
            }
        }
        recog.base.exit_rule();

        Ok(_localctx)
    }
}

lazy_static! {
    static ref _ATN: Arc<ATN> =
        Arc::new(ATNDeserializer::new(None).deserialize(_serializedATN.chars()));
    static ref _decision_to_DFA: Arc<Vec<antlr_rust::RwLock<DFA>>> = {
        let mut dfa = Vec::new();
        let size = _ATN.decision_to_state.len();
        for i in 0..size {
            dfa.push(DFA::new(_ATN.clone(), _ATN.get_decision_state(i), i as isize).into())
        }
        Arc::new(dfa)
    };
}

const _serializedATN: &'static str = "\x03\u{608b}\u{a72a}\u{8133}\u{b9ed}\u{417c}\u{3be7}\u{7786}\u{5964}\x03\
	\x3c\u{172}\x04\x02\x09\x02\x04\x03\x09\x03\x04\x04\x09\x04\x04\x05\x09\
	\x05\x04\x06\x09\x06\x04\x07\x09\x07\x04\x08\x09\x08\x04\x09\x09\x09\x04\
	\x0a\x09\x0a\x04\x0b\x09\x0b\x04\x0c\x09\x0c\x04\x0d\x09\x0d\x04\x0e\x09\
	\x0e\x04\x0f\x09\x0f\x04\x10\x09\x10\x04\x11\x09\x11\x04\x12\x09\x12\x04\
	\x13\x09\x13\x04\x14\x09\x14\x04\x15\x09\x15\x04\x16\x09\x16\x04\x17\x09\
	\x17\x04\x18\x09\x18\x04\x19\x09\x19\x04\x1a\x09\x1a\x04\x1b\x09\x1b\x04\
	\x1c\x09\x1c\x04\x1d\x09\x1d\x04\x1e\x09\x1e\x04\x1f\x09\x1f\x04\x20\x09\
	\x20\x04\x21\x09\x21\x04\x22\x09\x22\x04\x23\x09\x23\x03\x02\x07\x02\x48\
	\x0a\x02\x0c\x02\x0e\x02\x4b\x0b\x02\x03\x02\x03\x02\x03\x03\x03\x03\x03\
	\x03\x03\x03\x03\x03\x03\x03\x03\x03\x03\x03\x03\x03\x03\x03\x03\x03\x03\
	\x03\x03\x03\x05\x03\x5c\x0a\x03\x03\x04\x03\x04\x03\x04\x03\x04\x03\x05\
	\x03\x05\x03\x05\x03\x05\x05\x05\x66\x0a\x05\x03\x05\x05\x05\x69\x0a\x05\
	\x03\x06\x03\x06\x03\x06\x03\x06\x03\x06\x03\x07\x03\x07\x03\x07\x03\x07\
	\x03\x07\x03\x07\x05\x07\x76\x0a\x07\x03\x07\x05\x07\x79\x0a\x07\x03\x08\
	\x03\x08\x03\x08\x03\x08\x03\x08\x03\x08\x03\x08\x03\x08\x05\x08\u{83}\x0a\
	\x08\x03\x08\x05\x08\u{86}\x0a\x08\x03\x09\x03\x09\x03\x09\x03\x09\x03\x09\
	\x03\x09\x03\x09\x03\x09\x03\x09\x03\x09\x03\x09\x05\x09\u{93}\x0a\x09\x03\
	\x0a\x03\x0a\x03\x0a\x03\x0a\x05\x0a\u{99}\x0a\x0a\x03\x0a\x03\x0a\x05\x0a\
	\u{9d}\x0a\x0a\x03\x0a\x05\x0a\u{a0}\x0a\x0a\x03\x0a\x05\x0a\u{a3}\x0a\x0a\
	\x03\x0b\x03\x0b\x03\x0b\x03\x0b\x03\x0b\x03\x0b\x03\x0b\x03\x0b\x03\x0b\
	\x05\x0b\u{ae}\x0a\x0b\x03\x0b\x05\x0b\u{b1}\x0a\x0b\x03\x0b\x05\x0b\u{b4}\
	\x0a\x0b\x03\x0c\x03\x0c\x03\x0d\x03\x0d\x03\x0d\x03\x0d\x03\x0e\x03\x0e\
	\x03\x0e\x05\x0e\u{bf}\x0a\x0e\x03\x0f\x03\x0f\x03\x0f\x03\x0f\x03\x0f\x03\
	\x0f\x03\x10\x03\x10\x03\x10\x03\x10\x03\x10\x07\x10\u{cc}\x0a\x10\x0c\x10\
	\x0e\x10\u{cf}\x0b\x10\x05\x10\u{d1}\x0a\x10\x03\x10\x03\x10\x03\x10\x05\
	\x10\u{d6}\x0a\x10\x03\x11\x03\x11\x03\x11\x03\x11\x03\x11\x03\x11\x03\x11\
	\x03\x11\x03\x11\x05\x11\u{e1}\x0a\x11\x03\x11\x03\x11\x05\x11\u{e5}\x0a\
	\x11\x03\x12\x03\x12\x03\x12\x03\x12\x03\x12\x03\x12\x03\x12\x03\x12\x05\
	\x12\u{ef}\x0a\x12\x03\x12\x05\x12\u{f2}\x0a\x12\x03\x13\x03\x13\x03\x13\
	\x03\x13\x03\x13\x03\x13\x03\x13\x03\x13\x03\x13\x03\x13\x03\x13\x05\x13\
	\u{ff}\x0a\x13\x03\x13\x03\x13\x05\x13\u{103}\x0a\x13\x03\x14\x03\x14\x03\
	\x14\x03\x14\x03\x14\x03\x14\x03\x14\x03\x15\x03\x15\x03\x15\x03\x15\x03\
	\x16\x03\x16\x03\x16\x07\x16\u{113}\x0a\x16\x0c\x16\x0e\x16\u{116}\x0b\x16\
	\x03\x17\x03\x17\x05\x17\u{11a}\x0a\x17\x03\x18\x03\x18\x03\x18\x05\x18\
	\u{11f}\x0a\x18\x03\x19\x03\x19\x03\x1a\x05\x1a\u{124}\x0a\x1a\x03\x1a\x03\
	\x1a\x03\x1b\x03\x1b\x03\x1b\x07\x1b\u{12b}\x0a\x1b\x0c\x1b\x0e\x1b\u{12e}\
	\x0b\x1b\x03\x1c\x03\x1c\x03\x1c\x07\x1c\u{133}\x0a\x1c\x0c\x1c\x0e\x1c\
	\u{136}\x0b\x1c\x03\x1d\x03\x1d\x03\x1d\x07\x1d\u{13b}\x0a\x1d\x0c\x1d\x0e\
	\x1d\u{13e}\x0b\x1d\x03\x1e\x03\x1e\x03\x1e\x07\x1e\u{143}\x0a\x1e\x0c\x1e\
	\x0e\x1e\u{146}\x0b\x1e\x03\x1f\x03\x1f\x03\x1f\x07\x1f\u{14b}\x0a\x1f\x0c\
	\x1f\x0e\x1f\u{14e}\x0b\x1f\x03\x20\x05\x20\u{151}\x0a\x20\x03\x20\x03\x20\
	\x03\x21\x03\x21\x03\x21\x03\x21\x03\x21\x03\x21\x05\x21\u{15b}\x0a\x21\
	\x03\x22\x03\x22\x03\x22\x03\x22\x03\x22\x07\x22\u{162}\x0a\x22\x0c\x22\
	\x0e\x22\u{165}\x0b\x22\x05\x22\u{167}\x0a\x22\x03\x22\x03\x22\x03\x23\x03\
	\x23\x03\x23\x03\x23\x03\x23\x05\x23\u{170}\x0a\x23\x03\x23\x02\x02\x24\
	\x02\x04\x06\x08\x0a\x0c\x0e\x10\x12\x14\x16\x18\x1a\x1c\x1e\x20\x22\x24\
	\x26\x28\x2a\x2c\x2e\x30\x32\x34\x36\x38\x3a\x3c\x3e\x40\x42\x44\x02\x09\
	\x03\x02\x0e\x0f\x03\x02\x31\x32\x03\x02\x0b\x11\x03\x02\x12\x13\x03\x02\
	\x04\x24\x03\x02\x2b\x30\x03\x02\x33\x35\x02\u{189}\x02\x49\x03\x02\x02\
	\x02\x04\x5b\x03\x02\x02\x02\x06\x5d\x03\x02\x02\x02\x08\x61\x03\x02\x02\
	\x02\x0a\x6a\x03\x02\x02\x02\x0c\x6f\x03\x02\x02\x02\x0e\x7a\x03\x02\x02\
	\x02\x10\u{92}\x03\x02\x02\x02\x12\u{94}\x03\x02\x02\x02\x14\u{a4}\x03\x02\
	\x02\x02\x16\u{b5}\x03\x02\x02\x02\x18\u{b7}\x03\x02\x02\x02\x1a\u{bb}\x03\
	\x02\x02\x02\x1c\u{c0}\x03\x02\x02\x02\x1e\u{d5}\x03\x02\x02\x02\x20\u{d7}\
	\x03\x02\x02\x02\x22\u{e6}\x03\x02\x02\x02\x24\u{f3}\x03\x02\x02\x02\x26\
	\u{104}\x03\x02\x02\x02\x28\u{10b}\x03\x02\x02\x02\x2a\u{10f}\x03\x02\x02\
	\x02\x2c\u{119}\x03\x02\x02\x02\x2e\u{11e}\x03\x02\x02\x02\x30\u{120}\x03\
	\x02\x02\x02\x32\u{123}\x03\x02\x02\x02\x34\u{127}\x03\x02\x02\x02\x36\u{12f}\
	\x03\x02\x02\x02\x38\u{137}\x03\x02\x02\x02\x3a\u{13f}\x03\x02\x02\x02\x3c\
	\u{147}\x03\x02\x02\x02\x3e\u{150}\x03\x02\x02\x02\x40\u{15a}\x03\x02\x02\
	\x02\x42\u{15c}\x03\x02\x02\x02\x44\u{16f}\x03\x02\x02\x02\x46\x48\x05\x04\
	\x03\x02\x47\x46\x03\x02\x02\x02\x48\x4b\x03\x02\x02\x02\x49\x47\x03\x02\
	\x02\x02\x49\x4a\x03\x02\x02\x02\x4a\x4c\x03\x02\x02\x02\x4b\x49\x03\x02\
	\x02\x02\x4c\x4d\x07\x02\x02\x03\x4d\x03\x03\x02\x02\x02\x4e\x5c\x05\x06\
	\x04\x02\x4f\x5c\x05\x08\x05\x02\x50\x5c\x05\x0c\x07\x02\x51\x5c\x05\x0e\
	\x08\x02\x52\x5c\x05\x12\x0a\x02\x53\x5c\x05\x14\x0b\x02\x54\x5c\x05\x1a\
	\x0e\x02\x55\x5c\x05\x1c\x0f\x02\x56\x5c\x05\x20\x11\x02\x57\x5c\x05\x22\
	\x12\x02\x58\x5c\x05\x24\x13\x02\x59\x5c\x05\x26\x14\x02\x5a\x5c\x05\x28\
	\x15\x02\x5b\x4e\x03\x02\x02\x02\x5b\x4f\x03\x02\x02\x02\x5b\x50\x03\x02\
	\x02\x02\x5b\x51\x03\x02\x02\x02\x5b\x52\x03\x02\x02\x02\x5b\x53\x03\x02\
	\x02\x02\x5b\x54\x03\x02\x02\x02\x5b\x55\x03\x02\x02\x02\x5b\x56\x03\x02\
	\x02\x02\x5b\x57\x03\x02\x02\x02\x5b\x58\x03\x02\x02\x02\x5b\x59\x03\x02\
	\x02\x02\x5b\x5a\x03\x02\x02\x02\x5c\x05\x03\x02\x02\x02\x5d\x5e\x07\x38\
	\x02\x02\x5e\x5f\x07\x03\x02\x02\x5f\x60\x05\x2a\x16\x02\x60\x07\x03\x02\
	\x02\x02\x61\x62\x07\x38\x02\x02\x62\x63\x07\x25\x02\x02\x63\x65\x05\x2e\
	\x18\x02\x64\x66\x05\x0a\x06\x02\x65\x64\x03\x02\x02\x02\x65\x66\x03\x02\
	\x02\x02\x66\x68\x03\x02\x02\x02\x67\x69\x07\x04\x02\x02\x68\x67\x03\x02\
	\x02\x02\x68\x69\x03\x02\x02\x02\x69\x09\x03\x02\x02\x02\x6a\x6b\x07\x07\
	\x02\x02\x6b\x6c\x07\x08\x02\x02\x6c\x6d\x07\x09\x02\x02\x6d\x6e\x05\x34\
	\x1b\x02\x6e\x0b\x03\x02\x02\x02\x6f\x70\x07\x38\x02\x02\x70\x71\x07\x25\
	\x02\x02\x71\x72\x07\x0a\x02\x02\x72\x73\x05\x16\x0c\x02\x73\x75\x05\x34\
	\x1b\x02\x74\x76\x05\x18\x0d\x02\x75\x74\x03\x02\x02\x02\x75\x76\x03\x02\
	\x02\x02\x76\x78\x03\x02\x02\x02\x77\x79\x07\x04\x02\x02\x78\x77\x03\x02\
	\x02\x02\x78\x79\x03\x02\x02\x02\x79\x0d\x03\x02\x02\x02\x7a\x7b\x07\x38\
	\x02\x02\x7b\x7c\x07\x25\x02\x02\x7c\x7d\x07\x0a\x02\x02\x7d\x7e\x07\x15\
	\x02\x02\x7e\x7f\x07\x26\x02\x02\x7f\u{80}\x05\x10\x09\x02\u{80}\u{82}\x07\
	\x27\x02\x02\u{81}\u{83}\x05\x18\x0d\x02\u{82}\u{81}\x03\x02\x02\x02\u{82}\
	\u{83}\x03\x02\x02\x02\u{83}\u{85}\x03\x02\x02\x02\u{84}\u{86}\x07\x1a\x02\
	\x02\u{85}\u{84}\x03\x02\x02\x02\u{85}\u{86}\x03\x02\x02\x02\u{86}\x0f\x03\
	\x02\x02\x02\u{87}\u{88}\x05\x34\x1b\x02\u{88}\u{89}\x07\x28\x02\x02\u{89}\
	\u{8a}\x05\x34\x1b\x02\u{8a}\u{8b}\x07\x28\x02\x02\u{8b}\u{8c}\x05\x34\x1b\
	\x02\u{8c}\u{93}\x03\x02\x02\x02\u{8d}\u{8e}\x05\x2a\x16\x02\u{8e}\u{8f}\
	\x07\x0b\x02\x02\u{8f}\u{90}\x05\x34\x1b\x02\u{90}\u{93}\x03\x02\x02\x02\
	\u{91}\u{93}\x05\x2a\x16\x02\u{92}\u{87}\x03\x02\x02\x02\u{92}\u{8d}\x03\
	\x02\x02\x02\u{92}\u{91}\x03\x02\x02\x02\u{93}\x11\x03\x02\x02\x02\u{94}\
	\u{95}\x07\x38\x02\x02\u{95}\u{96}\x07\x25\x02\x02\u{96}\u{98}\x07\x16\x02\
	\x02\u{97}\u{99}\x09\x02\x02\x02\u{98}\u{97}\x03\x02\x02\x02\u{98}\u{99}\
	\x03\x02\x02\x02\u{99}\u{9a}\x03\x02\x02\x02\u{9a}\u{9c}\x05\x34\x1b\x02\
	\u{9b}\u{9d}\x05\x18\x0d\x02\u{9c}\u{9b}\x03\x02\x02\x02\u{9c}\u{9d}\x03\
	\x02\x02\x02\u{9d}\u{9f}\x03\x02\x02\x02\u{9e}\u{a0}\x07\x04\x02\x02\u{9f}\
	\u{9e}\x03\x02\x02\x02\u{9f}\u{a0}\x03\x02\x02\x02\u{a0}\u{a2}\x03\x02\x02\
	\x02\u{a1}\u{a3}\x07\x1a\x02\x02\u{a2}\u{a1}\x03\x02\x02\x02\u{a2}\u{a3}\
	\x03\x02\x02\x02\u{a3}\x13\x03\x02\x02\x02\u{a4}\u{a5}\x07\x38\x02\x02\u{a5}\
	\u{a6}\x07\x25\x02\x02\u{a6}\u{a7}\x07\x17\x02\x02\u{a7}\u{a8}\x07\x26\x02\
	\x02\u{a8}\u{a9}\x05\x2a\x16\x02\u{a9}\u{aa}\x09\x03\x02\x02\u{aa}\u{ab}\
	\x05\x34\x1b\x02\u{ab}\u{ad}\x07\x27\x02\x02\u{ac}\u{ae}\x05\x18\x0d\x02\
	\u{ad}\u{ac}\x03\x02\x02\x02\u{ad}\u{ae}\x03\x02\x02\x02\u{ae}\u{b0}\x03\
	\x02\x02\x02\u{af}\u{b1}\x07\x04\x02\x02\u{b0}\u{af}\x03\x02\x02\x02\u{b0}\
	\u{b1}\x03\x02\x02\x02\u{b1}\u{b3}\x03\x02\x02\x02\u{b2}\u{b4}\x07\x1a\x02\
	\x02\u{b3}\u{b2}\x03\x02\x02\x02\u{b3}\u{b4}\x03\x02\x02\x02\u{b4}\x15\x03\
	\x02\x02\x02\u{b5}\u{b6}\x09\x04\x02\x02\u{b6}\x17\x03\x02\x02\x02\u{b7}\
	\u{b8}\x09\x05\x02\x02\u{b8}\u{b9}\x05\x34\x1b\x02\u{b9}\u{ba}\x07\x14\x02\
	\x02\u{ba}\x19\x03\x02\x02\x02\u{bb}\u{bc}\x07\x18\x02\x02\u{bc}\u{be}\x05\
	\x1e\x10\x02\u{bd}\u{bf}\x07\x1a\x02\x02\u{be}\u{bd}\x03\x02\x02\x02\u{be}\
	\u{bf}\x03\x02\x02\x02\u{bf}\x1b\x03\x02\x02\x02\u{c0}\u{c1}\x07\x18\x02\
	\x02\u{c1}\u{c2}\x05\x1e\x10\x02\u{c2}\u{c3}\x07\x19\x02\x02\u{c3}\u{c4}\
	\x05\x34\x1b\x02\u{c4}\u{c5}\x07\x14\x02\x02\u{c5}\x1d\x03\x02\x02\x02\u{c6}\
	\u{c7}\x05\x2a\x16\x02\u{c7}\u{d0}\x07\x26\x02\x02\u{c8}\u{cd}\x05\x34\x1b\
	\x02\u{c9}\u{ca}\x07\x28\x02\x02\u{ca}\u{cc}\x05\x34\x1b\x02\u{cb}\u{c9}\
	\x03\x02\x02\x02\u{cc}\u{cf}\x03\x02\x02\x02\u{cd}\u{cb}\x03\x02\x02\x02\
	\u{cd}\u{ce}\x03\x02\x02\x02\u{ce}\u{d1}\x03\x02\x02\x02\u{cf}\u{cd}\x03\
	\x02\x02\x02\u{d0}\u{c8}\x03\x02\x02\x02\u{d0}\u{d1}\x03\x02\x02\x02\u{d1}\
	\u{d2}\x03\x02\x02\x02\u{d2}\u{d3}\x07\x27\x02\x02\u{d3}\u{d6}\x03\x02\x02\
	\x02\u{d4}\u{d6}\x05\x2a\x16\x02\u{d5}\u{c6}\x03\x02\x02\x02\u{d5}\u{d4}\
	\x03\x02\x02\x02\u{d6}\x1f\x03\x02\x02\x02\u{d7}\u{d8}\x07\x38\x02\x02\u{d8}\
	\u{d9}\x07\x25\x02\x02\u{d9}\u{da}\x07\x1b\x02\x02\u{da}\u{db}\x07\x08\x02\
	\x02\u{db}\u{e0}\x05\x34\x1b\x02\u{dc}\u{dd}\x07\x13\x02\x02\u{dd}\u{de}\
	\x05\x34\x1b\x02\u{de}\u{df}\x07\x14\x02\x02\u{df}\u{e1}\x03\x02\x02\x02\
	\u{e0}\u{dc}\x03\x02\x02\x02\u{e0}\u{e1}\x03\x02\x02\x02\u{e1}\u{e4}\x03\
	\x02\x02\x02\u{e2}\u{e3}\x07\x22\x02\x02\u{e3}\u{e5}\x05\x34\x1b\x02\u{e4}\
	\u{e2}\x03\x02\x02\x02\u{e4}\u{e5}\x03\x02\x02\x02\u{e5}\x21\x03\x02\x02\
	\x02\u{e6}\u{e7}\x07\x38\x02\x02\u{e7}\u{e8}\x07\x25\x02\x02\u{e8}\u{e9}\
	\x07\x1c\x02\x02\u{e9}\u{ea}\x07\x1d\x02\x02\u{ea}\u{eb}\x05\x34\x1b\x02\
	\u{eb}\u{ec}\x07\x15\x02\x02\u{ec}\u{ee}\x05\x34\x1b\x02\u{ed}\u{ef}\x05\
	\x18\x0d\x02\u{ee}\u{ed}\x03\x02\x02\x02\u{ee}\u{ef}\x03\x02\x02\x02\u{ef}\
	\u{f1}\x03\x02\x02\x02\u{f0}\u{f2}\x07\x04\x02\x02\u{f1}\u{f0}\x03\x02\x02\
	\x02\u{f1}\u{f2}\x03\x02\x02\x02\u{f2}\x23\x03\x02\x02\x02\u{f3}\u{f4}\x07\
	\x38\x02\x02\u{f4}\u{f5}\x07\x25\x02\x02\u{f5}\u{f6}\x07\x1e\x02\x02\u{f6}\
	\u{f7}\x07\x25\x02\x02\u{f7}\u{fe}\x07\x1f\x02\x02\u{f8}\u{f9}\x07\x07\x02\
	\x02\u{f9}\u{fa}\x07\x08\x02\x02\u{fa}\u{ff}\x07\x09\x02\x02\u{fb}\u{fc}\
	\x07\x08\x02\x02\u{fc}\u{ff}\x07\x09\x02\x02\u{fd}\u{ff}\x07\x08\x02\x02\
	\u{fe}\u{f8}\x03\x02\x02\x02\u{fe}\u{fb}\x03\x02\x02\x02\u{fe}\u{fd}\x03\
	\x02\x02\x02\u{ff}\u{100}\x03\x02\x02\x02\u{100}\u{102}\x05\x34\x1b\x02\
	\u{101}\u{103}\x07\x1a\x02\x02\u{102}\u{101}\x03\x02\x02\x02\u{102}\u{103}\
	\x03\x02\x02\x02\u{103}\x25\x03\x02\x02\x02\u{104}\u{105}\x07\x38\x02\x02\
	\u{105}\u{106}\x07\x25\x02\x02\u{106}\u{107}\x07\x20\x02\x02\u{107}\u{108}\
	\x07\x21\x02\x02\u{108}\u{109}\x05\x16\x0c\x02\u{109}\u{10a}\x05\x34\x1b\
	\x02\u{10a}\x27\x03\x02\x02\x02\u{10b}\u{10c}\x07\x05\x02\x02\u{10c}\u{10d}\
	\x07\x39\x02\x02\u{10d}\u{10e}\x07\x06\x02\x02\u{10e}\x29\x03\x02\x02\x02\
	\u{10f}\u{114}\x05\x2c\x17\x02\u{110}\u{111}\x07\x25\x02\x02\u{111}\u{113}\
	\x05\x2c\x17\x02\u{112}\u{110}\x03\x02\x02\x02\u{113}\u{116}\x03\x02\x02\
	\x02\u{114}\u{112}\x03\x02\x02\x02\u{114}\u{115}\x03\x02\x02\x02\u{115}\
	\x2b\x03\x02\x02\x02\u{116}\u{114}\x03\x02\x02\x02\u{117}\u{11a}\x07\x38\
	\x02\x02\u{118}\u{11a}\x05\x30\x19\x02\u{119}\u{117}\x03\x02\x02\x02\u{119}\
	\u{118}\x03\x02\x02\x02\u{11a}\x2d\x03\x02\x02\x02\u{11b}\u{11f}\x07\x38\
	\x02\x02\u{11c}\u{11f}\x07\x39\x02\x02\u{11d}\u{11f}\x05\x30\x19\x02\u{11e}\
	\u{11b}\x03\x02\x02\x02\u{11e}\u{11c}\x03\x02\x02\x02\u{11e}\u{11d}\x03\
	\x02\x02\x02\u{11f}\x2f\x03\x02\x02\x02\u{120}\u{121}\x09\x06\x02\x02\u{121}\
	\x31\x03\x02\x02\x02\u{122}\u{124}\x09\x03\x02\x02\u{123}\u{122}\x03\x02\
	\x02\x02\u{123}\u{124}\x03\x02\x02\x02\u{124}\u{125}\x03\x02\x02\x02\u{125}\
	\u{126}\x07\x37\x02\x02\u{126}\x33\x03\x02\x02\x02\u{127}\u{12c}\x05\x36\
	\x1c\x02\u{128}\u{129}\x07\x29\x02\x02\u{129}\u{12b}\x05\x36\x1c\x02\u{12a}\
	\u{128}\x03\x02\x02\x02\u{12b}\u{12e}\x03\x02\x02\x02\u{12c}\u{12a}\x03\
	\x02\x02\x02\u{12c}\u{12d}\x03\x02\x02\x02\u{12d}\x35\x03\x02\x02\x02\u{12e}\
	\u{12c}\x03\x02\x02\x02\u{12f}\u{134}\x05\x38\x1d\x02\u{130}\u{131}\x07\
	\x2a\x02\x02\u{131}\u{133}\x05\x38\x1d\x02\u{132}\u{130}\x03\x02\x02\x02\
	\u{133}\u{136}\x03\x02\x02\x02\u{134}\u{132}\x03\x02\x02\x02\u{134}\u{135}\
	\x03\x02\x02\x02\u{135}\x37\x03\x02\x02\x02\u{136}\u{134}\x03\x02\x02\x02\
	\u{137}\u{13c}\x05\x3a\x1e\x02\u{138}\u{139}\x09\x07\x02\x02\u{139}\u{13b}\
	\x05\x3a\x1e\x02\u{13a}\u{138}\x03\x02\x02\x02\u{13b}\u{13e}\x03\x02\x02\
	\x02\u{13c}\u{13a}\x03\x02\x02\x02\u{13c}\u{13d}\x03\x02\x02\x02\u{13d}\
	\x39\x03\x02\x02\x02\u{13e}\u{13c}\x03\x02\x02\x02\u{13f}\u{144}\x05\x3c\
	\x1f\x02\u{140}\u{141}\x09\x03\x02\x02\u{141}\u{143}\x05\x3c\x1f\x02\u{142}\
	\u{140}\x03\x02\x02\x02\u{143}\u{146}\x03\x02\x02\x02\u{144}\u{142}\x03\
	\x02\x02\x02\u{144}\u{145}\x03\x02\x02\x02\u{145}\x3b\x03\x02\x02\x02\u{146}\
	\u{144}\x03\x02\x02\x02\u{147}\u{14c}\x05\x3e\x20\x02\u{148}\u{149}\x09\
	\x08\x02\x02\u{149}\u{14b}\x05\x3e\x20\x02\u{14a}\u{148}\x03\x02\x02\x02\
	\u{14b}\u{14e}\x03\x02\x02\x02\u{14c}\u{14a}\x03\x02\x02\x02\u{14c}\u{14d}\
	\x03\x02\x02\x02\u{14d}\x3d\x03\x02\x02\x02\u{14e}\u{14c}\x03\x02\x02\x02\
	\u{14f}\u{151}\x07\x36\x02\x02\u{150}\u{14f}\x03\x02\x02\x02\u{150}\u{151}\
	\x03\x02\x02\x02\u{151}\u{152}\x03\x02\x02\x02\u{152}\u{153}\x05\x40\x21\
	\x02\u{153}\x3f\x03\x02\x02\x02\u{154}\u{155}\x07\x26\x02\x02\u{155}\u{156}\
	\x05\x34\x1b\x02\u{156}\u{157}\x07\x27\x02\x02\u{157}\u{15b}\x03\x02\x02\
	\x02\u{158}\u{15b}\x05\x42\x22\x02\u{159}\u{15b}\x05\x44\x23\x02\u{15a}\
	\u{154}\x03\x02\x02\x02\u{15a}\u{158}\x03\x02\x02\x02\u{15a}\u{159}\x03\
	\x02\x02\x02\u{15b}\x41\x03\x02\x02\x02\u{15c}\u{15d}\x05\x2a\x16\x02\u{15d}\
	\u{166}\x07\x26\x02\x02\u{15e}\u{163}\x05\x34\x1b\x02\u{15f}\u{160}\x07\
	\x28\x02\x02\u{160}\u{162}\x05\x34\x1b\x02\u{161}\u{15f}\x03\x02\x02\x02\
	\u{162}\u{165}\x03\x02\x02\x02\u{163}\u{161}\x03\x02\x02\x02\u{163}\u{164}\
	\x03\x02\x02\x02\u{164}\u{167}\x03\x02\x02\x02\u{165}\u{163}\x03\x02\x02\
	\x02\u{166}\u{15e}\x03\x02\x02\x02\u{166}\u{167}\x03\x02\x02\x02\u{167}\
	\u{168}\x03\x02\x02\x02\u{168}\u{169}\x07\x27\x02\x02\u{169}\x43\x03\x02\
	\x02\x02\u{16a}\u{170}\x05\x32\x1a\x02\u{16b}\u{170}\x07\x39\x02\x02\u{16c}\
	\u{170}\x07\x23\x02\x02\u{16d}\u{170}\x07\x24\x02\x02\u{16e}\u{170}\x05\
	\x2a\x16\x02\u{16f}\u{16a}\x03\x02\x02\x02\u{16f}\u{16b}\x03\x02\x02\x02\
	\u{16f}\u{16c}\x03\x02\x02\x02\u{16f}\u{16d}\x03\x02\x02\x02\u{16f}\u{16e}\
	\x03\x02\x02\x02\u{170}\x45\x03\x02\x02\x02\x2a\x49\x5b\x65\x68\x75\x78\
	\u{82}\u{85}\u{92}\u{98}\u{9c}\u{9f}\u{a2}\u{ad}\u{b0}\u{b3}\u{be}\u{cd}\
	\u{d0}\u{d5}\u{e0}\u{e4}\u{ee}\u{f1}\u{fe}\u{102}\u{114}\u{119}\u{11e}\u{123}\
	\u{12c}\u{134}\u{13c}\u{144}\u{14c}\u{150}\u{15a}\u{163}\u{166}\u{16f}";
