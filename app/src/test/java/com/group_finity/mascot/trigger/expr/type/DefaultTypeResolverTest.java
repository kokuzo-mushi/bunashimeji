package com.group_finity.mascot.trigger.expr.type;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class DefaultTypeResolverTest {

    private final DefaultTypeResolver resolver = new DefaultTypeResolver();

    @Nested
    @DisplayName("Unary Operator Tests")
    class UnaryOpTests {

        @ParameterizedTest
        @CsvSource({
                "true,    false", // Boolean true
                "false,   true",  // Boolean false
                "1,       false", // Number non-zero
                "0,       true",  // Number zero
                "'text',  false", // Non-empty string
                "'0',     true",  // String "0" is false, so !false is true
                "'',      true"   // Empty string is false, so !false is true
        })
        void testLogicalNot(Object input, boolean expected) {
            assertEquals(expected, resolver.applyUnaryOp("!", input));
        }

        @DisplayName("Logical Not with null input")
        @Test
        void testLogicalNotNull() {
            // !null -> !false -> true
            assertEquals(true, resolver.applyUnaryOp("!", null));
        }

        @ParameterizedTest
        @CsvSource({"5,-5", "0,0", "-10,10", "3.14,-3.14"})
        void testNegation(String input, String expected) {
            Object result = resolver.applyUnaryOp("-", input);
            assertInstanceOf(BigDecimal.class, result);
            assertEquals(0, new BigDecimal(expected).compareTo((BigDecimal) result));
        }

        @Test
        void testUnaryPlus() {
            Object result = resolver.applyUnaryOp("+", "123");
            assertInstanceOf(BigDecimal.class, result);
            assertEquals(0, new BigDecimal("123").compareTo((BigDecimal) result));
        }

        @Test
        void testUnknownUnaryOperator() {
            assertThrows(IllegalArgumentException.class, () -> resolver.applyUnaryOp("?", 1));
        }
    }

    @Nested
    @DisplayName("Binary Operator Tests")
    class BinaryOpTests {

        @Test
        void testLogicalAnd() {
            assertEquals(true, resolver.applyBinaryOp("&&", true, 1));
            assertEquals(false, resolver.applyBinaryOp("&&", true, 0));
        }

        @Test
        void testLogicalOr() {
            assertEquals(true, resolver.applyBinaryOp("||", false, "text"));
            assertEquals(false, resolver.applyBinaryOp("||", false, ""));
        }

        @Test
        void testStringConcatenation() {
            // 片方が文字列の場合、文字列結合にフォールバックすることを確認
            assertEquals("hello5", resolver.applyBinaryOp("+", "hello", 5));
            assertEquals("5hello", resolver.applyBinaryOp("+", 5, "hello"));
            // 数値に変換できない文字列との加算も文字列結合になる
            assertEquals("10abc", resolver.applyBinaryOp("+", 10, "abc"));
        }

        @ParameterizedTest
        @CsvSource({
                "10, 10, true",
                "10, 5, false",
                "'hello', 'hello', true",
                "'hello', 'world', false",
                "true, true, true"
        })
        void testEquality(Object left, Object right, boolean expected) {
            assertEquals(expected, resolver.applyBinaryOp("==", left, right));
            assertEquals(!expected, resolver.applyBinaryOp("!=", left, right));
        }

        @Test
        @DisplayName("Equality with null values")
        void testEqualityWithNulls() {
            // null == null -> true
            assertEquals(true, resolver.applyBinaryOp("==", null, null));
            assertEquals(false, resolver.applyBinaryOp("!=", null, null));

            // 10 == null -> false
            assertEquals(false, resolver.applyBinaryOp("==", 10, null));
            assertEquals(true, resolver.applyBinaryOp("!=", 10, null));

            // null == "hello" -> false
            assertEquals(false, resolver.applyBinaryOp("==", null, "hello"));
            assertEquals(true, resolver.applyBinaryOp("!=", null, "hello"));
        }

        @ParameterizedTest
        @CsvSource({
                "10, 5, true",
                "5, 10, false",
                "10, 10, false"
        })
        void testGreaterThan(int left, int right, boolean expected) {
            assertEquals(expected, resolver.applyBinaryOp(">", left, right));
        }

        @ParameterizedTest
        @CsvSource({
                "10, 5, true",
                "5, 10, false",
                "10, 10, true"
        })
        void testGreaterThanOrEqual(int left, int right, boolean expected) {
            assertEquals(expected, resolver.applyBinaryOp(">=", left, right));
        }

        @Test
        void testAddition() {
            Object result = resolver.applyBinaryOp("+", 10, "20.5");
            assertInstanceOf(BigDecimal.class, result);
            assertEquals(0, new BigDecimal("30.5").compareTo((BigDecimal) result));
        }

        @Test
        void testBooleanAddition() {
            // Booleanは数値 (true=1, false=0) として扱われ、数値加算が優先されることを確認
            Object result = resolver.applyBinaryOp("+", true, false);
            assertInstanceOf(BigDecimal.class, result);
            assertEquals(0, new BigDecimal("1").compareTo((BigDecimal) result));
        }

        @Test
        void testSubtraction() {
            Object result = resolver.applyBinaryOp("-", 100, 25);
            assertInstanceOf(BigDecimal.class, result);
            assertEquals(0, new BigDecimal("75").compareTo((BigDecimal) result));
        }

        @Test
        void testMultiplication() {
            Object result = resolver.applyBinaryOp("*", "2.5", 4);
            assertInstanceOf(BigDecimal.class, result);
            assertEquals(0, new BigDecimal("10.0").compareTo((BigDecimal) result));
        }

        @Test
        void testDivision() {
            Object result = resolver.applyBinaryOp("/", 10, 4);
            assertInstanceOf(BigDecimal.class, result);
            // 10 / 4 = 2.5
            assertEquals(0, new BigDecimal("2.5").compareTo(((BigDecimal) result).stripTrailingZeros()));
        }

        @Test
        void testDivisionByZero() {
            assertNull(resolver.applyBinaryOp("/", 10, 0));
        }

        @Test
        void testRemainder() {
            Object result = resolver.applyBinaryOp("%", 10, 3);
            assertInstanceOf(BigDecimal.class, result);
            assertEquals(0, new BigDecimal("1").compareTo((BigDecimal) result));
        }

        @Test
        void testRemainderByZero() {
            assertNull(resolver.applyBinaryOp("%", 10, 0));
        }

        @Test
        void testArithmeticWithNonNumericStringReturnsNull() {
            // + 以外の算術演算子は、数値に変換できない場合 null を返すことを確認
            assertNull(resolver.applyBinaryOp("-", 10, "abc"));
            assertNull(resolver.applyBinaryOp("*", 10, "abc"));
            assertNull(resolver.applyBinaryOp("/", 10, "abc"));
        }

        @Test
        void testUnknownBinaryOperator() {
            assertThrows(IllegalArgumentException.class, () -> resolver.applyBinaryOp("^", 2, 3));
        }
    }
}