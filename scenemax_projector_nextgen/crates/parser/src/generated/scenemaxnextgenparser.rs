// Generated from SceneMaxNextGen.g4 by ANTLR 4.8
#![allow(dead_code)]
#![allow(non_snake_case)]
#![allow(non_upper_case_globals)]
#![allow(nonstandard_style)]
#![allow(unused_imports)]
#![allow(unused_mut)]
#![allow(unused_braces)]
use antlr_rust::PredictionContextCache;
use antlr_rust::parser::{Parser, BaseParser, ParserRecog, ParserNodeType};
use antlr_rust::token_stream::TokenStream;
use antlr_rust::TokenSource;
use antlr_rust::parser_atn_simulator::ParserATNSimulator;
use antlr_rust::errors::*;
use antlr_rust::rule_context::{BaseRuleContext, CustomRuleContext, RuleContext};
use antlr_rust::recognizer::{Recognizer,Actions};
use antlr_rust::atn_deserializer::ATNDeserializer;
use antlr_rust::dfa::DFA;
use antlr_rust::atn::{ATN, INVALID_ALT};
use antlr_rust::error_strategy::{ErrorStrategy, DefaultErrorStrategy};
use antlr_rust::parser_rule_context::{BaseParserRuleContext, ParserRuleContext,cast,cast_mut};
use antlr_rust::tree::*;
use antlr_rust::token::{TOKEN_EOF,OwningToken,Token};
use antlr_rust::int_stream::EOF;
use antlr_rust::vocabulary::{Vocabulary,VocabularyImpl};
use antlr_rust::token_factory::{CommonTokenFactory,TokenFactory, TokenAware};
use super::scenemaxnextgenlistener::*;
use super::scenemaxnextgenvisitor::*;

use antlr_rust::lazy_static;
use antlr_rust::{TidAble,TidExt};

use std::marker::PhantomData;
use std::sync::Arc;
use std::rc::Rc;
use std::convert::TryFrom;
use std::cell::RefCell;
use std::ops::{DerefMut, Deref};
use std::borrow::{Borrow,BorrowMut};
use std::any::{Any,TypeId};

		pub const ISA:isize=1; 
		pub const LOOP:isize=2; 
		pub const ADD:isize=3; 
		pub const CODE:isize=4; 
		pub const AT:isize=5; 
		pub const SPEED:isize=6; 
		pub const OF:isize=7; 
		pub const DOT:isize=8; 
		pub const SIGN:isize=9; 
		pub const DECIMAL:isize=10; 
		pub const IDENT:isize=11; 
		pub const QUOTED_STRING:isize=12; 
		pub const LINE_COMMENT:isize=13; 
		pub const BLOCK_COMMENT:isize=14; 
		pub const WS:isize=15;
	pub const RULE_program:usize = 0; 
	pub const RULE_statement:usize = 1; 
	pub const RULE_model_decl:usize = 2; 
	pub const RULE_animate_stmt:usize = 3; 
	pub const RULE_speed_clause:usize = 4; 
	pub const RULE_add_code_stmt:usize = 5; 
	pub const RULE_qualified_name:usize = 6; 
	pub const RULE_qualified_name_part:usize = 7; 
	pub const RULE_animation_name:usize = 8; 
	pub const RULE_keyword_identifier:usize = 9; 
	pub const RULE_number:usize = 10;
	pub const ruleNames: [&'static str; 11] =  [
		"program", "statement", "model_decl", "animate_stmt", "speed_clause", 
		"add_code_stmt", "qualified_name", "qualified_name_part", "animation_name", 
		"keyword_identifier", "number"
	];


	pub const _LITERAL_NAMES: [Option<&'static str>;9] = [
		None, None, None, None, None, None, None, None, Some("'.'")
	];
	pub const _SYMBOLIC_NAMES: [Option<&'static str>;16]  = [
		None, Some("ISA"), Some("LOOP"), Some("ADD"), Some("CODE"), Some("AT"), 
		Some("SPEED"), Some("OF"), Some("DOT"), Some("SIGN"), Some("DECIMAL"), 
		Some("IDENT"), Some("QUOTED_STRING"), Some("LINE_COMMENT"), Some("BLOCK_COMMENT"), 
		Some("WS")
	];
	lazy_static!{
	    static ref _shared_context_cache: Arc<PredictionContextCache> = Arc::new(PredictionContextCache::new());
		static ref VOCABULARY: Box<dyn Vocabulary> = Box::new(VocabularyImpl::new(_LITERAL_NAMES.iter(), _SYMBOLIC_NAMES.iter(), None));
	}


type BaseParserType<'input, I> =
	BaseParser<'input,SceneMaxNextGenParserExt<'input>, I, SceneMaxNextGenParserContextType , dyn SceneMaxNextGenListener<'input> + 'input >;

type TokenType<'input> = <LocalTokenFactory<'input> as TokenFactory<'input>>::Tok;
pub type LocalTokenFactory<'input> = CommonTokenFactory;

pub type SceneMaxNextGenTreeWalker<'input,'a> =
	ParseTreeWalker<'input, 'a, SceneMaxNextGenParserContextType , dyn SceneMaxNextGenListener<'input> + 'a>;

/// Parser for SceneMaxNextGen grammar
pub struct SceneMaxNextGenParser<'input,I,H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input> > + TidAble<'input>,
    H: ErrorStrategy<'input,BaseParserType<'input,I>>
{
	base:BaseParserType<'input,I>,
	interpreter:Arc<ParserATNSimulator>,
	_shared_context_cache: Box<PredictionContextCache>,
    pub err_handler: H,
}

impl<'input, I, H> SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input> > + TidAble<'input>,
    H: ErrorStrategy<'input,BaseParserType<'input,I>>
{
	pub fn get_serialized_atn() -> &'static str { _serializedATN }

    pub fn set_error_strategy(&mut self, strategy: H) {
        self.err_handler = strategy
    }

    pub fn with_strategy(input: I, strategy: H) -> Self {
		antlr_rust::recognizer::check_version("0","3");
		let interpreter = Arc::new(ParserATNSimulator::new(
			_ATN.clone(),
			_decision_to_DFA.clone(),
			_shared_context_cache.clone(),
		));
		Self {
			base: BaseParser::new_base_parser(
				input,
				Arc::clone(&interpreter),
				SceneMaxNextGenParserExt{
					_pd: Default::default(),
				}
			),
			interpreter,
            _shared_context_cache: Box::new(PredictionContextCache::new()),
            err_handler: strategy,
        }
    }

}

type DynStrategy<'input,I> = Box<dyn ErrorStrategy<'input,BaseParserType<'input,I>> + 'input>;

impl<'input, I> SceneMaxNextGenParser<'input, I, DynStrategy<'input,I>>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input> > + TidAble<'input>,
{
    pub fn with_dyn_strategy(input: I) -> Self{
    	Self::with_strategy(input,Box::new(DefaultErrorStrategy::new()))
    }
}

impl<'input, I> SceneMaxNextGenParser<'input, I, DefaultErrorStrategy<'input,SceneMaxNextGenParserContextType>>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input> > + TidAble<'input>,
{
    pub fn new(input: I) -> Self{
    	Self::with_strategy(input,DefaultErrorStrategy::new())
    }
}

/// Trait for monomorphized trait object that corresponds to the nodes of parse tree generated for SceneMaxNextGenParser
pub trait SceneMaxNextGenParserContext<'input>:
	for<'x> Listenable<dyn SceneMaxNextGenListener<'input> + 'x > + 
	for<'x> Visitable<dyn SceneMaxNextGenVisitor<'input> + 'x > + 
	ParserRuleContext<'input, TF=LocalTokenFactory<'input>, Ctx=SceneMaxNextGenParserContextType>
{}

antlr_rust::coerce_from!{ 'input : SceneMaxNextGenParserContext<'input> }

impl<'input, 'x, T> VisitableDyn<T> for dyn SceneMaxNextGenParserContext<'input> + 'input
where
    T: SceneMaxNextGenVisitor<'input> + 'x,
{
    fn accept_dyn(&self, visitor: &mut T) {
        self.accept(visitor as &mut (dyn SceneMaxNextGenVisitor<'input> + 'x))
    }
}

