grammar SceneMaxNextGen;

program
    : statement* EOF
    ;

statement
    : model_decl
    | animate_stmt
    | move_stmt
    | move_to_stmt
    | turn_stmt
    | rotate_stmt
    | run_function_stmt
    | run_every_stmt
    | animation_speed_stmt
    | sprite_play_stmt
    | character_jump_stmt
    | physics_impulse_stmt
    | add_code_stmt
    ;

model_decl
    : target=IDENT ISA resource=qualified_name
    ;

animate_stmt
    : target=IDENT DOT animation=animation_name speed_clause? LOOP?
    ;

speed_clause
    : AT SPEED OF logical_expression
    ;

move_stmt
    : target=IDENT DOT MOVE direction=move_direction distance=logical_expression duration_clause? LOOP?
    ;

move_to_stmt
    : target=IDENT DOT MOVE TO LPAREN destination=move_to_destination RPAREN duration_clause? ASYNC?
    ;

move_to_destination
    : logical_expression COMMA logical_expression COMMA logical_expression
    | qualified_name FORWARD logical_expression
    | qualified_name
    ;

turn_stmt
    : target=IDENT DOT TURN (LEFT | RIGHT)? logical_expression duration_clause? LOOP? ASYNC?
    ;

rotate_stmt
    : target=IDENT DOT ROTATE LPAREN qualified_name (PLUS | MINUS) logical_expression RPAREN duration_clause? LOOP? ASYNC?
    ;

move_direction
    : FORWARD
    | BACKWARD
    | BACK
    | LEFT
    | RIGHT
    | UP
    | DOWN
    ;

duration_clause
    : (IN | FOR) logical_expression SECONDS
    ;

run_function_stmt
    : RUN function_call ASYNC?
    ;

run_every_stmt
    : RUN function_call EVERY logical_expression SECONDS
    ;

function_call
    : qualified_name LPAREN (logical_expression (COMMA logical_expression)*)? RPAREN
    | qualified_name
    ;

animation_speed_stmt
    : target=IDENT DOT ANIMATION SPEED logical_expression (FOR logical_expression SECONDS)? (WHEN logical_expression)?
    ;

sprite_play_stmt
    : target=IDENT DOT PLAY FRAME logical_expression TO logical_expression duration_clause? LOOP?
    ;

character_jump_stmt
    : target=IDENT DOT CHARACTER DOT JUMP (AT SPEED OF | SPEED OF | SPEED) logical_expression ASYNC?
    ;

physics_impulse_stmt
    : target=IDENT DOT PHYSICS IMPULSE direction=move_direction logical_expression
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
    | MOVE
    | FORWARD
    | BACKWARD
    | BACK
    | LEFT
    | RIGHT
    | UP
    | DOWN
    | IN
    | FOR
    | SECONDS
    | TO
    | TURN
    | ROTATE
    | RUN
    | EVERY
    | ASYNC
    | ANIMATION
    | PLAY
    | FRAME
    | CHARACTER
    | JUMP
    | PHYSICS
    | IMPULSE
    | WHEN
    | TRUE
    | FALSE
    ;

number
    : (PLUS | MINUS)? DECIMAL
    ;

logical_expression
    : boolean_and_expression (OR boolean_and_expression)*
    ;

boolean_and_expression
    : relational_expression (AND relational_expression)*
    ;

relational_expression
    : additive_expression ((LT | LTEQ | GT | GTEQ | EQUALS | NOTEQUALS) additive_expression)*
    ;

additive_expression
    : multiplicative_expression ((PLUS | MINUS) multiplicative_expression)*
    ;

multiplicative_expression
    : unary_expression ((MULT | DIV | MOD) unary_expression)*
    ;

unary_expression
    : NOT? primary_expression
    ;

primary_expression
    : LPAREN logical_expression RPAREN
    | function_value
    | value
    ;

function_value
    : qualified_name LPAREN (logical_expression (COMMA logical_expression)*)? RPAREN
    ;

value
    : number
    | QUOTED_STRING
    | TRUE
    | FALSE
    | qualified_name
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

MOVE
    : 'move'
    | 'Move'
    ;

FORWARD
    : 'forward'
    | 'Forward'
    ;

BACKWARD
    : 'backward'
    | 'Backward'
    ;

BACK
    : 'back'
    | 'Back'
    ;

LEFT
    : 'left'
    | 'Left'
    ;

RIGHT
    : 'right'
    | 'Right'
    ;

UP
    : 'up'
    | 'Up'
    ;

DOWN
    : 'down'
    | 'Down'
    ;

IN
    : 'in'
    | 'In'
    ;

FOR
    : 'for'
    | 'For'
    ;

SECONDS
    : 'seconds'
    | 'Seconds'
    | 'second'
    | 'Second'
    ;

TO
    : 'to'
    | 'To'
    ;

TURN
    : 'turn'
    | 'Turn'
    ;

ROTATE
    : 'rotate'
    | 'Rotate'
    ;

RUN
    : 'run'
    | 'Run'
    ;

EVERY
    : 'every'
    | 'Every'
    ;

ASYNC
    : 'async'
    | 'Async'
    ;

ANIMATION
    : 'animation'
    | 'Animation'
    ;

PLAY
    : 'play'
    | 'Play'
    ;

FRAME
    : 'frame'
    | 'Frame'
    ;

CHARACTER
    : 'character'
    | 'Character'
    ;

JUMP
    : 'jump'
    | 'Jump'
    ;

PHYSICS
    : 'physics'
    | 'Physics'
    ;

IMPULSE
    : 'impulse'
    | 'Impulse'
    ;

WHEN
    : 'when'
    | 'When'
    ;

TRUE
    : 'true'
    | 'True'
    ;

FALSE
    : 'false'
    | 'False'
    ;

DOT
    : '.'
    ;

LPAREN
    : '('
    ;

RPAREN
    : ')'
    ;

COMMA
    : ','
    ;

OR
    : '||'
    | 'or'
    | 'Or'
    ;

AND
    : '&&'
    | 'and'
    | 'And'
    ;

EQUALS
    : '=='
    ;

NOTEQUALS
    : '!='
    | '<>'
    ;

LTEQ
    : '<='
    ;

GTEQ
    : '>='
    ;

LT
    : '<'
    ;

GT
    : '>'
    ;

PLUS
    : '+'
    ;

MINUS
    : '-'
    ;

MULT
    : '*'
    ;

DIV
    : '/'
    ;

MOD
    : '%'
    ;

NOT
    : '!'
    | 'not'
    | 'Not'
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
