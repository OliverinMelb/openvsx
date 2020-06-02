The server application is available as [Docker image](https://github.com/eclipse/openvsx/packages/128014). It is a [Spring Boot](https://spring.io/projects/spring-boot) application and needs an `application.yml` file in the directory `/home/openvsx/server/config` to configure the deployment. You can add such a file by extending the image or with a Kubernetes ConfigMap.

**TODO: Document application.yml properties**

**TODO: How to configure and link the web UI**