impl<'input> SceneMaxNextGenParserContext<'input> for TerminalNode<'input,SceneMaxNextGenParserContextType> {}
impl<'input> SceneMaxNextGenParserContext<'input> for ErrorNode<'input,SceneMaxNextGenParserContextType> {}

antlr_rust::tid! { impl<'input> TidAble<'input> for dyn SceneMaxNextGenParserContext<'input> + 'input }

antlr_rust::tid! { impl<'input> TidAble<'input> for dyn SceneMaxNextGenListener<'input> + 'input }

pub struct SceneMaxNextGenParserContextType;
antlr_rust::tid!{SceneMaxNextGenParserContextType}

impl<'input> ParserNodeType<'input> for SceneMaxNextGenParserContextType{
	type TF = LocalTokenFactory<'input>;
	type Type = dyn SceneMaxNextGenParserContext<'input> + 'input;
}

impl<'input, I, H> Deref for SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input> > + TidAble<'input>,
    H: ErrorStrategy<'input,BaseParserType<'input,I>>
{
    type Target = BaseParserType<'input,I>;

    fn deref(&self) -> &Self::Target {
        &self.base
    }
}

impl<'input, I, H> DerefMut for SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input> > + TidAble<'input>,
    H: ErrorStrategy<'input,BaseParserType<'input,I>>
{
    fn deref_mut(&mut self) -> &mut Self::Target {
        &mut self.base
    }
}

pub struct SceneMaxNextGenParserExt<'input>{
	_pd: PhantomData<&'input str>,
}

impl<'input> SceneMaxNextGenParserExt<'input>{
}
antlr_rust::tid! { SceneMaxNextGenParserExt<'a> }

impl<'input> TokenAware<'input> for SceneMaxNextGenParserExt<'input>{
	type TF = LocalTokenFactory<'input>;
}

impl<'input,I: TokenStream<'input, TF = LocalTokenFactory<'input> > + TidAble<'input>> ParserRecog<'input, BaseParserType<'input,I>> for SceneMaxNextGenParserExt<'input>{}

impl<'input,I: TokenStream<'input, TF = LocalTokenFactory<'input> > + TidAble<'input>> Actions<'input, BaseParserType<'input,I>> for SceneMaxNextGenParserExt<'input>{
	fn get_grammar_file_name(&self) -> & str{ "SceneMaxNextGen.g4"}

   	fn get_rule_names(&self) -> &[& str] {&ruleNames}

   	fn get_vocabulary(&self) -> &dyn Vocabulary { &**VOCABULARY }
}
//------------------- program ----------------
pub type ProgramContextAll<'input> = ProgramContext<'input>;


pub type ProgramContext<'input> = BaseParserRuleContext<'input,ProgramContextExt<'input>>;

#[derive(Clone)]
pub struct ProgramContextExt<'input>{
ph:PhantomData<&'input str>
}

impl<'input> SceneMaxNextGenParserContext<'input> for ProgramContext<'input>{}

impl<'input,'a> Listenable<dyn SceneMaxNextGenListener<'input> + 'a> for ProgramContext<'input>{
		fn enter(&self,listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
			listener.enter_every_rule(self);
			listener.enter_program(self);
		}
		fn exit(&self,listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
			listener.exit_program(self);
			listener.exit_every_rule(self);
		}
}

impl<'input,'a> Visitable<dyn SceneMaxNextGenVisitor<'input> + 'a> for ProgramContext<'input>{
	fn accept(&self,visitor: &mut (dyn SceneMaxNextGenVisitor<'input> + 'a)) {
		visitor.visit_program(self);
	}
}

impl<'input> CustomRuleContext<'input> for ProgramContextExt<'input>{
	type TF = LocalTokenFactory<'input>;
	type Ctx = SceneMaxNextGenParserContextType;
	fn get_rule_index(&self) -> usize { RULE_program }
	//fn type_rule_index() -> usize where Self: Sized { RULE_program }
}
antlr_rust::tid!{ProgramContextExt<'a>}

impl<'input> ProgramContextExt<'input>{
	fn new(parent: Option<Rc<dyn SceneMaxNextGenParserContext<'input> + 'input > >, invoking_state: isize) -> Rc<ProgramContextAll<'input>> {
		Rc::new(
			BaseParserRuleContext::new_parser_ctx(parent, invoking_state,ProgramContextExt{
				ph:PhantomData
			}),
		)
	}
}

pub trait ProgramContextAttrs<'input>: SceneMaxNextGenParserContext<'input> + BorrowMut<ProgramContextExt<'input>>{

/// Retrieves first TerminalNode corresponding to token EOF
/// Returns `None` if there is no child corresponding to token EOF
fn EOF(&self) -> Option<Rc<TerminalNode<'input,SceneMaxNextGenParserContextType>>> where Self:Sized{
	self.get_token(EOF, 0)
}
fn statement_all(&self) ->  Vec<Rc<StatementContextAll<'input>>> where Self:Sized{
	self.children_of_type()
}
fn statement(&self, i: usize) -> Option<Rc<StatementContextAll<'input>>> where Self:Sized{
	self.child_of_type(i)
}

}

impl<'input> ProgramContextAttrs<'input> for ProgramContext<'input>{}

impl<'input, I, H> SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input> > + TidAble<'input>,
    H: ErrorStrategy<'input,BaseParserType<'input,I>>
{
	pub fn program(&mut self,)
	-> Result<Rc<ProgramContextAll<'input>>,ANTLRError> {
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
			recog.base.set_state(25);
			recog.err_handler.sync(&mut recog.base)?;
			_la = recog.base.input.la(1);
			while _la==ADD || _la==IDENT {
				{
				{
				/*InvokeRule statement*/
				recog.base.set_state(22);
				recog.statement()?;

				}
				}
				recog.base.set_state(27);
				recog.err_handler.sync(&mut recog.base)?;
				_la = recog.base.input.la(1);
			}
			recog.base.set_state(28);
			recog.base.match_token(EOF,&mut recog.err_handler)?;

			}
			Ok(())
		})();
		match result {
		Ok(_)=>{},
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


pub type StatementContext<'input> = BaseParserRuleContext<'input,StatementContextExt<'input>>;

#[derive(Clone)]
pub struct StatementContextExt<'input>{
ph:PhantomData<&'input str>
}

impl<'input> SceneMaxNextGenParserContext<'input> for StatementContext<'input>{}

impl<'input,'a> Listenable<dyn SceneMaxNextGenListener<'input> + 'a> for StatementContext<'input>{
		fn enter(&self,listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
			listener.enter_every_rule(self);
			listener.enter_statement(self);
		}
		fn exit(&self,listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
			listener.exit_statement(self);
			listener.exit_every_rule(self);
		}
}

impl<'input,'a> Visitable<dyn SceneMaxNextGenVisitor<'input> + 'a> for StatementContext<'input>{
	fn accept(&self,visitor: &mut (dyn SceneMaxNextGenVisitor<'input> + 'a)) {
		visitor.visit_statement(self);
	}
}

impl<'input> CustomRuleContext<'input> for StatementContextExt<'input>{
	type TF = LocalTokenFactory<'input>;
	type Ctx = SceneMaxNextGenParserContextType;
	fn get_rule_index(&self) -> usize { RULE_statement }
	//fn type_rule_index() -> usize where Self: Sized { RULE_statement }
}
antlr_rust::tid!{StatementContextExt<'a>}

impl<'input> StatementContextExt<'input>{
	fn new(parent: Option<Rc<dyn SceneMaxNextGenParserContext<'input> + 'input > >, invoking_state: isize) -> Rc<StatementContextAll<'input>> {
		Rc::new(
			BaseParserRuleContext::new_parser_ctx(parent, invoking_state,StatementContextExt{
				ph:PhantomData
			}),
		)
	}
}

pub trait StatementContextAttrs<'input>: SceneMaxNextGenParserContext<'input> + BorrowMut<StatementContextExt<'input>>{

fn model_decl(&self) -> Option<Rc<Model_declContextAll<'input>>> where Self:Sized{
	self.child_of_type(0)
}
fn animate_stmt(&self) -> Option<Rc<Animate_stmtContextAll<'input>>> where Self:Sized{
	self.child_of_type(0)
}
fn add_code_stmt(&self) -> Option<Rc<Add_code_stmtContextAll<'input>>> where Self:Sized{
	self.child_of_type(0)
}

}

impl<'input> StatementContextAttrs<'input> for StatementContext<'input>{}

