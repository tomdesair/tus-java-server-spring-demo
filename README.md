# tus-java-server-spring-demo
Tus java server demo using Spring Boot that uses the [tus-java-server library](https://github.com/tomdesair/tus-java-server/) and the [Uppy file uploader](https://uppy.io/).

To build and run this demo, execute the following commands:

```
$ git clone https://github.com/tomdesair/tus-java-server.git
$ cd tus-java-server
$ mvn clean install
$ cd ..
$ git clone https://github.com/tomdesair/tus-java-server-spring-demo.git
$ cd tus-java-server-spring-demo
$ mvn clean package
$ java -jar spring-boot-rest/target/spring-boot-rest-0.0.1-SNAPSHOT.jar
```

Then visit http://localhost:8080/ in your browser and try to upload a file using the Uppy file uploader.
