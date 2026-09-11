# Phone acceptance checklist

All items below are pending until executed on a physical arm64 phone. Use the included debug screen; no performance numbers are invented.

## Build gate

- [ ] Compile debug APK, JVM tests and lint succeed.
- [ ] Install on the intended Android 13 phone; launch without a model.
- [ ] No native crash, missing library or unexpected permission prompt at launch.

## Direct tools without a model

- [ ] Open YouTube → correct app opens.
- [ ] Unknown app → error; ambiguous label → package clarification.
- [ ] Turn on flashlight → permission requested; allow → torch on.
- [ ] Deny camera → no crash; type remains usable.
- [ ] Turn off flashlight → torch off.
- [ ] Set a timer for 10 minutes → confirm clock actually starts it.
- [ ] Set an alarm for 7:30 am → clock receives 07:30.
- [ ] Remember that I need to revise physics → local note saved.
- [ ] What did I ask you to remember? → same note shown.
- [ ] Restart app → note and conversation remain.
- [ ] Open https://example.com → browser opens.
- [ ] Search for nuclear fusion → browser receives encoded query.
- [ ] Device info → RAM/battery/storage display plausibly.
- [ ] Play/pause/next/previous → test with an active music player; ignored requests are possible.

## Model

- [ ] Valid 2–3B Q4 GGUF imports without freezing UI.
- [ ] Wrong file, truncated file, unavailable provider, full storage → readable error.
- [ ] Load → Ready; Explain how nuclear fusion works → real offline answer.
- [ ] Airplane mode → model and notes still work.
- [ ] Rapid repeated Send → only one response/generation.
- [ ] Stop during prompt processing and token output → stops, next request works.
- [ ] Very long request → bounded context or readable rejection, no overrun.
- [ ] Load/unload 10 times → no monotonic native memory leak.
- [ ] Replace model → old session released, new model used.
- [ ] Rotate during inference → no duplicate session; leave/reopen → stable.
- [ ] Unicode/Hinglish output → final text not corrupted.
- [ ] Record time to first token, total time, tokens/sec and peak RAM.
- [ ] 20 sequential prompts → no persistent context growth.
- [ ] Heat/low battery/low RAM → inference guard; direct tools remain available.

## Validation and privacy

- [ ] Unknown tool, extra fields, incorrect argument types, invalid alarm or oversized timer → no execution.
- [ ] javascript:, intent:, file: URLs → rejected.
- [ ] Model tool JSON → confirmation first; cancel → no execution.
- [ ] Camera permission revoked → guarded error or permission request.
- [ ] Privacy on → no cloud call.
- [ ] Cloud enabled with no provider → no attempted provider call.
- [ ] With an injected test provider, approve/deny → call happens only on approval.
- [ ] Mic denial → typing still works.
- [ ] Privacy on without on-device recognizer → refuses voice, not silent network fallback.
- [ ] Offline TTS voice present/missing → speaks or explains failure.
- [ ] Leaving activity → speech/TTS stop; no always-listening behavior.