impl<'input, I, H> SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input> > + TidAble<'input>,
    H: ErrorStrategy<'input,BaseParserType<'input,I>>
{
	pub fn statement(&mut self,)
	-> Result<Rc<StatementContextAll<'input>>,ANTLRError> {
		let mut recog = self;
		let _parentctx = recog.ctx.take();
		let mut _localctx = StatementContextExt::new(_parentctx.clone(), recog.base.get_state());
        recog.base.enter_rule(_localctx.clone(), 2, RULE_statement);
        let mut _localctx: Rc<StatementContextAll> = _localctx;
		let result: Result<(), ANTLRError> = (|| {

			recog.base.set_state(33);
			recog.err_handler.sync(&mut recog.base)?;
			match  recog.interpreter.adaptive_predict(1,&mut recog.base)? {
				1 =>{
					//recog.base.enter_outer_alt(_localctx.clone(), 1);
					recog.base.enter_outer_alt(None, 1);
					{
					/*InvokeRule model_decl*/
					recog.base.set_state(30);
					recog.model_decl()?;

					}
				}
			,
				2 =>{
					//recog.base.enter_outer_alt(_localctx.clone(), 2);
					recog.base.enter_outer_alt(None, 2);
					{
					/*InvokeRule animate_stmt*/
					recog.base.set_state(31);
					recog.animate_stmt()?;

					}
				}
			,
				3 =>{
					//recog.base.enter_outer_alt(_localctx.clone(), 3);
					recog.base.enter_outer_alt(None, 3);
					{
					/*InvokeRule add_code_stmt*/
					recog.base.set_state(32);
					recog.add_code_stmt()?;

					}
				}

				_ => {}
			}
			Ok(())
		})();
		match result {
		Ok(_)=>{},
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


pub type Model_declContext<'input> = BaseParserRuleContext<'input,Model_declContextExt<'input>>;

#[derive(Clone)]
pub struct Model_declContextExt<'input>{
	pub target: Option<TokenType<'input>>,
	pub resource: Option<Rc<Qualified_nameContextAll<'input>>>,
ph:PhantomData<&'input str>
}

impl<'input> SceneMaxNextGenParserContext<'input> for Model_declContext<'input>{}

impl<'input,'a> Listenable<dyn SceneMaxNextGenListener<'input> + 'a> for Model_declContext<'input>{
		fn enter(&self,listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
			listener.enter_every_rule(self);
			listener.enter_model_decl(self);
		}
		fn exit(&self,listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
			listener.exit_model_decl(self);
			listener.exit_every_rule(self);
		}
}

impl<'input,'a> Visitable<dyn SceneMaxNextGenVisitor<'input> + 'a> for Model_declContext<'input>{
	fn accept(&self,visitor: &mut (dyn SceneMaxNextGenVisitor<'input> + 'a)) {
		visitor.visit_model_decl(self);
	}
}

impl<'input> CustomRuleContext<'input> for Model_declContextExt<'input>{
	type TF = LocalTokenFactory<'input>;
	type Ctx = SceneMaxNextGenParserContextType;
	fn get_rule_index(&self) -> usize { RULE_model_decl }
	//fn type_rule_index() -> usize where Self: Sized { RULE_model_decl }
}
antlr_rust::tid!{Model_declContextExt<'a>}

impl<'input> Model_declContextExt<'input>{
	fn new(parent: Option<Rc<dyn SceneMaxNextGenParserContext<'input> + 'input > >, invoking_state: isize) -> Rc<Model_declContextAll<'input>> {
		Rc::new(
			BaseParserRuleContext::new_parser_ctx(parent, invoking_state,Model_declContextExt{
				target: None, 
				resource: None, 
				ph:PhantomData
			}),
		)
	}
}

pub trait Model_declContextAttrs<'input>: SceneMaxNextGenParserContext<'input> + BorrowMut<Model_declContextExt<'input>>{

/// Retrieves first TerminalNode corresponding to token ISA
/// Returns `None` if there is no child corresponding to token ISA
fn ISA(&self) -> Option<Rc<TerminalNode<'input,SceneMaxNextGenParserContextType>>> where Self:Sized{
	self.get_token(ISA, 0)
}
/// Retrieves first TerminalNode corresponding to token IDENT
/// Returns `None` if there is no child corresponding to token IDENT
fn IDENT(&self) -> Option<Rc<TerminalNode<'input,SceneMaxNextGenParserContextType>>> where Self:Sized{
	self.get_token(IDENT, 0)
}
fn qualified_name(&self) -> Option<Rc<Qualified_nameContextAll<'input>>> where Self:Sized{
	self.child_of_type(0)
}

}

impl<'input> Model_declContextAttrs<'input> for Model_declContext<'input>{}

impl<'input, I, H> SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input> > + TidAble<'input>,
    H: ErrorStrategy<'input,BaseParserType<'input,I>>
{
	pub fn model_decl(&mut self,)
	-> Result<Rc<Model_declContextAll<'input>>,ANTLRError> {
		let mut recog = self;
		let _parentctx = recog.ctx.take();
		let mut _localctx = Model_declContextExt::new(_parentctx.clone(), recog.base.get_state());
        recog.base.enter_rule(_localctx.clone(), 4, RULE_model_decl);
        let mut _localctx: Rc<Model_declContextAll> = _localctx;
		let result: Result<(), ANTLRError> = (|| {

			//recog.base.enter_outer_alt(_localctx.clone(), 1);
			recog.base.enter_outer_alt(None, 1);
			{
			recog.base.set_state(35);
			let tmp = recog.base.match_token(IDENT,&mut recog.err_handler)?;
			 cast_mut::<_,Model_declContext >(&mut _localctx).target = Some(tmp.clone());
			  

			recog.base.set_state(36);
			recog.base.match_token(ISA,&mut recog.err_handler)?;

			/*InvokeRule qualified_name*/
			recog.base.set_state(37);
			let tmp = recog.qualified_name()?;
			 cast_mut::<_,Model_declContext >(&mut _localctx).resource = Some(tmp.clone());
			  

			}
			Ok(())
		})();
		match result {
		Ok(_)=>{},
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


pub type Animate_stmtContext<'input> = BaseParserRuleContext<'input,Animate_stmtContextExt<'input>>;

#[derive(Clone)]
pub struct Animate_stmtContextExt<'input>{
	pub target: Option<TokenType<'input>>,
	pub animation: Option<Rc<Animation_nameContextAll<'input>>>,
ph:PhantomData<&'input str>
}

impl<'input> SceneMaxNextGenParserContext<'input> for Animate_stmtContext<'input>{}

impl<'input,'a> Listenable<dyn SceneMaxNextGenListener<'input> + 'a> for Animate_stmtContext<'input>{
		fn enter(&self,listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
			listener.enter_every_rule(self);
			listener.enter_animate_stmt(self);
		}
		fn exit(&self,listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
			listener.exit_animate_stmt(self);
			listener.exit_every_rule(self);
		}
}

impl<'input,'a> Visitable<dyn SceneMaxNextGenVisitor<'input> + 'a> for Animate_stmtContext<'input>{
	fn accept(&self,visitor: &mut (dyn SceneMaxNextGenVisitor<'input> + 'a)) {
		visitor.visit_animate_stmt(self);
	}
}

impl<'input> CustomRuleContext<'input> for Animate_stmtContextExt<'input>{
	type TF = LocalTokenFactory<'input>;
	type Ctx = SceneMaxNextGenParserContextType;
	fn get_rule_index(&self) -> usize { RULE_animate_stmt }
	//fn type_rule_index() -> usize where Self: Sized { RULE_animate_stmt }
}
antlr_rust::tid!{Animate_stmtContextExt<'a>}

impl<'input> Animate_stmtContextExt<'input>{
	fn new(parent: Option<Rc<dyn SceneMaxNextGenParserContext<'input> + 'input > >, invoking_state: isize) -> Rc<Animate_stmtContextAll<'input>> {
		Rc::new(
			BaseParserRuleContext::new_parser_ctx(parent, invoking_state,Animate_stmtContextExt{
				target: None, 
				animation: None, 
				ph:PhantomData
			}),
		)
	}
}

pub trait Animate_stmtContextAttrs<'input>: SceneMaxNextGenParserContext<'input> + BorrowMut<Animate_stmtContextExt<'input>>{

