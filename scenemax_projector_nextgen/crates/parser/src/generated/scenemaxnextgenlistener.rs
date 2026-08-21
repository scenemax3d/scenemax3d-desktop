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
 * Enter a parse tree produced by {@link SceneMaxNextGenParser#move_stmt}.
 * @param ctx the parse tree
 */
fn enter_move_stmt(&mut self, _ctx: &Move_stmtContext<'input>) { }
/**
 * Exit a parse tree produced by {@link SceneMaxNextGenParser#move_stmt}.
 * @param ctx the parse tree
 */
fn exit_move_stmt(&mut self, _ctx: &Move_stmtContext<'input>) { }
/**
 * Enter a parse tree produced by {@link SceneMaxNextGenParser#move_to_stmt}.
 * @param ctx the parse tree
 */
fn enter_move_to_stmt(&mut self, _ctx: &Move_to_stmtContext<'input>) { }
/**
 * Exit a parse tree produced by {@link SceneMaxNextGenParser#move_to_stmt}.
 * @param ctx the parse tree
 */
fn exit_move_to_stmt(&mut self, _ctx: &Move_to_stmtContext<'input>) { }
/**
 * Enter a parse tree produced by {@link SceneMaxNextGenParser#move_to_destination}.
 * @param ctx the parse tree
 */
fn enter_move_to_destination(&mut self, _ctx: &Move_to_destinationContext<'input>) { }
/**
 * Exit a parse tree produced by {@link SceneMaxNextGenParser#move_to_destination}.
 * @param ctx the parse tree
 */
fn exit_move_to_destination(&mut self, _ctx: &Move_to_destinationContext<'input>) { }
/**
 * Enter a parse tree produced by {@link SceneMaxNextGenParser#turn_stmt}.
 * @param ctx the parse tree
 */
fn enter_turn_stmt(&mut self, _ctx: &Turn_stmtContext<'input>) { }
/**
 * Exit a parse tree produced by {@link SceneMaxNextGenParser#turn_stmt}.
 * @param ctx the parse tree
 */
fn exit_turn_stmt(&mut self, _ctx: &Turn_stmtContext<'input>) { }
/**
 * Enter a parse tree produced by {@link SceneMaxNextGenParser#rotate_stmt}.
 * @param ctx the parse tree
 */
fn enter_rotate_stmt(&mut self, _ctx: &Rotate_stmtContext<'input>) { }
/**
 * Exit a parse tree produced by {@link SceneMaxNextGenParser#rotate_stmt}.
 * @param ctx the parse tree
 */
fn exit_rotate_stmt(&mut self, _ctx: &Rotate_stmtContext<'input>) { }
/**
 * Enter a parse tree produced by {@link SceneMaxNextGenParser#move_direction}.
 * @param ctx the parse tree
 */
fn enter_move_direction(&mut self, _ctx: &Move_directionContext<'input>) { }
/**
 * Exit a parse tree produced by {@link SceneMaxNextGenParser#move_direction}.
 * @param ctx the parse tree
 */
fn exit_move_direction(&mut self, _ctx: &Move_directionContext<'input>) { }
/**
 * Enter a parse tree produced by {@link SceneMaxNextGenParser#duration_clause}.
 * @param ctx the parse tree
 */
fn enter_duration_clause(&mut self, _ctx: &Duration_clauseContext<'input>) { }
/**
 * Exit a parse tree produced by {@link SceneMaxNextGenParser#duration_clause}.
 * @param ctx the parse tree
 */
fn exit_duration_clause(&mut self, _ctx: &Duration_clauseContext<'input>) { }
/**
 * Enter a parse tree produced by {@link SceneMaxNextGenParser#run_function_stmt}.
 * @param ctx the parse tree
 */
fn enter_run_function_stmt(&mut self, _ctx: &Run_function_stmtContext<'input>) { }
/**
 * Exit a parse tree produced by {@link SceneMaxNextGenParser#run_function_stmt}.
 * @param ctx the parse tree
 */
fn exit_run_function_stmt(&mut self, _ctx: &Run_function_stmtContext<'input>) { }
/**
 * Enter a parse tree produced by {@link SceneMaxNextGenParser#run_every_stmt}.
 * @param ctx the parse tree
 */
fn enter_run_every_stmt(&mut self, _ctx: &Run_every_stmtContext<'input>) { }
/**
 * Exit a parse tree produced by {@link SceneMaxNextGenParser#run_every_stmt}.
 * @param ctx the parse tree
 */
fn exit_run_every_stmt(&mut self, _ctx: &Run_every_stmtContext<'input>) { }
/**
 * Enter a parse tree produced by {@link SceneMaxNextGenParser#function_call}.
 * @param ctx the parse tree
 */
fn enter_function_call(&mut self, _ctx: &Function_callContext<'input>) { }
/**
 * Exit a parse tree produced by {@link SceneMaxNextGenParser#function_call}.
 * @param ctx the parse tree
 */
fn exit_function_call(&mut self, _ctx: &Function_callContext<'input>) { }
/**
 * Enter a parse tree produced by {@link SceneMaxNextGenParser#animation_speed_stmt}.
 * @param ctx the parse tree
 */
fn enter_animation_speed_stmt(&mut self, _ctx: &Animation_speed_stmtContext<'input>) { }
/**
 * Exit a parse tree produced by {@link SceneMaxNextGenParser#animation_speed_stmt}.
 * @param ctx the parse tree
 */
fn exit_animation_speed_stmt(&mut self, _ctx: &Animation_speed_stmtContext<'input>) { }
/**
 * Enter a parse tree produced by {@link SceneMaxNextGenParser#sprite_play_stmt}.
 * @param ctx the parse tree
 */
fn enter_sprite_play_stmt(&mut self, _ctx: &Sprite_play_stmtContext<'input>) { }
/**
 * Exit a parse tree produced by {@link SceneMaxNextGenParser#sprite_play_stmt}.
 * @param ctx the parse tree
 */
fn exit_sprite_play_stmt(&mut self, _ctx: &Sprite_play_stmtContext<'input>) { }
/**
 * Enter a parse tree produced by {@link SceneMaxNextGenParser#character_jump_stmt}.
 * @param ctx the parse tree
 */
fn enter_character_jump_stmt(&mut self, _ctx: &Character_jump_stmtContext<'input>) { }
/**
 * Exit a parse tree produced by {@link SceneMaxNextGenParser#character_jump_stmt}.
 * @param ctx the parse tree
 */
fn exit_character_jump_stmt(&mut self, _ctx: &Character_jump_stmtContext<'input>) { }
/**
 * Enter a parse tree produced by {@link SceneMaxNextGenParser#physics_impulse_stmt}.
 * @param ctx the parse tree
 */
fn enter_physics_impulse_stmt(&mut self, _ctx: &Physics_impulse_stmtContext<'input>) { }
/**
 * Exit a parse tree produced by {@link SceneMaxNextGenParser#physics_impulse_stmt}.
 * @param ctx the parse tree
 */
fn exit_physics_impulse_stmt(&mut self, _ctx: &Physics_impulse_stmtContext<'input>) { }
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
/**
 * Enter a parse tree produced by {@link SceneMaxNextGenParser#logical_expression}.
 * @param ctx the parse tree
 */
fn enter_logical_expression(&mut self, _ctx: &Logical_expressionContext<'input>) { }
/**
 * Exit a parse tree produced by {@link SceneMaxNextGenParser#logical_expression}.
 * @param ctx the parse tree
 */
fn exit_logical_expression(&mut self, _ctx: &Logical_expressionContext<'input>) { }
/**
 * Enter a parse tree produced by {@link SceneMaxNextGenParser#boolean_and_expression}.
 * @param ctx the parse tree
 */
fn enter_boolean_and_expression(&mut self, _ctx: &Boolean_and_expressionContext<'input>) { }
/**
 * Exit a parse tree produced by {@link SceneMaxNextGenParser#boolean_and_expression}.
 * @param ctx the parse tree
 */
fn exit_boolean_and_expression(&mut self, _ctx: &Boolean_and_expressionContext<'input>) { }
/**
 * Enter a parse tree produced by {@link SceneMaxNextGenParser#relational_expression}.
 * @param ctx the parse tree
 */
fn enter_relational_expression(&mut self, _ctx: &Relational_expressionContext<'input>) { }
/**
 * Exit a parse tree produced by {@link SceneMaxNextGenParser#relational_expression}.
 * @param ctx the parse tree
 */
fn exit_relational_expression(&mut self, _ctx: &Relational_expressionContext<'input>) { }
/**
 * Enter a parse tree produced by {@link SceneMaxNextGenParser#additive_expression}.
 * @param ctx the parse tree
 */
fn enter_additive_expression(&mut self, _ctx: &Additive_expressionContext<'input>) { }
/**
 * Exit a parse tree produced by {@link SceneMaxNextGenParser#additive_expression}.
 * @param ctx the parse tree
 */
fn exit_additive_expression(&mut self, _ctx: &Additive_expressionContext<'input>) { }
/**
 * Enter a parse tree produced by {@link SceneMaxNextGenParser#multiplicative_expression}.
 * @param ctx the parse tree
 */
fn enter_multiplicative_expression(&mut self, _ctx: &Multiplicative_expressionContext<'input>) { }
/**
 * Exit a parse tree produced by {@link SceneMaxNextGenParser#multiplicative_expression}.
 * @param ctx the parse tree
 */
fn exit_multiplicative_expression(&mut self, _ctx: &Multiplicative_expressionContext<'input>) { }
/**
 * Enter a parse tree produced by {@link SceneMaxNextGenParser#unary_expression}.
 * @param ctx the parse tree
 */
fn enter_unary_expression(&mut self, _ctx: &Unary_expressionContext<'input>) { }
/**
 * Exit a parse tree produced by {@link SceneMaxNextGenParser#unary_expression}.
 * @param ctx the parse tree
 */
fn exit_unary_expression(&mut self, _ctx: &Unary_expressionContext<'input>) { }
/**
 * Enter a parse tree produced by {@link SceneMaxNextGenParser#primary_expression}.
 * @param ctx the parse tree
 */
fn enter_primary_expression(&mut self, _ctx: &Primary_expressionContext<'input>) { }
/**
 * Exit a parse tree produced by {@link SceneMaxNextGenParser#primary_expression}.
 * @param ctx the parse tree
 */
fn exit_primary_expression(&mut self, _ctx: &Primary_expressionContext<'input>) { }
/**
 * Enter a parse tree produced by {@link SceneMaxNextGenParser#function_value}.
 * @param ctx the parse tree
 */
fn enter_function_value(&mut self, _ctx: &Function_valueContext<'input>) { }
/**
 * Exit a parse tree produced by {@link SceneMaxNextGenParser#function_value}.
 * @param ctx the parse tree
 */
fn exit_function_value(&mut self, _ctx: &Function_valueContext<'input>) { }
/**
 * Enter a parse tree produced by {@link SceneMaxNextGenParser#value}.
 * @param ctx the parse tree
 */
fn enter_value(&mut self, _ctx: &ValueContext<'input>) { }
/**
 * Exit a parse tree produced by {@link SceneMaxNextGenParser#value}.
 * @param ctx the parse tree
 */
fn exit_value(&mut self, _ctx: &ValueContext<'input>) { }

}

antlr_rust::coerce_from!{ 'input : SceneMaxNextGenListener<'input> }


