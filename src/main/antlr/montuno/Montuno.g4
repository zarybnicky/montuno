grammar Montuno;
@header {
package montuno;
}

file : END* decls+=top? (END+ decls+=top)* END* EOF ;
top
    : id=ident ':' type=term                   #Decl
    | id=binder (':' type=term)? '=' defn=term #Defn
    | '{-#' 'RESET' '#-}'                      #Reset
    | '{-#' 'BUILTIN' (ids+=ident)* '#-}'      #Builtin
    | '{-#' 'PRINT' '#-}'                      #Print
    | '{-#' cmd term '#-}'                     #Pragma
    | term                                     #Expr
    ;
cmd : 'PARSE' | 'RAW' | 'PRETTY' | 'NORMAL' | 'TYPE' | 'NORMAL_TYPE' ;
term : lambda (',' tuple+=term)* ;
lambda
    : LAMBDA (rands+=lamBind)+ '.' body=lambda #Lam
    | 'let' ident ':' type=term '=' defn=term 'in' body=lambda #LetType
    | 'let' ident '=' defn=term 'in' body=lambda #Let
    | (spine+=piBinder)+ ARROW body=lambda     #Pi
    | sigma ARROW body=lambda                  #Fun
    | sigma                                  #LamTerm
    ;
sigma
    : '(' binder ':' type=term ')' TIMES body=term #SgNamed
    | type=app TIMES body=term           #SgAnon
    | app                                #SigmaTerm
    ;
app : proj (args+=arg)* ;
proj
    : proj '.' ident #ProjNamed
    | proj '.1'      #ProjFst
    | proj '.2'      #ProjSnd
    | atom           #ProjTerm
    ;
arg
    : '{' (ident '=')? term '}' #ArgImpl
    | proj                      #ArgExpl
    ;
piBinder
    : '(' (ids+=binder)+ ':' type=term ')'    #PiExpl
    | '{' (ids+=binder)+ (':' type=term)? '}' #PiImpl
    ;
lamBind
    : binder                   #LamExpl
    | '{' binder '}'           #LamImpl
    | '{' ident '=' binder '}' #LamName
    ;
atom
    : '(' term ')'             #Rec
    | ident                    #Var
    | '_'                      #Hole
    | ('()' | 'Unit' | 'Type') #Star
    | inat                     #Nat
    | '[' ident '|' FOREIGN? '|' term ']' #Foreign
    ;
binder
    : ident #Bind
    | '_' #Irrel
    ;
ident : LETTER ICHAR*;
inat: DIGIT+;

LETTER : [a-zA-Z];
ICHAR : [a-zA-Z0-9'_];
DIGIT : [0-9];

END : (SEMICOLON | NEWLINE) NEWLINE*;
fragment SEMICOLON : ';';
fragment NEWLINE : '\r'? '\n' | '\r';
SPACES : [ \t] -> skip;
LINE_COMMENT : '--' (~[\r\n])* -> skip;
BLOCK_COMMENT : '{-'~[#] .*? '-}' -> skip;
LAMBDA : '\\' | 'λ';
ARROW : '->' | '→';
TIMES : '×' | '*';
FOREIGN : [^|]+;