/// Retrieves first TerminalNode corresponding to token DOT
/// Returns `None` if there is no child corresponding to token DOT
fn DOT(&self) -> Option<Rc<TerminalNode<'input,SceneMaxNextGenParserContextType>>> where Self:Sized{
	self.get_token(DOT, 0)
}
/// Retrieves first TerminalNode corresponding to token IDENT
/// Returns `None` if there is no child corresponding to token IDENT
fn IDENT(&self) -> Option<Rc<TerminalNode<'input,SceneMaxNextGenParserContextType>>> where Self:Sized{
	self.get_token(IDENT, 0)
}
fn animation_name(&self) -> Option<Rc<Animation_nameContextAll<'input>>> where Self:Sized{
	self.child_of_type(0)
}
fn speed_clause(&self) -> Option<Rc<Speed_clauseContextAll<'input>>> where Self:Sized{
	self.child_of_type(0)
}
/// Retrieves first TerminalNode corresponding to token LOOP
/// Returns `None` if there is no child corresponding to token LOOP
fn LOOP(&self) -> Option<Rc<TerminalNode<'input,SceneMaxNextGenParserContextType>>> where Self:Sized{
	self.get_token(LOOP, 0)
}

}

impl<'input> Animate_stmtContextAttrs<'input> for Animate_stmtContext<'input>{}

impl<'input, I, H> SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input> > + TidAble<'input>,
    H: ErrorStrategy<'input,BaseParserType<'input,I>>
{
	pub fn animate_stmt(&mut self,)
	-> Result<Rc<Animate_stmtContextAll<'input>>,ANTLRError> {
		let mut recog = self;
		let _parentctx = recog.ctx.take();
		let mut _localctx = Animate_stmtContextExt::new(_parentctx.clone(), recog.base.get_state());
        recog.base.enter_rule(_localctx.clone(), 6, RULE_animate_stmt);
        let mut _localctx: Rc<Animate_stmtContextAll> = _localctx;
		let mut _la: isize = -1;
		let result: Result<(), ANTLRError> = (|| {

			//recog.base.enter_outer_alt(_localctx.clone(), 1);
			recog.base.enter_outer_alt(None, 1);
			{
			recog.base.set_state(39);
			let tmp = recog.base.match_token(IDENT,&mut recog.err_handler)?;
			 cast_mut::<_,Animate_stmtContext >(&mut _localctx).target = Some(tmp.clone());
			  

			recog.base.set_state(40);
			recog.base.match_token(DOT,&mut recog.err_handler)?;

			/*InvokeRule animation_name*/
			recog.base.set_state(41);
			let tmp = recog.animation_name()?;
			 cast_mut::<_,Animate_stmtContext >(&mut _localctx).animation = Some(tmp.clone());
			  

			recog.base.set_state(43);
			recog.err_handler.sync(&mut recog.base)?;
			_la = recog.base.input.la(1);
			if _la==AT {
				{
				/*InvokeRule speed_clause*/
				recog.base.set_state(42);
				recog.speed_clause()?;

				}
			}

			recog.base.set_state(46);
			recog.err_handler.sync(&mut recog.base)?;
			_la = recog.base.input.la(1);
			if _la==LOOP {
				{
				recog.base.set_state(45);
				recog.base.match_token(LOOP,&mut recog.err_handler)?;

				}
			}

			}
			Ok(())
		})();
		match result {
		Ok(_)=>{},
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


pub type Speed_clauseContext<'input> = BaseParserRuleContext<'input,Speed_clauseContextExt<'input>>;

#[derive(Clone)]
pub struct Speed_clauseContextExt<'input>{
ph:PhantomData<&'input str>
}

impl<'input> SceneMaxNextGenParserContext<'input> for Speed_clauseContext<'input>{}

impl<'input,'a> Listenable<dyn SceneMaxNextGenListener<'input> + 'a> for Speed_clauseContext<'input>{
		fn enter(&self,listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
			listener.enter_every_rule(self);
			listener.enter_speed_clause(self);
		}
		fn exit(&self,listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
			listener.exit_speed_clause(self);
			listener.exit_every_rule(self);
		}
}

impl<'input,'a> Visitable<dyn SceneMaxNextGenVisitor<'input> + 'a> for Speed_clauseContext<'input>{
	fn accept(&self,visitor: &mut (dyn SceneMaxNextGenVisitor<'input> + 'a)) {
		visitor.visit_speed_clause(self);
	}
}

impl<'input> CustomRuleContext<'input> for Speed_clauseContextExt<'input>{
	type TF = LocalTokenFactory<'input>;
	type Ctx = SceneMaxNextGenParserContextType;
	fn get_rule_index(&self) -> usize { RULE_speed_clause }
	//fn type_rule_index() -> usize where Self: Sized { RULE_speed_clause }
}
antlr_rust::tid!{Speed_clauseContextExt<'a>}

impl<'input> Speed_clauseContextExt<'input>{
	fn new(parent: Option<Rc<dyn SceneMaxNextGenParserContext<'input> + 'input > >, invoking_state: isize) -> Rc<Speed_clauseContextAll<'input>> {
		Rc::new(
			BaseParserRuleContext::new_parser_ctx(parent, invoking_state,Speed_clauseContextExt{
				ph:PhantomData
			}),
		)
	}
}

pub trait Speed_clauseContextAttrs<'input>: SceneMaxNextGenParserContext<'input> + BorrowMut<Speed_clauseContextExt<'input>>{

/// Retrieves first TerminalNode corresponding to token AT
/// Returns `None` if there is no child corresponding to token AT
fn AT(&self) -> Option<Rc<TerminalNode<'input,SceneMaxNextGenParserContextType>>> where Self:Sized{
	self.get_token(AT, 0)
}
/// Retrieves first TerminalNode corresponding to token SPEED
/// Returns `None` if there is no child corresponding to token SPEED
fn SPEED(&self) -> Option<Rc<TerminalNode<'input,SceneMaxNextGenParserContextType>>> where Self:Sized{
	self.get_token(SPEED, 0)
}
/// Retrieves first TerminalNode corresponding to token OF
/// Returns `None` if there is no child corresponding to token OF
fn OF(&self) -> Option<Rc<TerminalNode<'input,SceneMaxNextGenParserContextType>>> where Self:Sized{
	self.get_token(OF, 0)
}
fn number(&self) -> Option<Rc<NumberContextAll<'input>>> where Self:Sized{
	self.child_of_type(0)
}

}

impl<'input> Speed_clauseContextAttrs<'input> for Speed_clauseContext<'input>{}

impl<'input, I, H> SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input> > + TidAble<'input>,
    H: ErrorStrategy<'input,BaseParserType<'input,I>>
{
	pub fn speed_clause(&mut self,)
	-> Result<Rc<Speed_clauseContextAll<'input>>,ANTLRError> {
		let mut recog = self;
		let _parentctx = recog.ctx.take();
		let mut _localctx = Speed_clauseContextExt::new(_parentctx.clone(), recog.base.get_state());
        recog.base.enter_rule(_localctx.clone(), 8, RULE_speed_clause);
        let mut _localctx: Rc<Speed_clauseContextAll> = _localctx;
		let result: Result<(), ANTLRError> = (|| {

			//recog.base.enter_outer_alt(_localctx.clone(), 1);
			recog.base.enter_outer_alt(None, 1);
			{
			recog.base.set_state(48);
			recog.base.match_token(AT,&mut recog.err_handler)?;

			recog.base.set_state(49);
			recog.base.match_token(SPEED,&mut recog.err_handler)?;

			recog.base.set_state(50);
			recog.base.match_token(OF,&mut recog.err_handler)?;

			/*InvokeRule number*/
			recog.base.set_state(51);
			recog.number()?;

			}
			Ok(())
		})();
		match result {
		Ok(_)=>{},
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


pub type Add_code_stmtContext<'input> = BaseParserRuleContext<'input,Add_code_stmtContextExt<'input>>;

#[derive(Clone)]
pub struct Add_code_stmtContextExt<'input>{
ph:PhantomData<&'input str>
}

impl<'input> SceneMaxNextGenParserContext<'input> for Add_code_stmtContext<'input>{}

impl<'input,'a> Listenable<dyn SceneMaxNextGenListener<'input> + 'a> for Add_code_stmtContext<'input>{
		fn enter(&self,listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
			listener.enter_every_rule(self);
			listener.enter_add_code_stmt(self);
		}
		fn exit(&self,listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
			listener.exit_add_code_stmt(self);
			listener.exit_every_rule(self);
		}
}

