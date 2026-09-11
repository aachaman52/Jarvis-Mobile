#include <jni.h>
#include <atomic>
#include <algorithm>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <thread>
#include <vector>
#include "llama.h"

namespace {
std::atomic_bool stopped{false};
std::once_flag backend;
struct Session {
 llama_model* model=nullptr;
 llama_context* ctx=nullptr;
 ~Session(){if(ctx)llama_free(ctx);if(model)llama_model_free(model);}
};
void fail(JNIEnv* e,const std::string& text){if(!e->ExceptionCheck())e->ThrowNew(e->FindClass("java/lang/IllegalStateException"),text.c_str());}
std::string string(JNIEnv* e,jstring s){
 const char* p=e->GetStringUTFChars(s,nullptr);if(!p)throw std::runtime_error("String allocation failed");
 std::string out(p);e->ReleaseStringUTFChars(s,p);return out;
}
std::string bytes(JNIEnv* e,jbyteArray b){
 auto n=e->GetArrayLength(b);std::string out(n,'\0');e->GetByteArrayRegion(b,0,n,reinterpret_cast<jbyte*>(out.data()));return out;
}
jbyteArray array(JNIEnv* e,const std::string& s){
 auto a=e->NewByteArray(static_cast<jsize>(s.size()));if(a)e->SetByteArrayRegion(a,0,s.size(),reinterpret_cast<const jbyte*>(s.data()));return a;
}
bool abort_decode(void*){return stopped.load();}
std::vector<llama_token> tokenize(const llama_vocab* vocab,const std::string& prompt){
 int n=llama_tokenize(vocab,prompt.data(),prompt.size(),nullptr,0,true,true);
 if(n>=0)throw std::runtime_error("Empty prompt");
 std::vector<llama_token> tokens(-n);
 n=llama_tokenize(vocab,prompt.data(),prompt.size(),tokens.data(),tokens.size(),true,true);
 if(n<0)throw std::runtime_error("Tokenization failed");
 tokens.resize(n);return tokens;
}
std::string format(Session* s,const std::vector<std::string>& roles,const std::vector<std::string>& content){
 std::vector<llama_chat_message> messages;
 for(size_t i=0;i<roles.size();++i)messages.push_back({roles[i].c_str(),content[i].c_str()});
 const char* tmpl=llama_model_chat_template(s->model,nullptr);
 if(!tmpl)throw std::runtime_error("GGUF has no chat template. Use an instruction-tuned model with embedded template.");
 int n=llama_chat_apply_template(tmpl,messages.data(),messages.size(),true,nullptr,0);
 if(n<0)throw std::runtime_error("Unsupported model chat template");
 std::vector<char> buf(n+1);
 n=llama_chat_apply_template(tmpl,messages.data(),messages.size(),true,buf.data(),buf.size());
 if(n<0||n>static_cast<int>(buf.size()))throw std::runtime_error("Chat template failed");
 return std::string(buf.data(),n);
}
}
extern "C" JNIEXPORT jlong JNICALL Java_com_aachmanstudios_jarvismobile_core_model_NativeLlamaRuntime_nativeLoad(JNIEnv* e,jobject,jstring path,jint context){
 try {
 std::call_once(backend,[]{llama_backend_init();});
 auto s=std::make_unique<Session>();auto mp=llama_model_default_params();mp.use_mmap=true;mp.n_gpu_layers=0;
 s->model=llama_model_load_from_file(string(e,path).c_str(),mp);
 if(!s->model)throw std::runtime_error("GGUF could not load: invalid file, unsupported model, or insufficient memory");
 auto cp=llama_context_default_params();cp.n_ctx=std::clamp(context,1024,4096);cp.n_batch=128;cp.n_ubatch=128;
 cp.n_threads=std::min(4,std::max(1,static_cast<int>(std::thread::hardware_concurrency())-2));cp.n_threads_batch=cp.n_threads;
 cp.abort_callback=abort_decode;cp.abort_callback_data=nullptr;
 s->ctx=llama_init_from_model(s->model,cp);
 if(!s->ctx)throw std::runtime_error("Insufficient memory for model context");
 return reinterpret_cast<jlong>(s.release());
 }catch(const std::exception& ex){fail(e,ex.what());return 0;}
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_com_aachmanstudios_jarvismobile_core_model_NativeLlamaRuntime_nativeGenerate(JNIEnv* e,jobject,jlong h,jobjectArray jr,jobjectArray jc,jint max,jobject cb){
 try {
 auto* s=reinterpret_cast<Session*>(h);if(!s)throw std::runtime_error("No model");
 std::vector<std::string> roles,contents;
 const int count=e->GetArrayLength(jr);
 if(count<1||count!=e->GetArrayLength(jc)||count>10)throw std::runtime_error("Invalid conversation");
 for(int i=0;i<count;i++){
 auto r=static_cast<jstring>(e->GetObjectArrayElement(jr,i));auto c=static_cast<jbyteArray>(e->GetObjectArrayElement(jc,i));
 roles.push_back(string(e,r));contents.push_back(bytes(e,c));e->DeleteLocalRef(r);e->DeleteLocalRef(c);
 }
 auto* vocab=llama_model_get_vocab(s->model);
 auto tokens=tokenize(vocab,format(s,roles,contents));
 const int limit=static_cast<int>(llama_n_ctx(s->ctx))-std::clamp(max,64,512)-8;
 while(static_cast<int>(tokens.size())>limit&&roles.size()>2){
 roles.erase(roles.begin()+1);contents.erase(contents.begin()+1);
 if(roles.size()>2&&roles[1]=="assistant"){roles.erase(roles.begin()+1);contents.erase(contents.begin()+1);}
 tokens=tokenize(vocab,format(s,roles,contents));
 }
 if(static_cast<int>(tokens.size())>limit)throw std::runtime_error("Request exceeds context. Shorten it or increase context in Settings.");
 llama_kv_cache_clear(s->ctx);
 for(size_t off=0;off<tokens.size()&&!stopped;off+=128){
 auto b=llama_batch_get_one(tokens.data()+off,std::min<size_t>(128,tokens.size()-off));
 if(llama_decode(s->ctx,b)!=0){if(stopped)break;throw std::runtime_error("Prompt decode failed");}
 }
 if(stopped)return array(e,"");
 auto* sampler=llama_sampler_chain_init(llama_sampler_chain_default_params());
 std::unique_ptr<llama_sampler,decltype(&llama_sampler_free)> owned(sampler,llama_sampler_free);
 llama_sampler_chain_add(sampler,llama_sampler_init_top_k(40));
 llama_sampler_chain_add(sampler,llama_sampler_init_top_p(0.9f,1));
 llama_sampler_chain_add(sampler,llama_sampler_init_temp(0.7f));
 llama_sampler_chain_add(sampler,llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
 jclass cls=e->GetObjectClass(cb);jmethodID emit=e->GetMethodID(cls,"onBytes","([B)V");e->DeleteLocalRef(cls);
 if(!emit) return nullptr;
 std::string out;
 for(int i=0;i<std::clamp(max,64,512)&&!stopped;i++){
 auto token=llama_sampler_sample(sampler,s->ctx,-1);if(llama_vocab_is_eog(vocab,token))break;
 std::vector<char> piece(256);int n=llama_token_to_piece(vocab,token,piece.data(),piece.size(),0,false);
 if(n<0){piece.resize(-n);n=llama_token_to_piece(vocab,token,piece.data(),piece.size(),0,false);}
 if(n<0)throw std::runtime_error("Token conversion failed");
 out.append(piece.data(),n);
 auto a=array(e,out);if(!a)return nullptr;
 e->CallVoidMethod(cb,emit,a);e->DeleteLocalRef(a);if(e->ExceptionCheck())return nullptr;
 auto b=llama_batch_get_one(&token,1);
 if(llama_decode(s->ctx,b)!=0){if(stopped)break;throw std::runtime_error("Token decode failed");}
 }
 return array(e,out);
 }catch(const std::exception& ex){fail(e,ex.what());return nullptr;}
}
extern "C" JNIEXPORT void JNICALL Java_com_aachmanstudios_jarvismobile_core_model_NativeLlamaRuntime_nativeFree(JNIEnv*,jobject,jlong h){delete reinterpret_cast<Session*>(h);}
extern "C" JNIEXPORT void JNICALL Java_com_aachmanstudios_jarvismobile_core_model_NativeLlamaRuntime_nativeStop(JNIEnv*,jobject){stopped=true;}
extern "C" JNIEXPORT void JNICALL Java_com_aachmanstudios_jarvismobile_core_model_NativeLlamaRuntime_nativeResetStop(JNIEnv*,jobject){stopped=false;}
