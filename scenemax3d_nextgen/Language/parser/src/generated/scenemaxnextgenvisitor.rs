#![allow(nonstandard_style)]
// Generated from SceneMaxNextGen.g4 by ANTLR 4.8
use super::scenemaxnextgenparser::*;
use antlr_rust::tree::{ParseTreeVisitor, ParseTreeVisitorCompat};

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link SceneMaxNextGenParser}.
 */
pub trait SceneMaxNextGenVisitor<'input>:
    ParseTreeVisitor<'input, SceneMaxNextGenParserContextType>
{
    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#program}.
     * @param ctx the parse tree
     */
    fn visit_program(&mut self, ctx: &ProgramContext<'input>) {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#statement}.
     * @param ctx the parse tree
     */
    fn visit_statement(&mut self, ctx: &StatementContext<'input>) {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#model_decl}.
     * @param ctx the parse tree
     */
    fn visit_model_decl(&mut self, ctx: &Model_declContext<'input>) {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#animate_stmt}.
     * @param ctx the parse tree
     */
    fn visit_animate_stmt(&mut self, ctx: &Animate_stmtContext<'input>) {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#speed_clause}.
     * @param ctx the parse tree
     */
    fn visit_speed_clause(&mut self, ctx: &Speed_clauseContext<'input>) {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#move_stmt}.
     * @param ctx the parse tree
     */
    fn visit_move_stmt(&mut self, ctx: &Move_stmtContext<'input>) {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#move_to_stmt}.
     * @param ctx the parse tree
     */
    fn visit_move_to_stmt(&mut self, ctx: &Move_to_stmtContext<'input>) {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#move_to_destination}.
     * @param ctx the parse tree
     */
    fn visit_move_to_destination(&mut self, ctx: &Move_to_destinationContext<'input>) {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#turn_stmt}.
     * @param ctx the parse tree
     */
    fn visit_turn_stmt(&mut self, ctx: &Turn_stmtContext<'input>) {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#rotate_stmt}.
     * @param ctx the parse tree
     */
    fn visit_rotate_stmt(&mut self, ctx: &Rotate_stmtContext<'input>) {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#move_direction}.
     * @param ctx the parse tree
     */
    fn visit_move_direction(&mut self, ctx: &Move_directionContext<'input>) {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#duration_clause}.
     * @param ctx the parse tree
     */
    fn visit_duration_clause(&mut self, ctx: &Duration_clauseContext<'input>) {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#run_function_stmt}.
     * @param ctx the parse tree
     */
    fn visit_run_function_stmt(&mut self, ctx: &Run_function_stmtContext<'input>) {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#run_every_stmt}.
     * @param ctx the parse tree
     */
    fn visit_run_every_stmt(&mut self, ctx: &Run_every_stmtContext<'input>) {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#function_call}.
     * @param ctx the parse tree
     */
    fn visit_function_call(&mut self, ctx: &Function_callContext<'input>) {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#animation_speed_stmt}.
     * @param ctx the parse tree
     */
    fn visit_animation_speed_stmt(&mut self, ctx: &Animation_speed_stmtContext<'input>) {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#sprite_play_stmt}.
     * @param ctx the parse tree
     */
    fn visit_sprite_play_stmt(&mut self, ctx: &Sprite_play_stmtContext<'input>) {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#character_jump_stmt}.
     * @param ctx the parse tree
     */
    fn visit_character_jump_stmt(&mut self, ctx: &Character_jump_stmtContext<'input>) {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#physics_impulse_stmt}.
     * @param ctx the parse tree
     */
    fn visit_physics_impulse_stmt(&mut self, ctx: &Physics_impulse_stmtContext<'input>) {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#add_code_stmt}.
     * @param ctx the parse tree
     */
    fn visit_add_code_stmt(&mut self, ctx: &Add_code_stmtContext<'input>) {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#qualified_name}.
     * @param ctx the parse tree
     */
    fn visit_qualified_name(&mut self, ctx: &Qualified_nameContext<'input>) {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#qualified_name_part}.
     * @param ctx the parse tree
     */
    fn visit_qualified_name_part(&mut self, ctx: &Qualified_name_partContext<'input>) {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#animation_name}.
     * @param ctx the parse tree
     */
    fn visit_animation_name(&mut self, ctx: &Animation_nameContext<'input>) {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#keyword_identifier}.
     * @param ctx the parse tree
     */
    fn visit_keyword_identifier(&mut self, ctx: &Keyword_identifierContext<'input>) {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#number}.
     * @param ctx the parse tree
     */
    fn visit_number(&mut self, ctx: &NumberContext<'input>) {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#logical_expression}.
     * @param ctx the parse tree
     */
    fn visit_logical_expression(&mut self, ctx: &Logical_expressionContext<'input>) {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#boolean_and_expression}.
     * @param ctx the parse tree
     */
    fn visit_boolean_and_expression(&mut self, ctx: &Boolean_and_expressionContext<'input>) {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#relational_expression}.
     * @param ctx the parse tree
     */
    fn visit_relational_expression(&mut self, ctx: &Relational_expressionContext<'input>) {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#additive_expression}.
     * @param ctx the parse tree
     */
    fn visit_additive_expression(&mut self, ctx: &Additive_expressionContext<'input>) {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#multiplicative_expression}.
     * @param ctx the parse tree
     */
    fn visit_multiplicative_expression(&mut self, ctx: &Multiplicative_expressionContext<'input>) {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#unary_expression}.
     * @param ctx the parse tree
     */
    fn visit_unary_expression(&mut self, ctx: &Unary_expressionContext<'input>) {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#primary_expression}.
     * @param ctx the parse tree
     */
    fn visit_primary_expression(&mut self, ctx: &Primary_expressionContext<'input>) {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#function_value}.
     * @param ctx the parse tree
     */
    fn visit_function_value(&mut self, ctx: &Function_valueContext<'input>) {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#value}.
     * @param ctx the parse tree
     */
    fn visit_value(&mut self, ctx: &ValueContext<'input>) {
        self.visit_children(ctx)
    }
}

pub trait SceneMaxNextGenVisitorCompat<'input>:
    ParseTreeVisitorCompat<'input, Node = SceneMaxNextGenParserContextType>
{
    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#program}.
     * @param ctx the parse tree
     */
    fn visit_program(&mut self, ctx: &ProgramContext<'input>) -> Self::Return {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#statement}.
     * @param ctx the parse tree
     */
    fn visit_statement(&mut self, ctx: &StatementContext<'input>) -> Self::Return {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#model_decl}.
     * @param ctx the parse tree
     */
    fn visit_model_decl(&mut self, ctx: &Model_declContext<'input>) -> Self::Return {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#animate_stmt}.
     * @param ctx the parse tree
     */
    fn visit_animate_stmt(&mut self, ctx: &Animate_stmtContext<'input>) -> Self::Return {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#speed_clause}.
     * @param ctx the parse tree
     */
    fn visit_speed_clause(&mut self, ctx: &Speed_clauseContext<'input>) -> Self::Return {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#move_stmt}.
     * @param ctx the parse tree
     */
    fn visit_move_stmt(&mut self, ctx: &Move_stmtContext<'input>) -> Self::Return {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#move_to_stmt}.
     * @param ctx the parse tree
     */
    fn visit_move_to_stmt(&mut self, ctx: &Move_to_stmtContext<'input>) -> Self::Return {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#move_to_destination}.
     * @param ctx the parse tree
     */
    fn visit_move_to_destination(
        &mut self,
        ctx: &Move_to_destinationContext<'input>,
    ) -> Self::Return {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#turn_stmt}.
     * @param ctx the parse tree
     */
    fn visit_turn_stmt(&mut self, ctx: &Turn_stmtContext<'input>) -> Self::Return {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#rotate_stmt}.
     * @param ctx the parse tree
     */
    fn visit_rotate_stmt(&mut self, ctx: &Rotate_stmtContext<'input>) -> Self::Return {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#move_direction}.
     * @param ctx the parse tree
     */
    fn visit_move_direction(&mut self, ctx: &Move_directionContext<'input>) -> Self::Return {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#duration_clause}.
     * @param ctx the parse tree
     */
    fn visit_duration_clause(&mut self, ctx: &Duration_clauseContext<'input>) -> Self::Return {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#run_function_stmt}.
     * @param ctx the parse tree
     */
    fn visit_run_function_stmt(&mut self, ctx: &Run_function_stmtContext<'input>) -> Self::Return {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#run_every_stmt}.
     * @param ctx the parse tree
     */
    fn visit_run_every_stmt(&mut self, ctx: &Run_every_stmtContext<'input>) -> Self::Return {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#function_call}.
     * @param ctx the parse tree
     */
    fn visit_function_call(&mut self, ctx: &Function_callContext<'input>) -> Self::Return {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#animation_speed_stmt}.
     * @param ctx the parse tree
     */
    fn visit_animation_speed_stmt(
        &mut self,
        ctx: &Animation_speed_stmtContext<'input>,
    ) -> Self::Return {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#sprite_play_stmt}.
     * @param ctx the parse tree
     */
    fn visit_sprite_play_stmt(&mut self, ctx: &Sprite_play_stmtContext<'input>) -> Self::Return {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#character_jump_stmt}.
     * @param ctx the parse tree
     */
    fn visit_character_jump_stmt(
        &mut self,
        ctx: &Character_jump_stmtContext<'input>,
    ) -> Self::Return {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#physics_impulse_stmt}.
     * @param ctx the parse tree
     */
    fn visit_physics_impulse_stmt(
        &mut self,
        ctx: &Physics_impulse_stmtContext<'input>,
    ) -> Self::Return {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#add_code_stmt}.
     * @param ctx the parse tree
     */
    fn visit_add_code_stmt(&mut self, ctx: &Add_code_stmtContext<'input>) -> Self::Return {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#qualified_name}.
     * @param ctx the parse tree
     */
    fn visit_qualified_name(&mut self, ctx: &Qualified_nameContext<'input>) -> Self::Return {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#qualified_name_part}.
     * @param ctx the parse tree
     */
    fn visit_qualified_name_part(
        &mut self,
        ctx: &Qualified_name_partContext<'input>,
    ) -> Self::Return {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#animation_name}.
     * @param ctx the parse tree
     */
    fn visit_animation_name(&mut self, ctx: &Animation_nameContext<'input>) -> Self::Return {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#keyword_identifier}.
     * @param ctx the parse tree
     */
    fn visit_keyword_identifier(
        &mut self,
        ctx: &Keyword_identifierContext<'input>,
    ) -> Self::Return {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#number}.
     * @param ctx the parse tree
     */
    fn visit_number(&mut self, ctx: &NumberContext<'input>) -> Self::Return {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#logical_expression}.
     * @param ctx the parse tree
     */
    fn visit_logical_expression(
        &mut self,
        ctx: &Logical_expressionContext<'input>,
    ) -> Self::Return {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#boolean_and_expression}.
     * @param ctx the parse tree
     */
    fn visit_boolean_and_expression(
        &mut self,
        ctx: &Boolean_and_expressionContext<'input>,
    ) -> Self::Return {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#relational_expression}.
     * @param ctx the parse tree
     */
    fn visit_relational_expression(
        &mut self,
        ctx: &Relational_expressionContext<'input>,
    ) -> Self::Return {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#additive_expression}.
     * @param ctx the parse tree
     */
    fn visit_additive_expression(
        &mut self,
        ctx: &Additive_expressionContext<'input>,
    ) -> Self::Return {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#multiplicative_expression}.
     * @param ctx the parse tree
     */
    fn visit_multiplicative_expression(
        &mut self,
        ctx: &Multiplicative_expressionContext<'input>,
    ) -> Self::Return {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#unary_expression}.
     * @param ctx the parse tree
     */
    fn visit_unary_expression(&mut self, ctx: &Unary_expressionContext<'input>) -> Self::Return {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#primary_expression}.
     * @param ctx the parse tree
     */
    fn visit_primary_expression(
        &mut self,
        ctx: &Primary_expressionContext<'input>,
    ) -> Self::Return {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#function_value}.
     * @param ctx the parse tree
     */
    fn visit_function_value(&mut self, ctx: &Function_valueContext<'input>) -> Self::Return {
        self.visit_children(ctx)
    }

    /**
     * Visit a parse tree produced by {@link SceneMaxNextGenParser#value}.
     * @param ctx the parse tree
     */
    fn visit_value(&mut self, ctx: &ValueContext<'input>) -> Self::Return {
        self.visit_children(ctx)
    }
}

impl<'input, T> SceneMaxNextGenVisitor<'input> for T
where
    T: SceneMaxNextGenVisitorCompat<'input>,
{
    fn visit_program(&mut self, ctx: &ProgramContext<'input>) {
        let result = <Self as SceneMaxNextGenVisitorCompat>::visit_program(self, ctx);
        *<Self as ParseTreeVisitorCompat>::temp_result(self) = result;
    }

    fn visit_statement(&mut self, ctx: &StatementContext<'input>) {
        let result = <Self as SceneMaxNextGenVisitorCompat>::visit_statement(self, ctx);
        *<Self as ParseTreeVisitorCompat>::temp_result(self) = result;
    }

    fn visit_model_decl(&mut self, ctx: &Model_declContext<'input>) {
        let result = <Self as SceneMaxNextGenVisitorCompat>::visit_model_decl(self, ctx);
        *<Self as ParseTreeVisitorCompat>::temp_result(self) = result;
    }

    fn visit_animate_stmt(&mut self, ctx: &Animate_stmtContext<'input>) {
        let result = <Self as SceneMaxNextGenVisitorCompat>::visit_animate_stmt(self, ctx);
        *<Self as ParseTreeVisitorCompat>::temp_result(self) = result;
    }

    fn visit_speed_clause(&mut self, ctx: &Speed_clauseContext<'input>) {
        let result = <Self as SceneMaxNextGenVisitorCompat>::visit_speed_clause(self, ctx);
        *<Self as ParseTreeVisitorCompat>::temp_result(self) = result;
    }

    fn visit_move_stmt(&mut self, ctx: &Move_stmtContext<'input>) {
        let result = <Self as SceneMaxNextGenVisitorCompat>::visit_move_stmt(self, ctx);
        *<Self as ParseTreeVisitorCompat>::temp_result(self) = result;
    }

    fn visit_move_to_stmt(&mut self, ctx: &Move_to_stmtContext<'input>) {
        let result = <Self as SceneMaxNextGenVisitorCompat>::visit_move_to_stmt(self, ctx);
        *<Self as ParseTreeVisitorCompat>::temp_result(self) = result;
    }

    fn visit_move_to_destination(&mut self, ctx: &Move_to_destinationContext<'input>) {
        let result = <Self as SceneMaxNextGenVisitorCompat>::visit_move_to_destination(self, ctx);
        *<Self as ParseTreeVisitorCompat>::temp_result(self) = result;
    }

    fn visit_turn_stmt(&mut self, ctx: &Turn_stmtContext<'input>) {
        let result = <Self as SceneMaxNextGenVisitorCompat>::visit_turn_stmt(self, ctx);
        *<Self as ParseTreeVisitorCompat>::temp_result(self) = result;
    }

    fn visit_rotate_stmt(&mut self, ctx: &Rotate_stmtContext<'input>) {
        let result = <Self as SceneMaxNextGenVisitorCompat>::visit_rotate_stmt(self, ctx);
        *<Self as ParseTreeVisitorCompat>::temp_result(self) = result;
    }

    fn visit_move_direction(&mut self, ctx: &Move_directionContext<'input>) {
        let result = <Self as SceneMaxNextGenVisitorCompat>::visit_move_direction(self, ctx);
        *<Self as ParseTreeVisitorCompat>::temp_result(self) = result;
    }

    fn visit_duration_clause(&mut self, ctx: &Duration_clauseContext<'input>) {
        let result = <Self as SceneMaxNextGenVisitorCompat>::visit_duration_clause(self, ctx);
        *<Self as ParseTreeVisitorCompat>::temp_result(self) = result;
    }

    fn visit_run_function_stmt(&mut self, ctx: &Run_function_stmtContext<'input>) {
        let result = <Self as SceneMaxNextGenVisitorCompat>::visit_run_function_stmt(self, ctx);
        *<Self as ParseTreeVisitorCompat>::temp_result(self) = result;
    }

    fn visit_run_every_stmt(&mut self, ctx: &Run_every_stmtContext<'input>) {
        let result = <Self as SceneMaxNextGenVisitorCompat>::visit_run_every_stmt(self, ctx);
        *<Self as ParseTreeVisitorCompat>::temp_result(self) = result;
    }

    fn visit_function_call(&mut self, ctx: &Function_callContext<'input>) {
        let result = <Self as SceneMaxNextGenVisitorCompat>::visit_function_call(self, ctx);
        *<Self as ParseTreeVisitorCompat>::temp_result(self) = result;
    }

    fn visit_animation_speed_stmt(&mut self, ctx: &Animation_speed_stmtContext<'input>) {
        let result = <Self as SceneMaxNextGenVisitorCompat>::visit_animation_speed_stmt(self, ctx);
        *<Self as ParseTreeVisitorCompat>::temp_result(self) = result;
    }

    fn visit_sprite_play_stmt(&mut self, ctx: &Sprite_play_stmtContext<'input>) {
        let result = <Self as SceneMaxNextGenVisitorCompat>::visit_sprite_play_stmt(self, ctx);
        *<Self as ParseTreeVisitorCompat>::temp_result(self) = result;
    }

    fn visit_character_jump_stmt(&mut self, ctx: &Character_jump_stmtContext<'input>) {
        let result = <Self as SceneMaxNextGenVisitorCompat>::visit_character_jump_stmt(self, ctx);
        *<Self as ParseTreeVisitorCompat>::temp_result(self) = result;
    }

    fn visit_physics_impulse_stmt(&mut self, ctx: &Physics_impulse_stmtContext<'input>) {
        let result = <Self as SceneMaxNextGenVisitorCompat>::visit_physics_impulse_stmt(self, ctx);
        *<Self as ParseTreeVisitorCompat>::temp_result(self) = result;
    }

    fn visit_add_code_stmt(&mut self, ctx: &Add_code_stmtContext<'input>) {
        let result = <Self as SceneMaxNextGenVisitorCompat>::visit_add_code_stmt(self, ctx);
        *<Self as ParseTreeVisitorCompat>::temp_result(self) = result;
    }

    fn visit_qualified_name(&mut self, ctx: &Qualified_nameContext<'input>) {
        let result = <Self as SceneMaxNextGenVisitorCompat>::visit_qualified_name(self, ctx);
        *<Self as ParseTreeVisitorCompat>::temp_result(self) = result;
    }

    fn visit_qualified_name_part(&mut self, ctx: &Qualified_name_partContext<'input>) {
        let result = <Self as SceneMaxNextGenVisitorCompat>::visit_qualified_name_part(self, ctx);
        *<Self as ParseTreeVisitorCompat>::temp_result(self) = result;
    }

    fn visit_animation_name(&mut self, ctx: &Animation_nameContext<'input>) {
        let result = <Self as SceneMaxNextGenVisitorCompat>::visit_animation_name(self, ctx);
        *<Self as ParseTreeVisitorCompat>::temp_result(self) = result;
    }

    fn visit_keyword_identifier(&mut self, ctx: &Keyword_identifierContext<'input>) {
        let result = <Self as SceneMaxNextGenVisitorCompat>::visit_keyword_identifier(self, ctx);
        *<Self as ParseTreeVisitorCompat>::temp_result(self) = result;
    }

    fn visit_number(&mut self, ctx: &NumberContext<'input>) {
        let result = <Self as SceneMaxNextGenVisitorCompat>::visit_number(self, ctx);
        *<Self as ParseTreeVisitorCompat>::temp_result(self) = result;
    }

    fn visit_logical_expression(&mut self, ctx: &Logical_expressionContext<'input>) {
        let result = <Self as SceneMaxNextGenVisitorCompat>::visit_logical_expression(self, ctx);
        *<Self as ParseTreeVisitorCompat>::temp_result(self) = result;
    }

    fn visit_boolean_and_expression(&mut self, ctx: &Boolean_and_expressionContext<'input>) {
        let result =
            <Self as SceneMaxNextGenVisitorCompat>::visit_boolean_and_expression(self, ctx);
        *<Self as ParseTreeVisitorCompat>::temp_result(self) = result;
    }

    fn visit_relational_expression(&mut self, ctx: &Relational_expressionContext<'input>) {
        let result = <Self as SceneMaxNextGenVisitorCompat>::visit_relational_expression(self, ctx);
        *<Self as ParseTreeVisitorCompat>::temp_result(self) = result;
    }

    fn visit_additive_expression(&mut self, ctx: &Additive_expressionContext<'input>) {
        let result = <Self as SceneMaxNextGenVisitorCompat>::visit_additive_expression(self, ctx);
        *<Self as ParseTreeVisitorCompat>::temp_result(self) = result;
    }

    fn visit_multiplicative_expression(&mut self, ctx: &Multiplicative_expressionContext<'input>) {
        let result =
            <Self as SceneMaxNextGenVisitorCompat>::visit_multiplicative_expression(self, ctx);
        *<Self as ParseTreeVisitorCompat>::temp_result(self) = result;
    }

    fn visit_unary_expression(&mut self, ctx: &Unary_expressionContext<'input>) {
        let result = <Self as SceneMaxNextGenVisitorCompat>::visit_unary_expression(self, ctx);
        *<Self as ParseTreeVisitorCompat>::temp_result(self) = result;
    }

    fn visit_primary_expression(&mut self, ctx: &Primary_expressionContext<'input>) {
        let result = <Self as SceneMaxNextGenVisitorCompat>::visit_primary_expression(self, ctx);
        *<Self as ParseTreeVisitorCompat>::temp_result(self) = result;
    }

    fn visit_function_value(&mut self, ctx: &Function_valueContext<'input>) {
        let result = <Self as SceneMaxNextGenVisitorCompat>::visit_function_value(self, ctx);
        *<Self as ParseTreeVisitorCompat>::temp_result(self) = result;
    }

    fn visit_value(&mut self, ctx: &ValueContext<'input>) {
        let result = <Self as SceneMaxNextGenVisitorCompat>::visit_value(self, ctx);
        *<Self as ParseTreeVisitorCompat>::temp_result(self) = result;
    }
}