impl<'input,'a> Visitable<dyn SceneMaxNextGenVisitor<'input> + 'a> for Add_code_stmtContext<'input>{
	fn accept(&self,visitor: &mut (dyn SceneMaxNextGenVisitor<'input> + 'a)) {
		visitor.visit_add_code_stmt(self);
	}
}

impl<'input> CustomRuleContext<'input> for Add_code_stmtContextExt<'input>{
	type TF = LocalTokenFactory<'input>;
	type Ctx = SceneMaxNextGenParserContextType;
	fn get_rule_index(&self) -> usize { RULE_add_code_stmt }
	//fn type_rule_index() -> usize where Self: Sized { RULE_add_code_stmt }
}
antlr_rust::tid!{Add_code_stmtContextExt<'a>}

impl<'input> Add_code_stmtContextExt<'input>{
	fn new(parent: Option<Rc<dyn SceneMaxNextGenParserContext<'input> + 'input > >, invoking_state: isize) -> Rc<Add_code_stmtContextAll<'input>> {
		Rc::new(
			BaseParserRuleContext::new_parser_ctx(parent, invoking_state,Add_code_stmtContextExt{
				ph:PhantomData
			}),
		)
	}
}

pub trait Add_code_stmtContextAttrs<'input>: SceneMaxNextGenParserContext<'input> + BorrowMut<Add_code_stmtContextExt<'input>>{

/// Retrieves first TerminalNode corresponding to token ADD
/// Returns `None` if there is no child corresponding to token ADD
fn ADD(&self) -> Option<Rc<TerminalNode<'input,SceneMaxNextGenParserContextType>>> where Self:Sized{
	self.get_token(ADD, 0)
}
/// Retrieves first TerminalNode corresponding to token QUOTED_STRING
/// Returns `None` if there is no child corresponding to token QUOTED_STRING
fn QUOTED_STRING(&self) -> Option<Rc<TerminalNode<'input,SceneMaxNextGenParserContextType>>> where Self:Sized{
	self.get_token(QUOTED_STRING, 0)
}
/// Retrieves first TerminalNode corresponding to token CODE
/// Returns `None` if there is no child corresponding to token CODE
fn CODE(&self) -> Option<Rc<TerminalNode<'input,SceneMaxNextGenParserContextType>>> where Self:Sized{
	self.get_token(CODE, 0)
}

}

impl<'input> Add_code_stmtContextAttrs<'input> for Add_code_stmtContext<'input>{}

impl<'input, I, H> SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input> > + TidAble<'input>,
    H: ErrorStrategy<'input,BaseParserType<'input,I>>
{
	pub fn add_code_stmt(&mut self,)
	-> Result<Rc<Add_code_stmtContextAll<'input>>,ANTLRError> {
		let mut recog = self;
		let _parentctx = recog.ctx.take();
		let mut _localctx = Add_code_stmtContextExt::new(_parentctx.clone(), recog.base.get_state());
        recog.base.enter_rule(_localctx.clone(), 10, RULE_add_code_stmt);
        let mut _localctx: Rc<Add_code_stmtContextAll> = _localctx;
		let result: Result<(), ANTLRError> = (|| {

			//recog.base.enter_outer_alt(_localctx.clone(), 1);
			recog.base.enter_outer_alt(None, 1);
			{
			recog.base.set_state(53);
			recog.base.match_token(ADD,&mut recog.err_handler)?;

			recog.base.set_state(54);
			recog.base.match_token(QUOTED_STRING,&mut recog.err_handler)?;

			recog.base.set_state(55);
			recog.base.match_token(CODE,&mut recog.err_handler)?;

			}
			Ok(())
		})();
		match result {
		Ok(_)=>{},
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


pub type Qualified_nameContext<'input> = BaseParserRuleContext<'input,Qualified_nameContextExt<'input>>;

#[derive(Clone)]
pub struct Qualified_nameContextExt<'input>{
ph:PhantomData<&'input str>
}

impl<'input> SceneMaxNextGenParserContext<'input> for Qualified_nameContext<'input>{}

impl<'input,'a> Listenable<dyn SceneMaxNextGenListener<'input> + 'a> for Qualified_nameContext<'input>{
		fn enter(&self,listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
			listener.enter_every_rule(self);
			listener.enter_qualified_name(self);
		}
		fn exit(&self,listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
			listener.exit_qualified_name(self);
			listener.exit_every_rule(self);
		}
}

impl<'input,'a> Visitable<dyn SceneMaxNextGenVisitor<'input> + 'a> for Qualified_nameContext<'input>{
	fn accept(&self,visitor: &mut (dyn SceneMaxNextGenVisitor<'input> + 'a)) {
		visitor.visit_qualified_name(self);
	}
}

impl<'input> CustomRuleContext<'input> for Qualified_nameContextExt<'input>{
	type TF = LocalTokenFactory<'input>;
	type Ctx = SceneMaxNextGenParserContextType;
	fn get_rule_index(&self) -> usize { RULE_qualified_name }
	//fn type_rule_index() -> usize where Self: Sized { RULE_qualified_name }
}
antlr_rust::tid!{Qualified_nameContextExt<'a>}

impl<'input> Qualified_nameContextExt<'input>{
	fn new(parent: Option<Rc<dyn SceneMaxNextGenParserContext<'input> + 'input > >, invoking_state: isize) -> Rc<Qualified_nameContextAll<'input>> {
		Rc::new(
			BaseParserRuleContext::new_parser_ctx(parent, invoking_state,Qualified_nameContextExt{
				ph:PhantomData
			}),
		)
	}
}

pub trait Qualified_nameContextAttrs<'input>: SceneMaxNextGenParserContext<'input> + BorrowMut<Qualified_nameContextExt<'input>>{

fn qualified_name_part_all(&self) ->  Vec<Rc<Qualified_name_partContextAll<'input>>> where Self:Sized{
	self.children_of_type()
}
fn qualified_name_part(&self, i: usize) -> Option<Rc<Qualified_name_partContextAll<'input>>> where Self:Sized{
	self.child_of_type(i)
}
/// Retrieves all `TerminalNode`s corresponding to token DOT in current rule
fn DOT_all(&self) -> Vec<Rc<TerminalNode<'input,SceneMaxNextGenParserContextType>>>  where Self:Sized{
	self.children_of_type()
}
/// Retrieves 'i's TerminalNode corresponding to token DOT, starting from 0.
/// Returns `None` if number of children corresponding to token DOT is less or equal than `i`.
fn DOT(&self, i: usize) -> Option<Rc<TerminalNode<'input,SceneMaxNextGenParserContextType>>> where Self:Sized{
	self.get_token(DOT, i)
}

}

impl<'input> Qualified_nameContextAttrs<'input> for Qualified_nameContext<'input>{}

impl<'input, I, H> SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input> > + TidAble<'input>,
    H: ErrorStrategy<'input,BaseParserType<'input,I>>
{
	pub fn qualified_name(&mut self,)
	-> Result<Rc<Qualified_nameContextAll<'input>>,ANTLRError> {
		let mut recog = self;
		let _parentctx = recog.ctx.take();
		let mut _localctx = Qualified_nameContextExt::new(_parentctx.clone(), recog.base.get_state());
        recog.base.enter_rule(_localctx.clone(), 12, RULE_qualified_name);
        let mut _localctx: Rc<Qualified_nameContextAll> = _localctx;
		let mut _la: isize = -1;
		let result: Result<(), ANTLRError> = (|| {

			//recog.base.enter_outer_alt(_localctx.clone(), 1);
			recog.base.enter_outer_alt(None, 1);
			{
			/*InvokeRule qualified_name_part*/
			recog.base.set_state(57);
			recog.qualified_name_part()?;

			recog.base.set_state(62);
			recog.err_handler.sync(&mut recog.base)?;
			_la = recog.base.input.la(1);
			while _la==DOT {
				{
				{
				recog.base.set_state(58);
				recog.base.match_token(DOT,&mut recog.err_handler)?;

				/*InvokeRule qualified_name_part*/
				recog.base.set_state(59);
				recog.qualified_name_part()?;

				}
				}
				recog.base.set_state(64);
				recog.err_handler.sync(&mut recog.base)?;
				_la = recog.base.input.la(1);
			}
			}
			Ok(())
		})();
		match result {
		Ok(_)=>{},
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


pub type Qualified_name_partContext<'input> = BaseParserRuleContext<'input,Qualified_name_partContextExt<'input>>;

#[derive(Clone)]
pub struct Qualified_name_partContextExt<'input>{
ph:PhantomData<&'input str>
}

impl<'input> SceneMaxNextGenParserContext<'input> for Qualified_name_partContext<'input>{}

impl<'input,'a> Listenable<dyn SceneMaxNextGenListener<'input> + 'a> for Qualified_name_partContext<'input>{
		fn enter(&self,listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
			listener.enter_every_rule(self);
			listener.enter_qualified_name_part(self);
		}
		fn exit(&self,listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
			listener.exit_qualified_name_part(self);
			listener.exit_every_rule(self);
		}
}

