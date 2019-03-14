# Spring-Cloud-Netflix Ribbon 

Simple project to show Ribbon in combination with Hystrix and Eureka and to understand Ribbon's retry behavior.

It also shows how to access Ribbon LoadBalancer, Ping and ServerList implementations.

The sample can be deployed to Cloud Foundry and implements a client (`address-service-client`) that uses `@LoadBalanced RestTemplate` to access the service `address-service`. This implementation sets a [CF header](https://docs.cloudfoundry.org/concepts/http-routing.html#app-instance-routing) that instructs CF's Go-Router to route to a specific service instance. This requires specifying the `application GUID` of `address-service` and the `instance index` of the application instance.

The service instance is determined by the Ribbon Loadbalancer and the `application GUID` and `instance index` are maintained in `address-service`'s Eureka metadata and retrieved through Ribbons `Server` instances (returned by the `ServerList`).

# The Components

* `address-service` - the service. Acts as a Eureka client. Can run locally and in Cloud Foundry. 
* `address-service-client` - the client. Acts as a Eureka client. Can run locally and in Cloud Foundry. When run in Cloud Foundry will set the `X-CF-APP-INSTANCE` header to "force" Go-Router to route the service instance returned by Ribbon.  
  It uses class `FailingAddressServiceClient` which calls a (always failing) endpoint of `address-service` using a loadbalanced RestTemplate. This will simulate instance failures and cause Ribbon to retry the request and then try another instance. 
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

This will start 3 instances of `address-service` that each register to Eureka server.
The `address-service-client` will retrieve the server list from Eureka via Ribbon and will set the routing header according to the Eureka service instance metadata returned in the server list.

Requests will fail for `address-service-client` (which is intended) and in the `cf logs` of `address-service` you will see retry 4 attempts by Ribbon (2 on the same server, 2 on the next).
All requests go to the same `address-service`-instance which you can see in the log output by the index number after `APP/PROCWEB/` (should be 0...2).
```
 2019-03-14T13:06:17.35+0100 [APP/PROC/WEB/0] OUT 	at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:624) [na:1.8.0_202]
```

# The Problem

The project shows how an application can get hold of Ribbon's `ILoadBalancer` and other beans like `IPing`, `ServerList<Server>`, etc. as mentioned [here](http://cloud.spring.io/spring-cloud-static/Edgware.SR5/multi/multi_spring-cloud-ribbon.html#_customizing_the_ribbon_client). 
It also shows how information retrieved from Eureka service instance metadata can be used to give explicit routing orders to Go-Router.  

However, since this happens on application level, and not on Ribbon / RestTemplate-internal level, Ribbon retries - as a result of failing service instances - will not get updated routing information headers, since the application is not involved in the retry.
