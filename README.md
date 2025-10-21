# Learn2Debug

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**AI-powered Java Code Debugger for Learners**

Learn2Debug is an open-source tool designed to help Java learners and developers **improve their coding skills**. Unlike traditional debuggers, it **does not modify your code**. Instead, it analyzes your Java programs, points out potential bugs, suggests improvements, and provides **educational tips** to help you understand the reasoning behind each suggestion.

It combines **static code analysis** with **AI-generated explanations**, making learning and debugging interactive and insightful.

---

## Features (v1)

* Analyze Java code for **potential runtime errors** (e.g., division by zero, null pointer risks)
* Detect **unused variables** and **dead code**
* Provide **AI-generated hints** and **learning tips** for each detected issue
* **Non-destructive**: Original code is never modified
* Command-Line Interface (CLI) with **color-coded output**

  * **Red:** Critical errors
  * **Yellow:** Warnings
  * **Blue/Green:** Learning tips
* Configurable verbosity: Beginner / Intermediate / Advanced

---

## Tech Stack

* **Language:** Java
* **Static Analysis:** JavaParser, Checkstyle, SpotBugs
* **AI Module:** OpenAI API (or local LLM)
* **Build System:** Maven or Gradle

---

## Usage

### CLI Example

```bash
# Analyze a Java file
java -jar Learn2Debug.jar MyProgram.java --level beginner
```

### Example Output

```
[Error] Division by zero at line 12
[Tip] Avoid dividing by zero. Check the denominator before performing division.

[Warning] Unused variable 'temp' at line 5
[Tip] Remove unused variables to keep code clean and maintainable.
```

---

## Getting Started

1. **Clone the repository:**

```bash
git clone https://github.com/yourusername/Learn2Debug.git
cd Learn2Debug
```

2. **Build the project** with Maven or Gradle.
3. **Set up your OpenAI API key** (for AI explanations).
4. **Run the CLI** to analyze Java files.

---

## Contribution

Contributions are welcome! You can help by:

* Adding **new static analysis rules**
* Improving **AI explanations and learning tips**
* Enhancing **CLI experience**

Please submit a **pull request** with a clear description of your changes.

---

## License

This project is licensed under the **MIT License** – see the [LICENSE](LICENSE) file for details.

---

## Roadmap (Future Versions)

* IDE plugin support (VSCode / IntelliJ)
* Web-based interface with interactive hints
* Advanced AI-assisted refactoring suggestions
* Gamification / learning score system
