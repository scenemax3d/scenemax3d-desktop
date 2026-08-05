grammar SceneMaxNextGen;

program
    : statement* EOF
    ;

statement
    : model_decl
    | animate_stmt
    | add_code_stmt
    ;

model_decl
    : target=IDENT ISA resource=qualified_name
    ;

animate_stmt
    : target=IDENT DOT animation=animation_name speed_clause? LOOP?
    ;

speed_clause
    : AT SPEED OF number
    ;

add_code_stmt
    : ADD QUOTED_STRING CODE
    ;

qualified_name
    : qualified_name_part (DOT qualified_name_part)*
    ;

qualified_name_part
    : IDENT
    | keyword_identifier
    ;

animation_name
    : IDENT
    | QUOTED_STRING
    | keyword_identifier
    ;

keyword_identifier
    : LOOP
    | ADD
    | CODE
    | AT
    | SPEED
    | OF
    ;

number
    : SIGN? DECIMAL
    ;

ISA
    : '=>'
    | 'is a'
    | 'Is a'
    | 'is A'
    | 'Is A'
    ;

LOOP
    : 'loop'
    | 'Loop'
    ;

ADD
    : 'Add'
    | 'add'
    ;

CODE
    : 'Code'
    | 'code'
    ;

AT
    : 'at'
    | 'At'
    ;

SPEED
    : 'speed'
    | 'Speed'
    ;

OF
    : 'of'
    | 'Of'
    ;

DOT
    : '.'
    ;

SIGN
    : '+'
    | '-'
    ;

DECIMAL
    : [0-9]+ ('.' [0-9]+)?
    ;

IDENT
    : [a-zA-Z_$] [a-zA-Z_$0-9]*
    ;

QUOTED_STRING
    : '"' ( '\\"' | ~["\r\n] )* '"'
    ;

LINE_COMMENT
    : '//' ~[\r\n]* -> skip
    ;

BLOCK_COMMENT
    : '/*' .*? '*/' -> skip
    ;

WS
    : [ \t\r\n]+ -> channel(HIDDEN)
    ;
