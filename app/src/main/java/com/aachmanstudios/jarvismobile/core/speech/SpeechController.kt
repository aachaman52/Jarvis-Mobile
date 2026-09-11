package com.aachmanstudios.jarvismobile.core.speech
import android.content.*
import android.annotation.SuppressLint
import android.os.*
import android.speech.*
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Android speech and TTS APIs require the main thread. No always-listening service. */
class SpeechController(private val context:Context,private val text:(String)->Unit,private val status:(String)->Unit):RecognitionListener {
 private var recognizer:SpeechRecognizer?=null
 private var tts:TextToSpeech?=null
 private var ready=false
 init {tts=TextToSpeech(context){result->ready=result==TextToSpeech.SUCCESS; if(ready)tts?.language=Locale.getDefault()}}
 // Activity permission launcher gates this call. Denials/revocations are caught below.
 @SuppressLint("MissingPermission")
 fun listen(privacy:Boolean) {
 stopListening();tts?.stop()
 try {
 val onDevice=Build.VERSION.SDK_INT>=31&&SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
 if(privacy&&!onDevice){status("On-device recognition unavailable. Type instead, or disable privacy mode to allow the system recognizer.");return}
 if(!onDevice&&!SpeechRecognizer.isRecognitionAvailable(context)){status("No speech recognizer installed.");return}
 recognizer=if(onDevice&&Build.VERSION.SDK_INT>=31)SpeechRecognizer.createOnDeviceSpeechRecognizer(context) else SpeechRecognizer.createSpeechRecognizer(context)
 recognizer?.setRecognitionListener(this)
 recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
 .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
 .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true)
 .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false))
 status("Listening…")
 }catch(e:Exception){status(e.message?:"Microphone unavailable");stopListening()}
 }
 fun stopListening(){recognizer?.cancel();recognizer?.destroy();recognizer=null}
 fun speak(value:String,privacy:Boolean){
 if(!ready){status("TTS engine is not ready.");return}
 if(privacy){
 val voice=tts?.voices?.firstOrNull{!it.isNetworkConnectionRequired&&it.locale.language==Locale.getDefault().language}
 if(voice==null){status("No offline TTS voice installed.");return}
 tts?.voice=voice
 }
 tts?.speak(value.take(TextToSpeech.getMaxSpeechInputLength()),TextToSpeech.QUEUE_FLUSH,null,"jarvis")
 }
 fun stopAll(){stopListening();tts?.stop()}
 fun close(){stopAll();tts?.shutdown();tts=null}
 override fun onResults(results:Bundle?){status("");results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(text)}
 override fun onError(error:Int){status("Speech recognition failed ($error). Try again or type.");}
 override fun onReadyForSpeech(params:Bundle?){}
 override fun onBeginningOfSpeech(){}
 override fun onRmsChanged(rmsdB:Float){}
 override fun onBufferReceived(buffer:ByteArray?){}
 override fun onEndOfSpeech(){status("Recognizing…")}
 override fun onPartialResults(partialResults:Bundle?){}
 override fun onEvent(eventType:Int,params:Bundle?){}
}
