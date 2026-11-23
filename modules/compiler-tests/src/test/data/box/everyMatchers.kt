package sample

import io.lokki.LokkiException
import io.lokki.Matcher
import io.lokki.every
import io.lokki.mock

interface Shape

class Circle(val radius: Int) : Shape

class Square(val side: Int) : Shape

interface Canvas {
    fun draw(shape: Shape): String
    fun scale(factor: Number): String
    fun label(text: String?, size: Int): String
    fun <T> put(item: T): String
}

private fun answer(block: () -> String): String =
    try {
        block()
    } catch (e: LokkiException) {
        "none"
    }

fun equality(): String? {
    val canvas = mock<Canvas>()
    every { canvas.label(eq("a"), neq(1)) } returns "eq"
    if (canvas.label("a", 2) != "eq") return "eq and neq"
    if (answer { canvas.label("a", 1) } != "none") return "neq of the same value"
    if (answer { canvas.label("b", 2) } != "none") return "eq of another value"
    if (answer { canvas.label(null, 2) } != "none") return "eq of null"

    val inverse = true
    every { canvas.label(eq("x", inverse), eq(0)) } returns "inverse"
    if (canvas.label("y", 0) != "inverse" || canvas.label(null, 0) != "inverse") return "eq with inverse"
    if (answer { canvas.label("x", 0) } != "none") return "inverse eq of the same value"

    val text = buildString { append("ref") }
    every { canvas.label(refEq(text), 3) } returns "same"
    every { canvas.label(nrefEq(text), 4) } returns "other"
    if (canvas.label(text, 3) != "same") return "refEq of the same instance"
    if (answer { canvas.label(buildString { append("ref") }, 3) } != "none") return "refEq of an equal instance"
    if (canvas.label(buildString { append("ref") }, 4) != "other") return "nrefEq of an equal instance"
    if (answer { canvas.label(text, 4) } != "none") return "nrefEq of the same instance"

    every { canvas.label(isNull(), 5) } returns "null"
    every { canvas.label(isNull(inverse = true), 6) } returns "not null"
    if (canvas.label(null, 5) != "null" || canvas.label("a", 6) != "not null") return "isNull"
    if (answer { canvas.label("b", 5) } != "none" || answer { canvas.label(null, 6) } != "none") return "isNull of another"
    return null
}

fun comparison(): String? {
    val canvas = mock<Canvas>()
    every { canvas.label("more", more(5)) } returns "more"
    every { canvas.label("more or equal", more(5, andEquals = true)) } returns "more or equal"
    every { canvas.label("less", less(5)) } returns "less"
    every { canvas.label("less or equal", less(5, true)) } returns "less or equal"
    every { canvas.label("equal", cmpEq(5)) } returns "equal"
    every { canvas.label("range", range(1, 3)) } returns "range"
    every { canvas.label("open range", range(1, 3, fromInclusive = false, toInclusive = false)) } returns "open range"
    if (canvas.label("more", 6) != "more" || answer { canvas.label("more", 5) } != "none") return "more"
    if (canvas.label("more or equal", 5) != "more or equal") return "more or equal"
    if (canvas.label("less", 4) != "less" || answer { canvas.label("less", 5) } != "none") return "less"
    if (canvas.label("less or equal", 5) != "less or equal") return "less or equal"
    if (canvas.label("equal", 5) != "equal" || answer { canvas.label("equal", 4) } != "none") return "cmpEq"
    if ((0..4).map { answer { canvas.label("range", it) } } != listOf("none", "range", "range", "range", "none")) {
        return "range"
    }
    if ((1..3).map { answer { canvas.label("open range", it) } } != listOf("none", "open range", "none")) {
        return "open range"
    }

    every { canvas.scale(more(5)) } returns "more"
    if (canvas.scale(6) != "more") return "comparison of a subtype"
    if (answer { canvas.scale(6L) } != "none") return "comparison is not called for another subtype"
    if (answer { canvas.scale(4) } != "none") return "comparison of a subtype with a smaller value"
    return null
}

