# Blackjack – Boilerplate Project
**Ingegneria e Sviluppo Software 1**  
Java 21 · Maven Multi-Module · JavaFX  

---

## Overview

This project is a minimal boilerplate for a multi-module Java application using:

- Java 21  
- Maven  
- JavaFX  
- Frontend / Backend separation
- Fat JAR packaging  

The current implementation demonstrates:

- A simple JavaFX UI  
- A TextArea  
- A button that saves the content of the TextArea to a file  
- Proper separation between frontend and backend modules  

This boilerplate serves as the starting point for the Blackjack course project.

---

## Project Structure

```
blackjack/
│
├── pom.xml               (parent / aggregator)
├── backend/              (business logic & services)
└── frontend/             (JavaFX UI)
```

### Backend Module

Contains:
- Application services
- File persistence service (`FileService`)
- Future Blackjack domain logic

The backend does not contain UI code.

### Frontend Module

Contains:
- JavaFX UI
- FXML layout
- Controllers

The frontend depends on the backend as a Maven dependency.

---

## Requirements

- Java 21  
- Maven 3.9+  

Verify installation:

```bash
java -version
mvn -version
```

---

## Build the Project

From the root directory:

```bash
mvn clean package
```

This will:

- Build the backend  
- Build the frontend  
- Produce a fat JAR inside:

```
frontend/target/frontend-1.0.0-SNAPSHOT-all.jar
```

---

## Run the Application

### Option 1 – Run the Fat JAR

```bash
java -jar frontend/target/frontend-1.0.0-SNAPSHOT-all.jar
```

### Option 2 – Run in Development Mode (Recommended)

```bash
mvn -pl frontend javafx:run
```

This ensures JavaFX modules are correctly loaded.

---

## Running from IntelliJ IDEA

1. Open the root folder (`blackjack/`)
2. Ensure Project SDK = Java 21
3. Reload the Maven project

To run the application:

- Open the Maven tool window  
- Execute:

```
frontend → Plugins → javafx → javafx:run
```

Avoid running `MainApp` directly unless JavaFX is properly configured.

---

## File Saving Example

The boilerplate includes a simple persistence example.

Backend service:

```java
public void saveUtf8(Path file, String content)
```

Frontend usage:

```java
fileService.saveUtf8(
    Path.of("saved", "textarea.txt"),
    textArea.getText()
);
```

By default, the file will be created in:

```
saved/textarea.txt
```

(relative to the application working directory)

---

## Educational Purpose

This boilerplate is intentionally minimal and designed to:

- Demonstrate proper separation of frontend and backend
- Provide a working JavaFX + Maven structure
- Offer a starting point for the Blackjack project
- Serve as a base for adding:
  - Game logic
  - Serialization
  - License validation in C
  - Internationalization
  - Unit testing

---

Author: Edoardo Terzi
