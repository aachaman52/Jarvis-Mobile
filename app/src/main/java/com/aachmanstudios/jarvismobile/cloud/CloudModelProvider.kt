package com.aachmanstudios.jarvismobile.cloud
interface CloudModelProvider { suspend fun generate(prompt:String):String }
/** No keys or provider configured by default. Inject an implementation in JarvisApplication. */
class CloudRegistry { var provider:CloudModelProvider?=null }