impl<'input,'a> Visitable<dyn SceneMaxNextGenVisitor<'input> + 'a> for Qualified_name_partContext<'input>{
	fn accept(&self,visitor: &mut (dyn SceneMaxNextGenVisitor<'input> + 'a)) {
		visitor.visit_qualified_name_part(self);
	}
}

impl<'input> CustomRuleContext<'input> for Qualified_name_partContextExt<'input>{
	type TF = LocalTokenFactory<'input>;
	type Ctx = SceneMaxNextGenParserContextType;
	fn get_rule_index(&self) -> usize { RULE_qualified_name_part }
	//fn type_rule_index() -> usize where Self: Sized { RULE_qualified_name_part }
}
antlr_rust::tid!{Qualified_name_partContextExt<'a>}

impl<'input> Qualified_name_partContextExt<'input>{
	fn new(parent: Option<Rc<dyn SceneMaxNextGenParserContext<'input> + 'input > >, invoking_state: isize) -> Rc<Qualified_name_partContextAll<'input>> {
		Rc::new(
			BaseParserRuleContext::new_parser_ctx(parent, invoking_state,Qualified_name_partContextExt{
				ph:PhantomData
			}),
		)
	}
}

pub trait Qualified_name_partContextAttrs<'input>: SceneMaxNextGenParserContext<'input> + BorrowMut<Qualified_name_partContextExt<'input>>{

/// Retrieves first TerminalNode corresponding to token IDENT
/// Returns `None` if there is no child corresponding to token IDENT
fn IDENT(&self) -> Option<Rc<TerminalNode<'input,SceneMaxNextGenParserContextType>>> where Self:Sized{
	self.get_token(IDENT, 0)
}
fn keyword_identifier(&self) -> Option<Rc<Keyword_identifierContextAll<'input>>> where Self:Sized{
	self.child_of_type(0)
}

}

impl<'input> Qualified_name_partContextAttrs<'input> for Qualified_name_partContext<'input>{}

impl<'input, I, H> SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input> > + TidAble<'input>,
    H: ErrorStrategy<'input,BaseParserType<'input,I>>
{
	pub fn qualified_name_part(&mut self,)
	-> Result<Rc<Qualified_name_partContextAll<'input>>,ANTLRError> {
		let mut recog = self;
		let _parentctx = recog.ctx.take();
		let mut _localctx = Qualified_name_partContextExt::new(_parentctx.clone(), recog.base.get_state());
        recog.base.enter_rule(_localctx.clone(), 14, RULE_qualified_name_part);
        let mut _localctx: Rc<Qualified_name_partContextAll> = _localctx;
		let result: Result<(), ANTLRError> = (|| {

			recog.base.set_state(67);
			recog.err_handler.sync(&mut recog.base)?;
			match recog.base.input.la(1) {
			 IDENT 
				=> {
					//recog.base.enter_outer_alt(_localctx.clone(), 1);
					recog.base.enter_outer_alt(None, 1);
					{
					recog.base.set_state(65);
					recog.base.match_token(IDENT,&mut recog.err_handler)?;

					}
				}

			 LOOP | ADD | CODE | AT | SPEED | OF 
				=> {
					//recog.base.enter_outer_alt(_localctx.clone(), 2);
					recog.base.enter_outer_alt(None, 2);
					{
					/*InvokeRule keyword_identifier*/
					recog.base.set_state(66);
					recog.keyword_identifier()?;

					}
				}

				_ => Err(ANTLRError::NoAltError(NoViableAltError::new(&mut recog.base)))?
			}
			Ok(())
		})();
		match result {
		Ok(_)=>{},
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


pub type Animation_nameContext<'input> = BaseParserRuleContext<'input,Animation_nameContextExt<'input>>;

#[derive(Clone)]
pub struct Animation_nameContextExt<'input>{
ph:PhantomData<&'input str>
}

impl<'input> SceneMaxNextGenParserContext<'input> for Animation_nameContext<'input>{}

impl<'input,'a> Listenable<dyn SceneMaxNextGenListener<'input> + 'a> for Animation_nameContext<'input>{
		fn enter(&self,listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
			listener.enter_every_rule(self);
			listener.enter_animation_name(self);
		}
		fn exit(&self,listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
			listener.exit_animation_name(self);
			listener.exit_every_rule(self);
		}
}

impl<'input,'a> Visitable<dyn SceneMaxNextGenVisitor<'input> + 'a> for Animation_nameContext<'input>{
	fn accept(&self,visitor: &mut (dyn SceneMaxNextGenVisitor<'input> + 'a)) {
		visitor.visit_animation_name(self);
	}
}

impl<'input> CustomRuleContext<'input> for Animation_nameContextExt<'input>{
	type TF = LocalTokenFactory<'input>;
	type Ctx = SceneMaxNextGenParserContextType;
	fn get_rule_index(&self) -> usize { RULE_animation_name }
	//fn type_rule_index() -> usize where Self: Sized { RULE_animation_name }
}
antlr_rust::tid!{Animation_nameContextExt<'a>}

impl<'input> Animation_nameContextExt<'input>{
	fn new(parent: Option<Rc<dyn SceneMaxNextGenParserContext<'input> + 'input > >, invoking_state: isize) -> Rc<Animation_nameContextAll<'input>> {
		Rc::new(
			BaseParserRuleContext::new_parser_ctx(parent, invoking_state,Animation_nameContextExt{
				ph:PhantomData
			}),
		)
	}
}

pub trait Animation_nameContextAttrs<'input>: SceneMaxNextGenParserContext<'input> + BorrowMut<Animation_nameContextExt<'input>>{

/// Retrieves first TerminalNode corresponding to token IDENT
/// Returns `None` if there is no child corresponding to token IDENT
fn IDENT(&self) -> Option<Rc<TerminalNode<'input,SceneMaxNextGenParserContextType>>> where Self:Sized{
	self.get_token(IDENT, 0)
}
/// Retrieves first TerminalNode corresponding to token QUOTED_STRING
/// Returns `None` if there is no child corresponding to token QUOTED_STRING
fn QUOTED_STRING(&self) -> Option<Rc<TerminalNode<'input,SceneMaxNextGenParserContextType>>> where Self:Sized{
	self.get_token(QUOTED_STRING, 0)
}
fn keyword_identifier(&self) -> Option<Rc<Keyword_identifierContextAll<'input>>> where Self:Sized{
	self.child_of_type(0)
}

}

impl<'input> Animation_nameContextAttrs<'input> for Animation_nameContext<'input>{}

