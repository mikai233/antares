# antares

# distributed game server based on akka

## development environment

- JDK17(azul-17)
- Gradle 8
- Zookeeper

## basic demo

1. run  ```NodeConfigInit.kt``` and ```GameWorldConfigInit.kt``` init zookeeper
2. run ``` gradlew bootJar ``` to build fatjar
3. run ```java -jar stardust-1.0.0.jar``` to start the all-in-one server

## TODO