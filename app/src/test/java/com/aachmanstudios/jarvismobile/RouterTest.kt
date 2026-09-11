package com.aachmanstudios.jarvismobile
import com.aachmanstudios.jarvismobile.core.router.*
import org.junit.Assert.*
import org.junit.Test
class RouterTest {
 private val router=Router()
 private val normal=RoutingContext(3000,70,0,true,true,false,false,true)
 @Test fun directToolsWorkWithoutModel(){
 val c=normal.copy(localReady=false,availableRamMb=100,battery=3,thermal=4)
 assertEquals(Path.TOOL,router.route("Open YouTube",c).path)
 assertEquals("YouTube",router.direct("Open YouTube")!!.arguments["app"])
 }
 @Test fun timerUsesSeconds(){assertEquals(600L,router.direct("Set a timer for 10 minutes")!!.arguments["duration"])}
 @Test fun flashlightBoolean(){assertEquals(true,router.direct("Turn on flashlight")!!.arguments["on"])}
 @Test fun remembersExactContent(){assertEquals("I need to revise physics",router.direct("Remember that I need to revise physics")!!.arguments["text"])}
 @Test fun retrievesNotes(){assertEquals("read_notes",router.direct("What did I ask you to remember?")!!.tool)}
 @Test fun alarmsNormalizeAmPm(){
 assertEquals("00:30",router.direct("Set an alarm for 12:30 am")!!.arguments["time"])
 assertEquals("19:00",router.direct("Set alarm at 7 pm")!!.arguments["time"])
 }
 @Test fun explanationRemainsLocal(){assertEquals(Path.LOCAL,router.route("Explain nuclear fusion",normal).path)}
 @Test fun privacyBlocksCloud(){assertEquals(Path.LOCAL,router.route("Solve complex programming",normal.copy(cloudEnabled=true,cloudConfigured=true)).path)}
 @Test fun cloudOnlyOfferedWithConnectivityAndConsentSetting(){
 val c=normal.copy(privacy=false,cloudEnabled=true,cloudConfigured=true)
 assertEquals(Path.CLOUD_OFFER,router.route("Complex programming",c).path)
 assertEquals(Path.LOCAL,router.route("Complex programming",c.copy(online=false)).path)
 }
 @Test fun hotDeviceBlocksInference(){assertEquals(Path.UNAVAILABLE,router.route("Explain fusion",normal.copy(thermal=3)).path)}
 @Test fun quotedCommandsAreNotDirect(){assertNull(router.direct("Explain what open YouTube means"))}
}