impl<'input, I, H> SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input> > + TidAble<'input>,
    H: ErrorStrategy<'input,BaseParserType<'input,I>>
{
	pub fn animation_name(&mut self,)
	-> Result<Rc<Animation_nameContextAll<'input>>,ANTLRError> {
		let mut recog = self;
		let _parentctx = recog.ctx.take();
		let mut _localctx = Animation_nameContextExt::new(_parentctx.clone(), recog.base.get_state());
        recog.base.enter_rule(_localctx.clone(), 16, RULE_animation_name);
        let mut _localctx: Rc<Animation_nameContextAll> = _localctx;
		let result: Result<(), ANTLRError> = (|| {

			recog.base.set_state(72);
			recog.err_handler.sync(&mut recog.base)?;
			match recog.base.input.la(1) {
			 IDENT 
				=> {
					//recog.base.enter_outer_alt(_localctx.clone(), 1);
					recog.base.enter_outer_alt(None, 1);
					{
					recog.base.set_state(69);
					recog.base.match_token(IDENT,&mut recog.err_handler)?;

					}
				}

			 QUOTED_STRING 
				=> {
					//recog.base.enter_outer_alt(_localctx.clone(), 2);
					recog.base.enter_outer_alt(None, 2);
					{
					recog.base.set_state(70);
					recog.base.match_token(QUOTED_STRING,&mut recog.err_handler)?;

					}
				}

			 LOOP | ADD | CODE | AT | SPEED | OF 
				=> {
					//recog.base.enter_outer_alt(_localctx.clone(), 3);
					recog.base.enter_outer_alt(None, 3);
					{
					/*InvokeRule keyword_identifier*/
					recog.base.set_state(71);
					recog.keyword_identifier()?;

					}
				}

				_ => Err(ANTLRError::NoAltError(NoViableAltError::new(&mut recog.base)))?
			}
			Ok(())
		})();
		match result {
		Ok(_)=>{},
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


pub type Keyword_identifierContext<'input> = BaseParserRuleContext<'input,Keyword_identifierContextExt<'input>>;

#[derive(Clone)]
pub struct Keyword_identifierContextExt<'input>{
ph:PhantomData<&'input str>
}

impl<'input> SceneMaxNextGenParserContext<'input> for Keyword_identifierContext<'input>{}

impl<'input,'a> Listenable<dyn SceneMaxNextGenListener<'input> + 'a> for Keyword_identifierContext<'input>{
		fn enter(&self,listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
			listener.enter_every_rule(self);
			listener.enter_keyword_identifier(self);
		}
		fn exit(&self,listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
			listener.exit_keyword_identifier(self);
			listener.exit_every_rule(self);
		}
}

impl<'input,'a> Visitable<dyn SceneMaxNextGenVisitor<'input> + 'a> for Keyword_identifierContext<'input>{
	fn accept(&self,visitor: &mut (dyn SceneMaxNextGenVisitor<'input> + 'a)) {
		visitor.visit_keyword_identifier(self);
	}
}

impl<'input> CustomRuleContext<'input> for Keyword_identifierContextExt<'input>{
	type TF = LocalTokenFactory<'input>;
	type Ctx = SceneMaxNextGenParserContextType;
	fn get_rule_index(&self) -> usize { RULE_keyword_identifier }
	//fn type_rule_index() -> usize where Self: Sized { RULE_keyword_identifier }
}
antlr_rust::tid!{Keyword_identifierContextExt<'a>}

impl<'input> Keyword_identifierContextExt<'input>{
	fn new(parent: Option<Rc<dyn SceneMaxNextGenParserContext<'input> + 'input > >, invoking_state: isize) -> Rc<Keyword_identifierContextAll<'input>> {
		Rc::new(
			BaseParserRuleContext::new_parser_ctx(parent, invoking_state,Keyword_identifierContextExt{
				ph:PhantomData
			}),
		)
	}
}

pub trait Keyword_identifierContextAttrs<'input>: SceneMaxNextGenParserContext<'input> + BorrowMut<Keyword_identifierContextExt<'input>>{

/// Retrieves first TerminalNode corresponding to token LOOP
/// Returns `None` if there is no child corresponding to token LOOP
fn LOOP(&self) -> Option<Rc<TerminalNode<'input,SceneMaxNextGenParserContextType>>> where Self:Sized{
	self.get_token(LOOP, 0)
}
/// Retrieves first TerminalNode corresponding to token ADD
/// Returns `None` if there is no child corresponding to token ADD
fn ADD(&self) -> Option<Rc<TerminalNode<'input,SceneMaxNextGenParserContextType>>> where Self:Sized{
	self.get_token(ADD, 0)
}
/// Retrieves first TerminalNode corresponding to token CODE
/// Returns `None` if there is no child corresponding to token CODE
fn CODE(&self) -> Option<Rc<TerminalNode<'input,SceneMaxNextGenParserContextType>>> where Self:Sized{
	self.get_token(CODE, 0)
}
/// Retrieves first TerminalNode corresponding to token AT
/// Returns `None` if there is no child corresponding to token AT
fn AT(&self) -> Option<Rc<TerminalNode<'input,SceneMaxNextGenParserContextType>>> where Self:Sized{
	self.get_token(AT, 0)
}
/// Retrieves first TerminalNode corresponding to token SPEED
/// Returns `None` if there is no child corresponding to token SPEED
fn SPEED(&self) -> Option<Rc<TerminalNode<'input,SceneMaxNextGenParserContextType>>> where Self:Sized{
	self.get_token(SPEED, 0)
}
/// Retrieves first TerminalNode corresponding to token OF
/// Returns `None` if there is no child corresponding to token OF
fn OF(&self) -> Option<Rc<TerminalNode<'input,SceneMaxNextGenParserContextType>>> where Self:Sized{
	self.get_token(OF, 0)
}

}

impl<'input> Keyword_identifierContextAttrs<'input> for Keyword_identifierContext<'input>{}

impl<'input, I, H> SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input> > + TidAble<'input>,
    H: ErrorStrategy<'input,BaseParserType<'input,I>>
{
	pub fn keyword_identifier(&mut self,)
	-> Result<Rc<Keyword_identifierContextAll<'input>>,ANTLRError> {
		let mut recog = self;
		let _parentctx = recog.ctx.take();
		let mut _localctx = Keyword_identifierContextExt::new(_parentctx.clone(), recog.base.get_state());
        recog.base.enter_rule(_localctx.clone(), 18, RULE_keyword_identifier);
        let mut _localctx: Rc<Keyword_identifierContextAll> = _localctx;
		let mut _la: isize = -1;
		let result: Result<(), ANTLRError> = (|| {

			//recog.base.enter_outer_alt(_localctx.clone(), 1);
			recog.base.enter_outer_alt(None, 1);
			{
			recog.base.set_state(74);
			_la = recog.base.input.la(1);
			if { !((((_la) & !0x3f) == 0 && ((1usize << _la) & ((1usize << LOOP) | (1usize << ADD) | (1usize << CODE) | (1usize << AT) | (1usize << SPEED) | (1usize << OF))) != 0)) } {
				recog.err_handler.recover_inline(&mut recog.base)?;

			}
			else {
				if  recog.base.input.la(1)==TOKEN_EOF { recog.base.matched_eof = true };
				recog.err_handler.report_match(&mut recog.base);
				recog.base.consume(&mut recog.err_handler);
			}
			}
			Ok(())
		})();
		match result {
		Ok(_)=>{},
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


pub type NumberContext<'input> = BaseParserRuleContext<'input,NumberContextExt<'input>>;

#[derive(Clone)]
pub struct NumberContextExt<'input>{
ph:PhantomData<&'input str>
}

impl<'input> SceneMaxNextGenParserContext<'input> for NumberContext<'input>{}

impl<'input,'a> Listenable<dyn SceneMaxNextGenListener<'input> + 'a> for NumberContext<'input>{
		fn enter(&self,listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
			listener.enter_every_rule(self);
			listener.enter_number(self);
		}
		fn exit(&self,listener: &mut (dyn SceneMaxNextGenListener<'input> + 'a)) {
			listener.exit_number(self);
			listener.exit_every_rule(self);
		}
}

impl<'input,'a> Visitable<dyn SceneMaxNextGenVisitor<'input> + 'a> for NumberContext<'input>{
	fn accept(&self,visitor: &mut (dyn SceneMaxNextGenVisitor<'input> + 'a)) {
		visitor.visit_number(self);
	}
}

impl<'input> CustomRuleContext<'input> for NumberContextExt<'input>{
	type TF = LocalTokenFactory<'input>;
	type Ctx = SceneMaxNextGenParserContextType;
	fn get_rule_index(&self) -> usize { RULE_number }
	//fn type_rule_index() -> usize where Self: Sized { RULE_number }
}
antlr_rust::tid!{NumberContextExt<'a>}

impl<'input> NumberContextExt<'input>{
	fn new(parent: Option<Rc<dyn SceneMaxNextGenParserContext<'input> + 'input > >, invoking_state: isize) -> Rc<NumberContextAll<'input>> {
		Rc::new(
			BaseParserRuleContext::new_parser_ctx(parent, invoking_state,NumberContextExt{
				ph:PhantomData
			}),
		)
	}
}

