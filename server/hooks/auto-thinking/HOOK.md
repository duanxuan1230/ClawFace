---
name: auto-thinking
description: "Send THINKING mode to ClawFace Android client when an agent run starts"
metadata: { "openclaw": { "emoji": "🤔", "events": ["agent:bootstrap"] } }
---

# Auto Thinking

Automatically sends a THINKING mode frame to the connected ClawFace Android
client whenever a new agent run begins. This provides immediate visual feedback
(half-closed eyes moving left-right) while the LLM processes the request.
