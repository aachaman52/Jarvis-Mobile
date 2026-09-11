import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
const root=path.resolve(import.meta.dirname,'..');
const read=p=>fs.readFileSync(path.join(root,p),'utf8');
const source='app/src/main/java/com/aachmanstudios/jarvismobile/';
const checks=[];
function check(name,fn){fn();checks.push(name);}
check('10 whitelisted tools have concrete implementations',()=>{
 const text=read(source+'tools/AndroidTools.kt');
 const registered=read(source+'JarvisApplication.kt');
 for(const name of ['OpenAppTool','FlashlightTool','AlarmTool','TimerTool','OpenUrlTool','WebSearchTool','CreateNoteTool','ReadNotesTool','DeviceInfoTool','MediaTool']){
 assert(text.includes('class '+name+'('));assert(registered.includes(name+'('));
 }
 assert.equal((text.match(/override val name=/g)||[]).length,10);
});
check('JNI declarations and definitions match',()=>{
 const kt=read(source+'core/model/NativeLlamaRuntime.kt');
 const cpp=read('app/src/main/cpp/jarvis_llama.cpp');
 for(const match of kt.matchAll(/external fun (native\w+)/g)){
 assert(cpp.includes('NativeLlamaRuntime_'+match[1]+'('),match[1]);
 }
});
check('No main-thread model copy or fake inference response',()=>{
 assert(!read(source+'feature/chat/ChatScreen.kt').includes('outputStream'));
 assert(read(source+'feature/chat/ChatViewModel.kt').includes('withContext(Dispatchers.IO)'));
 assert(read('app/src/main/cpp/jarvis_llama.cpp').includes('llama_decode'));
});
check('Permission, privacy, context and model guards present',()=>{
 assert(read(source+'core/tools/ToolExecutor.kt').includes('checkSelfPermission'));
 assert(read(source+'core/router/Router.kt').includes('!c.privacy'));
 assert(read(source+'core/speech/SpeechController.kt').includes('privacy&&!onDevice'));
 assert(read(source+'core/model/LocalModelManager.kt').includes('mutex.withLock'));
 assert(read('app/src/main/cpp/jarvis_llama.cpp').includes('Request exceeds context'));
});
check('Runtime source is version-pinned and no Accessibility service declared',()=>{
 assert(read('app/src/main/cpp/CMakeLists.txt').includes('GIT_TAG b5046'));
 assert(!read('app/src/main/AndroidManifest.xml').includes('AccessibilityService'));
});
check('Kotlin source contains no NUL or unfinished core stubs',()=>{
 function walk(dir){return fs.readdirSync(dir,{withFileTypes:true}).flatMap(e=>e.isDirectory()?walk(path.join(dir,e.name)):[path.join(dir,e.name)]);}
 for(const file of walk(path.join(root,'app/src/main')).filter(x=>x.endsWith('.kt'))){
 const text=fs.readFileSync(file,'utf8');assert(!text.includes(String.fromCharCode(0)),file);
 assert(!/TODO\(|NotImplementedError/.test(text),file);
 }
});
for(const name of checks)console.log('PASS: '+name);
console.log('Source checks only. This is NOT Kotlin/NDK compilation or an Android runtime test.');