pub trait NumberContextAttrs<'input>: SceneMaxNextGenParserContext<'input> + BorrowMut<NumberContextExt<'input>>{

/// Retrieves first TerminalNode corresponding to token DECIMAL
/// Returns `None` if there is no child corresponding to token DECIMAL
fn DECIMAL(&self) -> Option<Rc<TerminalNode<'input,SceneMaxNextGenParserContextType>>> where Self:Sized{
	self.get_token(DECIMAL, 0)
}
/// Retrieves first TerminalNode corresponding to token SIGN
/// Returns `None` if there is no child corresponding to token SIGN
fn SIGN(&self) -> Option<Rc<TerminalNode<'input,SceneMaxNextGenParserContextType>>> where Self:Sized{
	self.get_token(SIGN, 0)
}

}

impl<'input> NumberContextAttrs<'input> for NumberContext<'input>{}

impl<'input, I, H> SceneMaxNextGenParser<'input, I, H>
where
    I: TokenStream<'input, TF = LocalTokenFactory<'input> > + TidAble<'input>,
    H: ErrorStrategy<'input,BaseParserType<'input,I>>
{
	pub fn number(&mut self,)
	-> Result<Rc<NumberContextAll<'input>>,ANTLRError> {
		let mut recog = self;
		let _parentctx = recog.ctx.take();
		let mut _localctx = NumberContextExt::new(_parentctx.clone(), recog.base.get_state());
        recog.base.enter_rule(_localctx.clone(), 20, RULE_number);
        let mut _localctx: Rc<NumberContextAll> = _localctx;
		let mut _la: isize = -1;
		let result: Result<(), ANTLRError> = (|| {

			//recog.base.enter_outer_alt(_localctx.clone(), 1);
			recog.base.enter_outer_alt(None, 1);
			{
			recog.base.set_state(77);
			recog.err_handler.sync(&mut recog.base)?;
			_la = recog.base.input.la(1);
			if _la==SIGN {
				{
				recog.base.set_state(76);
				recog.base.match_token(SIGN,&mut recog.err_handler)?;

				}
			}

			recog.base.set_state(79);
			recog.base.match_token(DECIMAL,&mut recog.err_handler)?;

			}
			Ok(())
		})();
		match result {
		Ok(_)=>{},
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
            dfa.push(DFA::new(
                _ATN.clone(),
                _ATN.get_decision_state(i),
                i as isize,
            ).into())
        }
        Arc::new(dfa)
    };
}



const _serializedATN:&'static str =
	"\x03\u{608b}\u{a72a}\u{8133}\u{b9ed}\u{417c}\u{3be7}\u{7786}\u{5964}\x03\
	\x11\x54\x04\x02\x09\x02\x04\x03\x09\x03\x04\x04\x09\x04\x04\x05\x09\x05\
	\x04\x06\x09\x06\x04\x07\x09\x07\x04\x08\x09\x08\x04\x09\x09\x09\x04\x0a\
	\x09\x0a\x04\x0b\x09\x0b\x04\x0c\x09\x0c\x03\x02\x07\x02\x1a\x0a\x02\x0c\
	\x02\x0e\x02\x1d\x0b\x02\x03\x02\x03\x02\x03\x03\x03\x03\x03\x03\x05\x03\
	\x24\x0a\x03\x03\x04\x03\x04\x03\x04\x03\x04\x03\x05\x03\x05\x03\x05\x03\
	\x05\x05\x05\x2e\x0a\x05\x03\x05\x05\x05\x31\x0a\x05\x03\x06\x03\x06\x03\
	\x06\x03\x06\x03\x06\x03\x07\x03\x07\x03\x07\x03\x07\x03\x08\x03\x08\x03\
	\x08\x07\x08\x3f\x0a\x08\x0c\x08\x0e\x08\x42\x0b\x08\x03\x09\x03\x09\x05\
	\x09\x46\x0a\x09\x03\x0a\x03\x0a\x03\x0a\x05\x0a\x4b\x0a\x0a\x03\x0b\x03\
	\x0b\x03\x0c\x05\x0c\x50\x0a\x0c\x03\x0c\x03\x0c\x03\x0c\x02\x02\x0d\x02\
	\x04\x06\x08\x0a\x0c\x0e\x10\x12\x14\x16\x02\x03\x03\x02\x04\x09\x02\x52\
	\x02\x1b\x03\x02\x02\x02\x04\x23\x03\x02\x02\x02\x06\x25\x03\x02\x02\x02\
	\x08\x29\x03\x02\x02\x02\x0a\x32\x03\x02\x02\x02\x0c\x37\x03\x02\x02\x02\
	\x0e\x3b\x03\x02\x02\x02\x10\x45\x03\x02\x02\x02\x12\x4a\x03\x02\x02\x02\
	\x14\x4c\x03\x02\x02\x02\x16\x4f\x03\x02\x02\x02\x18\x1a\x05\x04\x03\x02\
	\x19\x18\x03\x02\x02\x02\x1a\x1d\x03\x02\x02\x02\x1b\x19\x03\x02\x02\x02\
	\x1b\x1c\x03\x02\x02\x02\x1c\x1e\x03\x02\x02\x02\x1d\x1b\x03\x02\x02\x02\
	\x1e\x1f\x07\x02\x02\x03\x1f\x03\x03\x02\x02\x02\x20\x24\x05\x06\x04\x02\
	\x21\x24\x05\x08\x05\x02\x22\x24\x05\x0c\x07\x02\x23\x20\x03\x02\x02\x02\
	\x23\x21\x03\x02\x02\x02\x23\x22\x03\x02\x02\x02\x24\x05\x03\x02\x02\x02\
	\x25\x26\x07\x0d\x02\x02\x26\x27\x07\x03\x02\x02\x27\x28\x05\x0e\x08\x02\
	\x28\x07\x03\x02\x02\x02\x29\x2a\x07\x0d\x02\x02\x2a\x2b\x07\x0a\x02\x02\
	\x2b\x2d\x05\x12\x0a\x02\x2c\x2e\x05\x0a\x06\x02\x2d\x2c\x03\x02\x02\x02\
	\x2d\x2e\x03\x02\x02\x02\x2e\x30\x03\x02\x02\x02\x2f\x31\x07\x04\x02\x02\
	\x30\x2f\x03\x02\x02\x02\x30\x31\x03\x02\x02\x02\x31\x09\x03\x02\x02\x02\
	\x32\x33\x07\x07\x02\x02\x33\x34\x07\x08\x02\x02\x34\x35\x07\x09\x02\x02\
	\x35\x36\x05\x16\x0c\x02\x36\x0b\x03\x02\x02\x02\x37\x38\x07\x05\x02\x02\
	\x38\x39\x07\x0e\x02\x02\x39\x3a\x07\x06\x02\x02\x3a\x0d\x03\x02\x02\x02\
	\x3b\x40\x05\x10\x09\x02\x3c\x3d\x07\x0a\x02\x02\x3d\x3f\x05\x10\x09\x02\
	\x3e\x3c\x03\x02\x02\x02\x3f\x42\x03\x02\x02\x02\x40\x3e\x03\x02\x02\x02\
	\x40\x41\x03\x02\x02\x02\x41\x0f\x03\x02\x02\x02\x42\x40\x03\x02\x02\x02\
	\x43\x46\x07\x0d\x02\x02\x44\x46\x05\x14\x0b\x02\x45\x43\x03\x02\x02\x02\
	\x45\x44\x03\x02\x02\x02\x46\x11\x03\x02\x02\x02\x47\x4b\x07\x0d\x02\x02\
	\x48\x4b\x07\x0e\x02\x02\x49\x4b\x05\x14\x0b\x02\x4a\x47\x03\x02\x02\x02\
	\x4a\x48\x03\x02\x02\x02\x4a\x49\x03\x02\x02\x02\x4b\x13\x03\x02\x02\x02\
	\x4c\x4d\x09\x02\x02\x02\x4d\x15\x03\x02\x02\x02\x4e\x50\x07\x0b\x02\x02\
	\x4f\x4e\x03\x02\x02\x02\x4f\x50\x03\x02\x02\x02\x50\x51\x03\x02\x02\x02\
	\x51\x52\x07\x0c\x02\x02\x52\x17\x03\x02\x02\x02\x0a\x1b\x23\x2d\x30\x40\
	\x45\x4a\x4f";

