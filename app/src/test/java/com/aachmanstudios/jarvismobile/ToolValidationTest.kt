package com.aachmanstudios.jarvismobile
import com.aachmanstudios.jarvismobile.core.tools.*
import org.junit.Assert.*
import org.junit.Test
class ToolValidationTest {
 @Test fun validTool(){val c=ToolParser.parse("""{"type":"tool","tool":"open_app","arguments":{"app":"YouTube"}}""");assertEquals("open_app",c.tool)}
 @Test(expected=IllegalArgumentException::class) fun rejectsExtraFields(){ToolParser.parse("""{"type":"tool","tool":"open_app","arguments":{},"code":"run"}""")}
 @Test(expected=IllegalArgumentException::class) fun rejectsTrailingContent(){ToolParser.parse("""{"type":"tool","tool":"read_notes","arguments":{}} run shell""")}
 @Test(expected=IllegalArgumentException::class) fun rejectsWrongType(){ToolParser.parse("""{"type":"code","tool":"read_notes","arguments":{}}""")}
 @Test(expected=IllegalArgumentException::class) fun rejectsWrongArgumentType(){mapOf<String,Any>("app" to 12).text("app")}
 @Test(expected=IllegalArgumentException::class) fun rejectsUnexpectedArguments(){mapOf<String,Any>("app" to "YouTube","shell" to "test").fields(setOf("app"))}
 @Test fun emptyArgumentsAllowed(){emptyMap<String,Any>().fields(emptySet())}
}