fun composition(): String? {
    val canvas = mock<Canvas>()
    every { canvas.label("and", and(more(1), less(5))) } returns "and"
    every { canvas.label("or", or(1, more(8))) } returns "or"
    every { canvas.label("not", not(eq(3))) } returns "not"
    every { canvas.label(or(isNull(), match { it.isEmpty() }), not(more(0))) } returns "nested"
    if (canvas.label("and", 3) != "and") return "and"
    if (answer { canvas.label("and", 1) } != "none" || answer { canvas.label("and", 5) } != "none") return "and of one"
    if (canvas.label("or", 1) != "or" || canvas.label("or", 9) != "or") return "or"
    if (answer { canvas.label("or", 5) } != "none") return "or of none"
    if (canvas.label("not", 4) != "not" || answer { canvas.label("not", 3) } != "none") return "not"
    if (canvas.label(null, 0) != "nested" || canvas.label("", -1) != "nested") return "nested composition"
    if (answer { canvas.label("a", 0) } != "none" || answer { canvas.label("", 1) } != "none") return "nested of none"
    return null
}

fun types(): String? {
    val canvas = mock<Canvas>()
    every { canvas.draw(any()) } returns "shape"
    every { canvas.draw(any<Square>()) } returns "square"
    every { canvas.draw(ofType<Circle>()) } returns "circle"
    if (canvas.draw(Circle(1)) != "circle" || canvas.draw(Square(1)) != "square") return "subtype"
    if (canvas.draw(object : Shape {}) != "shape") return "declared type"

    every { canvas.put(any<Int>()) } returns "int"
    every { canvas.put(any<String>()) } returns "string"
    every { canvas.put(anyNullable<Long?>()) } returns "long"
    if (canvas.put(1) != "int" || canvas.put("a") != "string" || canvas.put(1L) != "long") return "generic parameter"
    if (canvas.put<String?>(null) != "long") return "null passes a typed any"
    if (answer { canvas.put(1.5) } != "none") return "another type"

    every { canvas.put(ofType<Double>()) } returns "double"
    if (canvas.put(1.5) != "double") return "ofType"
    if (canvas.put<Double?>(null) != "long") return "null does not pass ofType"
    return classifiers()
}

fun classifiers(): String? {
    val canvas = mock<Canvas>()
    every { canvas.draw(any(Shape::class)) } returns "shape"
    every { canvas.draw(any(Square::class)) } returns "square"
    every { canvas.draw(ofType(Circle::class)) } returns "circle"
    if (canvas.draw(Circle(1)) != "circle" || canvas.draw(Square(1)) != "square") return "any and ofType of a class"
    if (canvas.draw(object : Shape {}) != "shape") return "any of the declared class"

    every { canvas.scale(match(object : Matcher<Int> { override fun match(arg: Int?): Boolean = arg == 2 }, Int::class)) } returns "two"
    if (canvas.scale(2) != "two") return "match with a class"
    if (answer { canvas.scale(2.0) } != "none" || answer { canvas.scale(3) } != "none") return "match of another value"
    return null
}

fun predicates(): String? {
    val canvas = mock<Canvas>()
    every { canvas.label(match { it.startsWith("x") }, 1) } returns "match"
    every { canvas.label(matchNullable { it == null || it.isEmpty() }, 2) } returns "nullable"
    if (canvas.label("xy", 1) != "match") return "match"
    if (answer { canvas.label("y", 1) } != "none" || answer { canvas.label(null, 1) } != "none") return "match of another"
    if (canvas.label(null, 2) != "nullable" || canvas.label("", 2) != "nullable") return "matchNullable"
    if (answer { canvas.label("a", 2) } != "none") return "matchNullable of another"

    every { canvas.draw(match<Circle> { it.radius > 1 }) } returns "big circle"
    if (canvas.draw(Circle(2)) != "big circle") return "match of a subtype"
    if (answer { canvas.draw(Circle(1)) } != "none") return "match of a subtype with another value"
    if (answer { canvas.draw(Square(2)) } != "none") return "match is not called for another subtype"
    return null
}

fun evaluation(): String? {
    val canvas = mock<Canvas>()
    val log = StringBuilder()
    fun <T> logged(value: T): T {
        log.append(value).append(';')
        return value
    }
    every { logged(canvas).label(eq(logged("a")), and(more(logged(1)), less(logged(9)))) } returns "logged"
    if (log.toString() != "${canvas};a;1;9;") return "evaluation order $log"
    if (canvas.label("a", 5) != "logged" || canvas.label("a", 6) != "logged") return "values of matchers"
    if (log.toString() != "${canvas};a;1;9;") return "matcher arguments are evaluated once"
    return null
}

fun box(): String {
    val failure = equality() ?: comparison() ?: composition() ?: types() ?: predicates() ?: evaluation()
    return if (failure == null) "OK" else "Fail: $failure"
}
