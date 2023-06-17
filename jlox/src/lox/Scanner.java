package lox;

import static lox.TokenType.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Scanner {
    private char c;
    //the source file as one big string
    private final String source;
    //The list of tokens for the source file is stored as an ArrayList. Remember List<Token> is an interface and ArrayList<> is the implementation.
    //The empty angle-brackets are part of Java's diamond operator. It's inferring the type from the LHS. '= new ArrayList<Token>()' is equivalent
    private final List<Token> tokens = new ArrayList<>();
    //Pointers in the source file for scanning. Each tokenization will use these two pointers to munch characters.
    //'start' will point to the beginning of a new token to consume. Every time a token is consumed, 'start' gets updated to point to the first character
    //of the next token. 'current' is the pointer that increments as the token is consumed. It is constantly increased with advance() to scan a whole
    //token until the token is fully consumed. It is then set to 'start' afterwards.
    private int start = 0;
    private int current = 0;
    //Line number is kept up for error reporting.
    private int line = 1;

    //Initializer method sets the source field to the one passed in the class instantiation
    Scanner(String source) {
        this.source = source;
    }

    //The wrapper function called by the Lox class to begin the tokenization process. Is a wrapper because it calles the main function 'scanToken()' until EOF.
    //Note this is where we 'restart' the token process with 'start = current'
    List<Token> scanTokens() {
        while (!isAtEnd()) {
            start = current;
            scanToken();
        }

        //Add the EOF token to the tail of the list. Not sure why we aren't calling addToken() 
        tokens.add(new Token(EOF, "", null, line));
        return tokens;
    }

    //Important function to scan the current token. For any given character 'c', try to match it to plenty of others with a switch statement.
    //rundown; at any time the char 'c' under consideration is NOT the one pointed to by 'current'. this is because we do 'char c = advance()'
    //at the beginning of scanToken(). thus we have 1 character of lookahead in our scanner.
    //'peek()' is how we can access the character pointed to by 'current.' 'match()' looks at this 
    private void scanToken() {
        c = advance();
        switch (c) {
            //single-character tokens
            case '(': addToken(LEFT_PAREN); break;
            case ')': addToken(RIGHT_PAREN); break;
            case '{': addToken(LEFT_BRACE); break;
            case '}': addToken(RIGHT_BRACE); break;
            case ',': addToken(COMMA); break;
            case '.': addToken(DOT); break;
            case '-': addToken(MINUS); break;
            case '+': addToken(PLUS); break;
            case ';': addToken(SEMICOLON); break;
            case '*': addToken(STAR); break;
            //operators -- need a way to match the next character with a comparison
            case '!':
                addToken(match('=') ? BANG_EQUAL : BANG);
                break;
            case '=':
                addToken(match('=') ? EQUAL_EQUAL : EQUAL);
                break;
            case '<':
                addToken(match('=') ? LESS_EQUAL : LESS);
                break;
            case '>':
                addToken(match('=') ? GREATER_EQUAL : GREATER);
                break;
            //might be a comment OR might be a division. if it's a comment we will match ('/'). then we call peek and advance -- look at source.charAt(current) and
            //advance -- until we hit the newline. also we will consider EOF the ending of a comment.
            //BTW, comments don't get added as tokens, and instead just disappear 
            case '/':
                if (match('/')) {
                    while (peek() != '\n' && !isAtEnd()) {
                        advance();
                    }
                } 
                //multi-line comment
                else if (match('*')) {
                    if (isAtEnd()) {
                        Lox.error(line, "UMLC.");
                        break;
                    }
                    while (!isAtEnd()) {
                        if ((peek() == '*' && peekNext() == '/')) {
                            advance(2);
                            break;
                        }
                        advance();
                    }
                } else {
                    addToken(SLASH);
                }
                break;
            //ignore whitespace
            case ' ':
            case '\r':
            case '\t':
                break;
            //getting here from the above '//' case will not put us past '\n' -- remember peek() doesn't consume it just reports current.
            //also since that case doesn't break, that'll get handled here. also if that case is skipped, here is still where newline
            //handline ('line++') occurrs!
            case '\n':
                line++;
                break;
            //found a '"' -> begin string munch
            case '"': string(); break;
            default:
                if (isDigit(c)) {
                    number();
                }
                //here to handle reserved words. we can't simply just do switches for each first
                //letter however -- consider 'or' vs 'orchid.' if we switched, we'd always view it
                //as an 'or' and never an 'orchid.' we need to employ 'maximal munch', which means
                //we should always accept the longer of two choices.
                else if (isAlpha(c)) {
                    identifier();
                }
                else {
                    //Since the character doesn't match to any valid cases, we know it's an error by default.
                    //We're going to keep scanning too -- advance() is still called -- but sincce Lox.error() flags hadError, it won't be executed.
                    Lox.error(line, "Unexpected character ' " + c + "'.");
                    break;
                }
        }
    }

    private void identifier() {
        while (isAlphaNumeric(peek())) advance();
        
        String text = source.substring(start, current);
        TokenType type = keywords.get(text);
        if (type == null) type = IDENTIFIER;
        addToken(type);
    }
    
    private void number() {
        //as long as we are still looking at a number, consume it. note that we had a lead-in number from 'scanToken()'
        while (isDigit(peek())) advance();
        //found the fractional part. does not mean it actually IS a fractional part -- so we check one extra character of lookahead with 'peekNext()'
        if (peek() == '.' && isDigit(peekNext())) {
            //consume '.'
            advance();
            //finish off number
            while (isDigit(peek())) advance();
        }

        addToken(NUMBER, Double.parseDouble(source.substring(start, current)));

    }

    private char peekNext() {
        if (current + 1 >= source.length()) return '\0';
        //extra lookahead
        return source.charAt(current + 1);
    }

    private boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') ||
        (c >= 'A' && c <= 'Z') ||
        c == '_';
    }

    private boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private void string() {
        //While we are not currently looking at ('current =') the closing ", 
        //and we aren't at the end, 'advance()' and also 'line++' if newline

        while (peek() != '"' && !isAtEnd()) {
            if (peek() == '\n') line++;
            advance();
        }
        //if we hit the end while consuming a string, lox error
        if (isAtEnd()) {
            Lox.error(line, "Unterminated string.");
        }
        //final '"'
        advance();

        String value = source.substring(start + 1, current - 1);
        addToken(STRING, value);
    }

    //a conditional 'advance()'. Only consumes the character (does 'current++') if the next character is expected.
    //it's important to note that whenever we call match() or even do source.charAt(current), we are not looking at the current value of 'c'.
    //basically, at the top of scanToken() we've already advanced 'current' one step further so we can use source.charAt(current) to refer to the 'next'
    //character
    private boolean match(char expected) {
        if (isAtEnd()) return false;
        
        if (source.charAt(current) != expected) return false; //same as peek()?!

        current++; //same as advance()?!
        return true;
    }

    //essentially a wrapper for 'source.charAt(current)'. note the error handling for EOF, which returns a '\0'.
    private char peek() {
        if (isAtEnd()) return '\0';
        return source.charAt(current);
    }

    private boolean isAtEnd() {
        return current >= source.length();
    }

    //used in the main scanTokens() call loop -- increments the 'current' pointer and returns the character there.
    private char advance() {
        return source.charAt(current++);
    }
    private char advance(int by) {
        for (int i = 0; i < by; i++) {
            advance();
        }
        return source.charAt(current - 1);
    }

    //add a non-literal token (overloaded addToken())
    private void addToken(TokenType type) {
        addToken(type, null);
    }
    //add a token with a literal
    private void addToken(TokenType type, Object literal) {
        String text = source.substring(start, current);
        tokens.add(new Token(type, text, literal, line));
    }

    private static final Map<String, TokenType> keywords;

    static {
        keywords = new HashMap<>();
        keywords.put("and", AND);
        keywords.put("class", CLASS);
        keywords.put("else", ELSE);
        keywords.put("false", FALSE);
        keywords.put("for", FOR);
        keywords.put("fun", FUN);
        keywords.put("if", IF);
        keywords.put("nil", NIL);
        keywords.put("or", OR);
        keywords.put("print", PRINT);
        keywords.put("return", RETURN);
        keywords.put("super", SUPER);
        keywords.put("this", THIS);
        keywords.put("true", TRUE);
        keywords.put("var", VAR);
        keywords.put("while", WHILE);
    }

    private void debugLookahead() {
        System.out.println("c: " + c + " | peek(): " + peek() + " | peeknext(): " + peekNext());
    }
}

