#![allow(nonstandard_style)]
// Generated from SceneMaxNextGen.g4 by ANTLR 4.8
use antlr_rust::tree::ParseTreeListener;
use super::scenemaxnextgenparser::*;

pub trait SceneMaxNextGenListener<'input> : ParseTreeListener<'input,SceneMaxNextGenParserContextType>{
/**
 * Enter a parse tree produced by {@link SceneMaxNextGenParser#program}.
 * @param ctx the parse tree
 */
fn enter_program(&mut self, _ctx: &ProgramContext<'input>) { }
/**
 * Exit a parse tree produced by {@link SceneMaxNextGenParser#program}.
 * @param ctx the parse tree
 */
fn exit_program(&mut self, _ctx: &ProgramContext<'input>) { }
/**
 * Enter a parse tree produced by {@link SceneMaxNextGenParser#statement}.
 * @param ctx the parse tree
 */
fn enter_statement(&mut self, _ctx: &StatementContext<'input>) { }
/**
 * Exit a parse tree produced by {@link SceneMaxNextGenParser#statement}.
 * @param ctx the parse tree
 */
fn exit_statement(&mut self, _ctx: &StatementContext<'input>) { }
/**
 * Enter a parse tree produced by {@link SceneMaxNextGenParser#model_decl}.
 * @param ctx the parse tree
 */
fn enter_model_decl(&mut self, _ctx: &Model_declContext<'input>) { }
/**
 * Exit a parse tree produced by {@link SceneMaxNextGenParser#model_decl}.
 * @param ctx the parse tree
 */
fn exit_model_decl(&mut self, _ctx: &Model_declContext<'input>) { }
/**
 * Enter a parse tree produced by {@link SceneMaxNextGenParser#animate_stmt}.
 * @param ctx the parse tree
 */
fn enter_animate_stmt(&mut self, _ctx: &Animate_stmtContext<'input>) { }
/**
 * Exit a parse tree produced by {@link SceneMaxNextGenParser#animate_stmt}.
 * @param ctx the parse tree
 */
fn exit_animate_stmt(&mut self, _ctx: &Animate_stmtContext<'input>) { }
/**
 * Enter a parse tree produced by {@link SceneMaxNextGenParser#speed_clause}.
 * @param ctx the parse tree
 */
fn enter_speed_clause(&mut self, _ctx: &Speed_clauseContext<'input>) { }
/**
 * Exit a parse tree produced by {@link SceneMaxNextGenParser#speed_clause}.
 * @param ctx the parse tree
 */
fn exit_speed_clause(&mut self, _ctx: &Speed_clauseContext<'input>) { }
/**
 * Enter a parse tree produced by {@link SceneMaxNextGenParser#add_code_stmt}.
 * @param ctx the parse tree
 */
fn enter_add_code_stmt(&mut self, _ctx: &Add_code_stmtContext<'input>) { }
/**
 * Exit a parse tree produced by {@link SceneMaxNextGenParser#add_code_stmt}.
 * @param ctx the parse tree
 */
fn exit_add_code_stmt(&mut self, _ctx: &Add_code_stmtContext<'input>) { }
/**
 * Enter a parse tree produced by {@link SceneMaxNextGenParser#qualified_name}.
 * @param ctx the parse tree
 */
fn enter_qualified_name(&mut self, _ctx: &Qualified_nameContext<'input>) { }
/**
 * Exit a parse tree produced by {@link SceneMaxNextGenParser#qualified_name}.
 * @param ctx the parse tree
 */
fn exit_qualified_name(&mut self, _ctx: &Qualified_nameContext<'input>) { }
/**
 * Enter a parse tree produced by {@link SceneMaxNextGenParser#qualified_name_part}.
 * @param ctx the parse tree
 */
fn enter_qualified_name_part(&mut self, _ctx: &Qualified_name_partContext<'input>) { }
/**
 * Exit a parse tree produced by {@link SceneMaxNextGenParser#qualified_name_part}.
 * @param ctx the parse tree
 */
fn exit_qualified_name_part(&mut self, _ctx: &Qualified_name_partContext<'input>) { }
/**
 * Enter a parse tree produced by {@link SceneMaxNextGenParser#animation_name}.
 * @param ctx the parse tree
 */
fn enter_animation_name(&mut self, _ctx: &Animation_nameContext<'input>) { }
/**
 * Exit a parse tree produced by {@link SceneMaxNextGenParser#animation_name}.
 * @param ctx the parse tree
 */
fn exit_animation_name(&mut self, _ctx: &Animation_nameContext<'input>) { }
/**
 * Enter a parse tree produced by {@link SceneMaxNextGenParser#keyword_identifier}.
 * @param ctx the parse tree
 */
fn enter_keyword_identifier(&mut self, _ctx: &Keyword_identifierContext<'input>) { }
/**
 * Exit a parse tree produced by {@link SceneMaxNextGenParser#keyword_identifier}.
 * @param ctx the parse tree
 */
fn exit_keyword_identifier(&mut self, _ctx: &Keyword_identifierContext<'input>) { }
/**
 * Enter a parse tree produced by {@link SceneMaxNextGenParser#number}.
 * @param ctx the parse tree
 */
fn enter_number(&mut self, _ctx: &NumberContext<'input>) { }
/**
 * Exit a parse tree produced by {@link SceneMaxNextGenParser#number}.
 * @param ctx the parse tree
 */
fn exit_number(&mut self, _ctx: &NumberContext<'input>) { }

}

antlr_rust::coerce_from!{ 'input : SceneMaxNextGenListener<'input> }


