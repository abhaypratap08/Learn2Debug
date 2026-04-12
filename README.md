# Learn2Debug

**A simple tool I built because I was tired of staring at Java errors and having no idea what was wrong.**

Live → **[https://learn2-debug.vercel.app](https://learn2-debug.vercel.app)**

---

### Why I made it

When I was learning Java, I kept running into the same problem:  
My code would break, but I didn’t know **where** or **why**.  
The error messages were confusing, and there was no friendly tool that could explain things simply.

So I created **Learn2Debug** out of curiosity — a place where you can paste your Java code and get clear, human explanations with fix suggestions.

---

### What it does

- Analyzes your Java code (full programs or LeetCode-style snippets)
- Shows you a score + what’s wrong
- Gives practical fix suggestions
- Links to proper Oracle documentation so you can actually learn

It’s made for beginners and students who just want to **understand** instead of guessing.

---

### Runtime note

The backend should run on **JDK 21**, not a JRE. Learn2Debug uses `javac` at runtime to surface real compiler errors like missing symbols, type mismatches, and syntax diagnostics.

---

### Optional AI explanations

Learn2Debug can optionally enrich compiler findings with Spring AI. The compiler and rule-based analyzer still stay the source of truth, and AI only adds a better explanation or likely logic hint.

To enable it locally:

```bash
export LEARN2DEBUG_AI_ENABLED=true
export SPRING_AI_MODEL_CHAT=openai
export OPENAI_API_KEY=your_key_here
export OPENAI_MODEL=gpt-4o-mini
```

Then run:

```bash
mvn spring-boot:run
```

If those variables are not set, the app falls back to deterministic analysis only.

---

### Try it now

**[Open Learn2Debug → https://learn2-debug.vercel.app](https://learn2-debug.vercel.app)**

No login. No signup. Just paste and learn.

---
Completely free and open source.
