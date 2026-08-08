#![allow(nonstandard_style)]
// Generated from SceneMaxNextGen.g4 by ANTLR 4.8
use antlr_rust::tree::{ParseTreeVisitor,ParseTreeVisitorCompat};
use super::scenemaxnextgenparser::*;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link SceneMaxNextGenParser}.
 */
pub trait SceneMaxNextGenVisitor<'input>: ParseTreeVisitor<'input,SceneMaxNextGenParserContextType>{
	/**
	 * Visit a parse tree produced by {@link SceneMaxNextGenParser#program}.
	 * @param ctx the parse tree
	 */
	fn visit_program(&mut self, ctx: &ProgramContext<'input>) { self.visit_children(ctx) }

	/**
	 * Visit a parse tree produced by {@link SceneMaxNextGenParser#statement}.
	 * @param ctx the parse tree
	 */
	fn visit_statement(&mut self, ctx: &StatementContext<'input>) { self.visit_children(ctx) }

	/**
	 * Visit a parse tree produced by {@link SceneMaxNextGenParser#model_decl}.
	 * @param ctx the parse tree
	 */
	fn visit_model_decl(&mut self, ctx: &Model_declContext<'input>) { self.visit_children(ctx) }

	/**
	 * Visit a parse tree produced by {@link SceneMaxNextGenParser#animate_stmt}.
	 * @param ctx the parse tree
	 */
	fn visit_animate_stmt(&mut self, ctx: &Animate_stmtContext<'input>) { self.visit_children(ctx) }

	/**
	 * Visit a parse tree produced by {@link SceneMaxNextGenParser#speed_clause}.
	 * @param ctx the parse tree
	 */
	fn visit_speed_clause(&mut self, ctx: &Speed_clauseContext<'input>) { self.visit_children(ctx) }

	/**
	 * Visit a parse tree produced by {@link SceneMaxNextGenParser#add_code_stmt}.
	 * @param ctx the parse tree
	 */
	fn visit_add_code_stmt(&mut self, ctx: &Add_code_stmtContext<'input>) { self.visit_children(ctx) }

	/**
	 * Visit a parse tree produced by {@link SceneMaxNextGenParser#qualified_name}.
	 * @param ctx the parse tree
	 */
	fn visit_qualified_name(&mut self, ctx: &Qualified_nameContext<'input>) { self.visit_children(ctx) }

	/**
	 * Visit a parse tree produced by {@link SceneMaxNextGenParser#qualified_name_part}.
	 * @param ctx the parse tree
	 */
	fn visit_qualified_name_part(&mut self, ctx: &Qualified_name_partContext<'input>) { self.visit_children(ctx) }

	/**
	 * Visit a parse tree produced by {@link SceneMaxNextGenParser#animation_name}.
	 * @param ctx the parse tree
	 */
	fn visit_animation_name(&mut self, ctx: &Animation_nameContext<'input>) { self.visit_children(ctx) }

	/**
	 * Visit a parse tree produced by {@link SceneMaxNextGenParser#keyword_identifier}.
	 * @param ctx the parse tree
	 */
	fn visit_keyword_identifier(&mut self, ctx: &Keyword_identifierContext<'input>) { self.visit_children(ctx) }

	/**
	 * Visit a parse tree produced by {@link SceneMaxNextGenParser#number}.
	 * @param ctx the parse tree
	 */
	fn visit_number(&mut self, ctx: &NumberContext<'input>) { self.visit_children(ctx) }

}

pub trait SceneMaxNextGenVisitorCompat<'input>:ParseTreeVisitorCompat<'input, Node= SceneMaxNextGenParserContextType>{
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
		fn visit_qualified_name_part(&mut self, ctx: &Qualified_name_partContext<'input>) -> Self::Return {
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
		fn visit_keyword_identifier(&mut self, ctx: &Keyword_identifierContext<'input>) -> Self::Return {
			self.visit_children(ctx)
		}

	/**
	 * Visit a parse tree produced by {@link SceneMaxNextGenParser#number}.
	 * @param ctx the parse tree
	 */
		fn visit_number(&mut self, ctx: &NumberContext<'input>) -> Self::Return {
			self.visit_children(ctx)
		}

}

impl<'input,T> SceneMaxNextGenVisitor<'input> for T
where
	T: SceneMaxNextGenVisitorCompat<'input>
{
	fn visit_program(&mut self, ctx: &ProgramContext<'input>){
		let result = <Self as SceneMaxNextGenVisitorCompat>::visit_program(self, ctx);
        *<Self as ParseTreeVisitorCompat>::temp_result(self) = result;
	}

	fn visit_statement(&mut self, ctx: &StatementContext<'input>){
		let result = <Self as SceneMaxNextGenVisitorCompat>::visit_statement(self, ctx);
        *<Self as ParseTreeVisitorCompat>::temp_result(self) = result;
	}

	fn visit_model_decl(&mut self, ctx: &Model_declContext<'input>){
		let result = <Self as SceneMaxNextGenVisitorCompat>::visit_model_decl(self, ctx);
        *<Self as ParseTreeVisitorCompat>::temp_result(self) = result;
	}

	fn visit_animate_stmt(&mut self, ctx: &Animate_stmtContext<'input>){
		let result = <Self as SceneMaxNextGenVisitorCompat>::visit_animate_stmt(self, ctx);
        *<Self as ParseTreeVisitorCompat>::temp_result(self) = result;
	}

	fn visit_speed_clause(&mut self, ctx: &Speed_clauseContext<'input>){
		let result = <Self as SceneMaxNextGenVisitorCompat>::visit_speed_clause(self, ctx);
        *<Self as ParseTreeVisitorCompat>::temp_result(self) = result;
	}

	fn visit_add_code_stmt(&mut self, ctx: &Add_code_stmtContext<'input>){
		let result = <Self as SceneMaxNextGenVisitorCompat>::visit_add_code_stmt(self, ctx);
        *<Self as ParseTreeVisitorCompat>::temp_result(self) = result;
	}

	fn visit_qualified_name(&mut self, ctx: &Qualified_nameContext<'input>){
		let result = <Self as SceneMaxNextGenVisitorCompat>::visit_qualified_name(self, ctx);
        *<Self as ParseTreeVisitorCompat>::temp_result(self) = result;
	}

	fn visit_qualified_name_part(&mut self, ctx: &Qualified_name_partContext<'input>){
		let result = <Self as SceneMaxNextGenVisitorCompat>::visit_qualified_name_part(self, ctx);
        *<Self as ParseTreeVisitorCompat>::temp_result(self) = result;
	}

	fn visit_animation_name(&mut self, ctx: &Animation_nameContext<'input>){
		let result = <Self as SceneMaxNextGenVisitorCompat>::visit_animation_name(self, ctx);
        *<Self as ParseTreeVisitorCompat>::temp_result(self) = result;
	}

	fn visit_keyword_identifier(&mut self, ctx: &Keyword_identifierContext<'input>){
		let result = <Self as SceneMaxNextGenVisitorCompat>::visit_keyword_identifier(self, ctx);
        *<Self as ParseTreeVisitorCompat>::temp_result(self) = result;
	}

	fn visit_number(&mut self, ctx: &NumberContext<'input>){
		let result = <Self as SceneMaxNextGenVisitorCompat>::visit_number(self, ctx);
        *<Self as ParseTreeVisitorCompat>::temp_result(self) = result;
	}

}