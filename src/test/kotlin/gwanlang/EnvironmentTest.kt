package gwanlang

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EnvironmentTest {

    private fun token(name: String) = Token(TokenType.IDENTIFIER, name, null, 1)

    @Test
    fun `define 후 get으로 값을 조회할 수 있다`() {
        val env = Environment()
        env.define("x", 42.0)

        assertEquals(42.0, env.get(token("x")))
    }

    @Test
    fun `여러 변수를 정의하고 각각 조회할 수 있다`() {
        val env = Environment()
        env.define("x", 1.0)
        env.define("y", "hello")
        env.define("z", true)

        assertEquals(1.0, env.get(token("x")))
        assertEquals("hello", env.get(token("y")))
        assertEquals(true, env.get(token("z")))
    }

    @Test
    fun `미정의 변수를 get하면 RuntimeError가 발생한다`() {
        val env = Environment()

        val error = assertThrows<RuntimeError> { env.get(token("x")) }
        assertEquals("Undefined variable 'x'.", error.message)
    }

    @Test
    fun `nil 값으로 변수를 정의할 수 있다`() {
        val env = Environment()
        env.define("x", null)

        assertNull(env.get(token("x")))
    }

    @Test
    fun `같은 이름으로 재선언하면 값이 덮어써진다`() {
        val env = Environment()
        env.define("x", 1.0)
        env.define("x", 2.0)

        assertEquals(2.0, env.get(token("x")))
    }

    // --- 스코프 체인 ---

    @Test
    fun `자식 스코프에서 부모 스코프의 변수를 조회할 수 있다`() {
        val parent = Environment()
        parent.define("x", 10.0)
        val child = Environment(parent)

        assertEquals(10.0, child.get(token("x")))
    }

    @Test
    fun `자식 스코프에서 같은 이름의 변수를 섀도잉할 수 있다`() {
        val parent = Environment()
        parent.define("x", 10.0)
        val child = Environment(parent)
        child.define("x", 20.0)

        assertEquals(20.0, child.get(token("x")))
        assertEquals(10.0, parent.get(token("x")))
    }

    @Test
    fun `자식 스코프의 변수는 부모에서 접근할 수 없다`() {
        val parent = Environment()
        val child = Environment(parent)
        child.define("x", 10.0)

        assertThrows<RuntimeError> { parent.get(token("x")) }
    }

    // --- 대입 ---

    @Test
    fun `assign으로 기존 변수의 값을 변경할 수 있다`() {
        val env = Environment()
        env.define("x", 1.0)
        env.assign(token("x"), 2.0)

        assertEquals(2.0, env.get(token("x")))
    }

    @Test
    fun `미정의 변수에 assign하면 RuntimeError가 발생한다`() {
        val env = Environment()

        val error = assertThrows<RuntimeError> { env.assign(token("x"), 1.0) }
        assertEquals("Undefined variable 'x'.", error.message)
    }

    @Test
    fun `자식 스코프에서 부모 스코프의 변수에 assign할 수 있다`() {
        val parent = Environment()
        parent.define("x", 1.0)
        val child = Environment(parent)
        child.assign(token("x"), 99.0)

        assertEquals(99.0, parent.get(token("x")))
        assertEquals(99.0, child.get(token("x")))
    }

    // --- 거리 기반 조회/대입 ---

    @Test
    fun `getAt 거리 0은 현재 환경에서 조회한다`() {
        val env = Environment()
        env.define("x", 42.0)

        assertEquals(42.0, env.getAt(0, "x"))
    }

    @Test
    fun `getAt 거리 1은 부모 환경에서 조회한다`() {
        val parent = Environment()
        parent.define("x", 10.0)
        val child = Environment(parent)
        child.define("y", 20.0)

        assertEquals(10.0, child.getAt(1, "x"))
    }

    @Test
    fun `getAt 거리 2는 조부모 환경에서 조회한다`() {
        val grandparent = Environment()
        grandparent.define("x", 1.0)
        val parent = Environment(grandparent)
        val child = Environment(parent)

        assertEquals(1.0, child.getAt(2, "x"))
    }

    @Test
    fun `assignAt 거리 0은 현재 환경에 대입한다`() {
        val env = Environment()
        env.define("x", 1.0)
        env.assignAt(0, token("x"), 99.0)

        assertEquals(99.0, env.get(token("x")))
    }

    @Test
    fun `assignAt 거리 1은 부모 환경에 대입한다`() {
        val parent = Environment()
        parent.define("x", 1.0)
        val child = Environment(parent)

        child.assignAt(1, token("x"), 99.0)

        assertEquals(99.0, parent.get(token("x")))
    }
}
