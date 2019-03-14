# Spring-Cloud-Netflix Ribbon 

Simple project to show Ribbon in combination with Hystrix and Eureka and to understand Ribbon's retry behavior.

It also shows how to access Ribbon LoadBalancer, Ping and ServerList implementations.

The sample can be deployed to Cloud Foundry and implements a client (`address-service-client`) that uses `@LoadBalanced RestTemplate` to access the service `address-service`. This implementation sets a [CF header](https://docs.cloudfoundry.org/concepts/http-routing.html#app-instance-routing) that instructs CF's Go-Router to route to a specific service instance. This requires specifying the `application GUID` of `address-service` and the `instance index` of the application instance.

The service instance is determined by the Ribbon Loadbalancer and the `application GUID` and `instance index` are maintained in `address-service`'s Eureka metadata and retrieved through Ribbons `Server` instances (returned by the `ServerList`).

# The Components

* `address-service` - the service. Acts as a Eureka client. Can run locally and in Cloud Foundry. 
* `address-service-client` - the client. Acts as a Eureka client. Can run locally and in Cloud Foundry. When run in Cloud Foundry will set the `X-CF-APP-INSTANCE` header to "force" Go-Router to route the service instance returned by Ribbon.
* `eureka-service` - the Eureka registry.

# Running Locally

* In the root folder executed `mvn clean package`
* Start Eureka using `java -jar ./eureka-service/target/eureka-service-snapshot-0.0.1.jar`
* Start `address-service` using `java -jar ./address-service/target/address-service-snapshot-0.0.1.jar`
* Start `address-service-client` using `java -jar ./address-service-client/target/address-service-client-snapshot-0.0.1.jar`

# Running in Cloud Foundry

* Adjust routes in root folder's `manifest.yml`
* In the root folder executed `mvn clean package`
* Execute `cf push`

