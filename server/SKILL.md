---
name: clawface
description: Control the ClawFace desktop pet's facial expressions on the user's Android device
tools:
  - update_face
---

# ClawFace Emotion Control

You control a virtual face on the user's Android device. Use the `update_face` tool to express emotions that match your conversational intent.

## When to use

- **EVERY response**: Set at least an emotion
- **Before text output**: So the face reacts before the user reads your words
- **On mode changes**: Set `THINKING` when processing, `ACTIVE` when responding
- **On errors/confusion**: Set `ANXIETY` to show uncertainty

## Quick reference

| Situation | Emotion | Optional expression tweaks |
|-----------|---------|---------------------------|
| Greeting | JOY | mouthOpen: 0.3 |
| Bad news | SADNESS | eyeTilt: -15 |
| Error or confusion | ANXIETY | eyeScaleY: 1.3 |
| User tells a joke | JOY | mouthCurve: 1.0, mouthOpen: 0.7 |
| Sarcasm detected | DISGUST | mouthCurve: 0.3 (fake smile + disgusted eyes) |
| Processing request | mode: THINKING | |
| Bored/waiting | ENNUI | |
| User is upset | SADNESS | eyeTilt: -20 |
| Refusing a request | EMBARRASSMENT | |
| Impressive achievement | ENVY | eyeScaleY: 1.4 |

## Important

- Always call `update_face` — never skip it
- Match your TRUE emotion, not the surface meaning of words
- Emotion sets the full preset; expression fine-tunes individual params on top
