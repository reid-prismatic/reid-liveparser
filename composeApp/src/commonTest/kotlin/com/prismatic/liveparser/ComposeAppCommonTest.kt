package com.prismatic.liveparser

import com.prismatic.liveparser.weave.properties.PropertyLineParseResult
import com.prismatic.liveparser.weave.properties.StringParseErrorType
import com.prismatic.liveparser.weave.properties.parsePropertyLine
import kotlin.test.Test
import kotlin.test.assertEquals

class ComposeAppCommonTest {

    @Test
    fun example() {
        assertEquals(3, 1 + 2)
    }

    @Test
    fun propertyLineParser() {

        fun checkLine(
            s: String,
            errorType: StringParseErrorType? = null,
            success: Boolean = errorType==null,
            errorOffset: Int = -1,
            style: String = "",
            name: String = "",
            value: String = "",
            condition: String = ""
        ) {
            val expected = PropertyLineParseResult(success, errorOffset, errorType, style, name, value, condition)
            val actual = parsePropertyLine(s)
            assertEquals(expected, actual)
        }

        checkLine("- one: two", name="one", value="two")
        checkLine("- one(a < b): two", name="one", value="two", condition = "a < b")
        checkLine("- (a < b): two", errorType = StringParseErrorType.ExpectedPropertyName, errorOffset = 2)
        checkLine("stuff", errorType = StringParseErrorType.UnexpectedChar('-','s'), errorOffset = 0)
        checkLine("- {foo}", style="foo")
        checkLine("- one(a < 3:5): two", name="one", value="two", condition = "a < 3:5")
        checkLine("-one(a < 3:5) : two", name="one", value="two", condition = "a < 3:5")


//        assertEquals( PropertyLineParseResult(name="one", value="two"), parsePropertyLine("- one: two"))

    }
}