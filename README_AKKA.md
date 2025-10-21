# 🔐 Accessing Akka Dependencies

This project uses **Akka** libraries hosted on Lightbend’s secure repository.  
To build or run the project, you must have a valid **Akka Secure Token**.

## 1. Get a Token

Visit [https://akka.io/token](https://akka.io/token) and request an access token.  
You may need to create a free Lightbend account to obtain one.

## 2. Configure the Token

Once you have the token, set it as an environment variable on your system:

### macOS / Linux

```bash
export AKKA_SECURE_TOKEN="your-token-here"
```

### Windows (PowerShell)

```powershell
setx AKKA_SECURE_TOKEN "your-token-here"
```

## 3. Verify Your Gradle Setup

Your `build.gradle.kts` (Kotlin DSL) should contain the following snippet:

```kotlin
repositories {
    maven("https://repo.akka.io/${System.getenv("AKKA_SECURE_TOKEN")}/secure")
    mavenCentral()
}
```

Gradle will automatically use the environment variable to authenticate with Akka’s repository.

## 4. Build the Project

After setting the token, you can build the project normally:

```bash
./gradlew build
```
