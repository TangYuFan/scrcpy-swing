# AGENTS.md - Agentic Coding Guidelines

## Project Overview

Java Swing desktop app for Android phone remote control (like Scrcpy). Uses Maven, Java 1.8+.

---

## Build Commands

```bash
# Compile & package
mvn compile
mvn package
mvn install
mvn clean package

# Run
mvn exec:java -Dexec.mainClass="com.tyf.demo.Main"

# Test
mvn test
mvn test -Dtest=TestClassName
mvn test -Dtest=TestClassName#testMethodName
mvn package -DskipTests
```

---

## Code Style

### Naming
- **Classes**: PascalCase (`MainPanel`, `LogService`)
- **Methods**: camelCase (`initMainPanel`)
- **Variables**: camelCase (`logFile`)
- **Constants**: UPPER_SNAKE_CASE (`MAIN_TITLE`)
- **Packages**: lowercase

### File Structure
- Source: `src/main/java/com/tyf/demo/`
- Packages: `gui`, `service`, `util`
- One public class per file

### Imports
1. Java stdlib (`java.*`)
2. Third-party (`javax.*`, `org.*`, `com.*`)
3. Project internal

### Formatting
- Indent: 4 spaces (no tabs)
- Max line: 120 chars
- K&R braces

---

## Error Handling

```java
// Try-with-resources (preferred)
try (InputStream in = ...; FileOutputStream out = ...) {
    // ops
} catch (IOException e) {
    e.printStackTrace();  // or Logger.error()
}

// Traditional
try { } catch (Exception e) { e.printStackTrace(); }
```

### Logging
- Use TinyLog: `org.pmw.tinylog.Logger`
- `Logger.info()`, `Logger.error()`, `Logger.debug()`

---

## Documentation

Chinese comments:
```java
/**
 *   @desc : 描述
 *   @auth : 作者
 *   @date : 2025-01-01 00:00:00
 */
```

---

## Project Structure

```
app-process/
├── pom.xml
├── src/main/java/com/tyf/demo/
│   ├── Main.java
│   ├── gui/        (MainPanel, InitPanel)
│   ├── service/    (ConstService, InitService, LogService)
│   └── util/       (CmdTools, DexTools, TimeTools)
└── mobile_demo/    (Android Gradle project)
```

---

## Dependencies

| Library | Ver | Purpose |
|---------|-----|---------|
| flatlaf | 3.2.5 | Swing UI |
| flatlaf-extras | 1.0 | Extra components |
| svgSalamander | 1.1.2.4 | SVG icons |
| tinylog | 1.3 | Logging |

---

## Main Entry
- Class: `com.tyf.demo.Main`
- Method: `main(String[] args)`
- Initializes FlatLaf, launches MainPanel

---

## Common Tasks

### Add Dependency (pom.xml)
```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>lib</artifactId>
    <version>1.0.0</version>
</dependency>
```

### New Service
1. Create in `src/main/java/com/tyf/demo/service/`
2. Add Chinese doc comment

### New GUI Panel
1. Extend JPanel/JFrame/JDialog
2. Add to `gui/` package
3. Follow FlatLaf patterns

## Additional requirements
1. 你在处理所有问题时，**全程思考过程必须使用中文**（包括需求分析、逻辑拆解、方案选择、步骤推导等所有内部推理环节）；
2. 最终输出的所有回答内容（包括文字解释、代码注释、步骤说明等）**必须全部使用中文**，仅代码语法本身的英文关键词除外。
3. 其中 QtScrcpy-dev、scrcpy-master 是参考项目。