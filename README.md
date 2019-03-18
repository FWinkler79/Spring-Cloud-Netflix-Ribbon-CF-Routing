# Spring-Cloud-Netflix Ribbon 

Simple project to show Ribbon in combination with Hystrix and Eureka and to understand Ribbon's retry behavior.  
It also shows how to access Ribbon LoadBalancer, Ping and ServerList implementations.

The project contains a custom Spring Cloud `LoadBalancerRequestTransformer` implementation that makes it possible to 
deploy this project to Cloud Foundry and have Ribbon "overrule" CF's Go-Router, essentially taking over load balancing from Go-Router.

# The Components

* `address-service` - a CF application that provides an address service. Acts as a Eureka client. Can run locally and in Cloud Foundry.  
  The service provides two endpoints `/address` (which randomly introduces delays and failures) and `/failing-address` (which will always fail). These are used to test Hystrix and Ribbon retry behaviour.

* `address-service-client` - a CF application acting as the service client. Acts as a Eureka client. Can run locally and in Cloud Foundry.  
  Provides a custom implementation of `LoadBalancerRequestTransformer` which, when run in Cloud Foundry, will set the `X-CF-APP-INSTANCE` header to "force" Go-Router to route Ribbon's requests to the service instance selected by Ribbon for load balancing.  
  `address-service-client` uses class `FailingAddressServiceClient` that calls the `/failing-address` endpoint of `address-service` using a loadbalanced RestTemplate. This will simulate instance failures and cause Ribbon to retry the request and then try another instance. 
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

Requests will fail for `address-service-client` (which is intended) and in the `cf logs` of `address-service` you will see 4 retry attempts by Ribbon (2 on the same server, 2 on the next).
The requests will go to different `address-service`-instances (the ones that Ribbon has selected for load balancing) which you can see in the log output by the index number after `APP/PROCWEB/` (should be between 0 and 2).
```
 2019-03-14T13:06:17.35+0100 [APP/PROC/WEB/0] OUT 	at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:624) [na:1.8.0_202]
```

# How Things Work

The most interesting parts of this project are happing in `address-service-client`. It provides two different use cases in the following packages 

* `com.fonz.cloud.address.service.client.cf` - Shows how Ribbon can be integrated with Cloud Foundry routing and load balancing
* `com.fonz.cloud.address.service.client.ribboninject` - Shows how Ribbon can be customized with custom implementations of the Ribbon extension interfaces.

In the following these will be explained further.

## Ribbon Cloud Foundry Routing Test

**Problem Statement:**  Ribbon is a client-side load balancer - i.e. it reads a list of servers from a source, e.g. a service registry like Eureka, and selects service instances (based on customizable rules) that it sends outgoing HTTP requests to. When deployed to Cloud Foundry, this conflicts with CF's own load balancing mechanism implemented by Go-Router:  
In CF all service instances share the same `route` configured for the service itself (i.e. the Cloud Foundry application implementing the service).
When Ribbon selects a service instance from Eureka, it retrieves the URL (which is the same for all instances) and sends a request. The request traverses CF's Go-Router which picks a service instance of its own choice to send the request to (effectively applying its own load balancing rules and rendering Ribbon useless).

**Solution:** There are different ways to overcome the problem above. Usually an approach is chosen, where direct container communication is enabled in CF, so that requests between containers can send requests to each other without traversing CF's Go-Router (as explained [here](https://docs.cloudfoundry.org/concepts/understand-cf-networking.html)).  
In this project, however, a different approach is chosen: in CF it is possible to instruct Go-Router which exact service instance to route a particular request to.  
This is usually used for debugging purposes, to make sure that a given request ends up at the exact service instance that was specified.  
This mechanism uses a custom CF HTTP header that Go-Router inspects if present and will route the request as instructed. This is described [here](https://docs.cloudfoundry.org/concepts/http-routing.html#app-instance-routing). We are using this mechanism to tell Go-Router to always select the service instance that Ribbon has pre-determined for load balancing. To get this working, a `LoadBalancerRequestTransformer` needs to be supplied, which acts as an interceptor for load balancer requests and injects the CF routing header that will be interpreted by Go-Router.

### The `LoadBalancerRequestTransformer` Implementation

The implementation of `LoadBalancerRequestTransformer` is available in `CFLoadBalancerRequestTransformer`:

```java 
public class CFLoadBalancerRequestTransformer implements LoadBalancerRequestTransformer {
    public static final String CF_APP_GUID = "cfAppGuid";
    public static final String CF_INSTANCE_INDEX = "cfInstanceIndex";
    public static final String ROUTING_HEADER = "X-CF-APP-INSTANCE";

    @Override
    public HttpRequest transformRequest(HttpRequest request, ServiceInstance instance) {
        
        System.out.println("Transforming Request from LoadBalancer Ribbon).");
 
        // First: Get the service instance information from the lower Ribbon layer.
        //        This will include the actual service instance information as returned by Eureka. 
        RibbonLoadBalancerClient.RibbonServer serviceInstanceFromRibbonLoadBalancer = (RibbonLoadBalancerClient.RibbonServer) instance;
        
        // Second: Get the the service instance from Eureka, which is encapsulated inside the Ribbon service instance wrapper.
        DiscoveryEnabledServer serviceInstanceFromEurekaClient = (DiscoveryEnabledServer) serviceInstanceFromRibbonLoadBalancer.getServer();
        
        // Finally: Get access to all the cool information that Eureka provides about the service instance (including metadata and much more).
        //          All of this is available for transforming the request now, if necessary.
        InstanceInfo instanceInfo = serviceInstanceFromEurekaClient.getInstanceInfo();
        
        // If it's only the instance metadata you are interested in, you can also get it without explicitly down-casting as shown above.  
        Map<String, String> metadata = instance.getMetadata();
        System.out.println("Instance: " + instance);
    
        dumpServiceInstanceInformation(metadata, instanceInfo);
        
        if (metadata.containsKey(CF_APP_GUID) && metadata.containsKey(CF_INSTANCE_INDEX)) {
            final String headerValue = String.format("%s:%s", metadata.get(CF_APP_GUID), metadata.get(CF_INSTANCE_INDEX));
            
            System.out.println("Returning Request with Special Routing Header");
            System.out.println("Header Value: " + headerValue);
            
            // request.getHeaders might be immutable, so we return a wrapper that pretends to be the original request.
            // and that injects an extra header.
            return new CFLoadBalancerHttpRequestWrapper(request, headerValue);
        }
        
        return request;
    }
    
    /**
     * Dumps metadata and InstanceInfo as JSON objects on the console.
     * @param metadata the metadata (directly) retrieved from 'ServiceInstance'
     * @param instanceInfo the instance info received from the (downcast) 'DiscoveryEnabledServer' 
     */
    private void dumpServiceInstanceInformation(Map<String, String> metadata, InstanceInfo instanceInfo) {
        ObjectMapper mapper = new ObjectMapper();
        String json;
        try {
            json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(metadata);
            System.err.println("-- Metadata: " );
            System.err.println(json);
            
            json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(instanceInfo);
            System.err.println("-- InstanceInfo: " );
            System.err.println(json);
        } catch (JsonProcessingException e) {
            System.err.println(e);
        }
    }
    
    /**
     * Wrapper class for an HttpRequest which may only return an
     * immutable list of headers. The wrapper immitates the original 
     * request and will return the original headers including a custom one
     * added when getHeaders() is called. 
     */
    private class CFLoadBalancerHttpRequestWrapper implements HttpRequest {

        private HttpRequest request;
        private String headerValue;
        
        CFLoadBalancerHttpRequestWrapper(HttpRequest request, String headerValue) {
            this.request = request;
            this.headerValue = headerValue;
        }
        
        @Override
        public HttpHeaders getHeaders() {
            HttpHeaders headers = new HttpHeaders();
            headers.putAll(request.getHeaders());
            headers.add(ROUTING_HEADER, headerValue);
            return headers;
        }

        @Override
        public String getMethodValue() {
            return request.getMethodValue();
        }

        @Override
        public URI getURI() {
            return request.getURI();
        }
    }  
}
```

The magic happens in the `transformRequest(HttpRequest request, ServiceInstance instance)` method.  
Here we use the `ServiceInstance` which is an instance selected by the underlying Ribbon implementation. `ServiceInstance` is a Spring Cloud interface.
When using Ribbon as the load balancer implementation, `ServiceInstance` objects are implemented by sub classes `RibbonLoadBalancerClient.RibbonServer`.
In combination with Eureka as service registry, a `RibbonServer` has a `getServer()` method that returns a `DiscoveryEnabledServer` instance, which is the server information returned by Eureka. Adding the proper casts allows us to retrieve the Eureka `InstanceInfo` object from an instance of `DiscoveryEnabledServer` and this yields full access to all the instance info provided by Eureka (not only the metadata).

From the Eureka Instance information, we can retrieve the metadata info and use it for filling and setting the CF routing header properly.
The CF routing header has the name `X-CF-APP-INSTANCE` and reuqires the following information:
*  The GUID of the CF application that implements the service and of which several instances exist for load balancing.
*  The index of the service / application instance that the request should be routed to. 

We simply add this information into the service instance metadata map of the (`address-service`) service's `application.yml`:

```yaml
---
spring.profiles: cloud

eureka:
  instance: 
    hostname: ${vcap.application.uris[0]:localhost}
    nonSecurePortEnabled: false
    securePortEnabled: true
    securePort: 443
    metadata-map:
      # Adding information about the application GUID and app instance index to 
      # each instance metadata. This will be used for setting the X-CF-APP-INSTANCE header
      # to instruct Go-Router where to route.
      cfAppGuid:       ${vcap.application.application_id}
      cfInstanceIndex: ${INSTANCE_INDEX}
    
  client: 
    serviceUrl:
      defaultZone: https://eureka-server.<your cf domain>/eureka
```

Note, the `cfAppGuid` and `cfInstanceIndex` properties. These are the properties that `CFLoadBalancerRequestTransformer` looks for and uses to set the header with.

The rest is simply setting the header on the load balancer's HTTP request.

As a result, Go-Router will always forward the request the service instance selected by Ribbon.

### Registering the `LoadBalancerRequestTransformer` Implementation

To register the `LoadBalancerRequestTransformer` you need to declare it in your Spring configuration, e.g. the SpringBoot application class (`RibbonCloudFoundryRetryTest`):

```java
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@EnableCircuitBreaker
public class RibbonCloudFoundryRetryTest {

    public static void main(String[] args) throws RestClientException, IOException {
        ApplicationContext ctx = SpringApplication.run(RibbonCloudFoundryRetryTest.class, args);
        
        FailingAddressServiceClient failingAddressServiceClient = ctx.getBean(FailingAddressServiceClient.class);
        System.out.println(failingAddressServiceClient);
        System.err.println("Address from RestTemplate Approach: " + failingAddressServiceClient.getAddress());
    }

    @Bean
    public FailingAddressServiceClient failingAddressServiceClient() {
        return new FailingAddressServiceClient();
    }

    @LoadBalanced // Note this annotation! It makes sure that RestTemplate uses Ribbon under the hood and thus inherits Eureka integration.
    @Bean
    public RestTemplate failingAddressServiceClientRestTemplate() {
        return new RestTemplate();
    }

    @Bean
    public LoadBalancerRequestTransformer customRequestTransformer() {
        return new CFLoadBalancerRequestTransformer();
    }
}
```

Note the `customRequestTransformer()` bean declaration!

Also note that the application uses a `@LoadBalanced RestTemplate` as its HTTP implementation (should also work for `FeignClient`s).
As a result Ribbon will act as the load balancer implementation and the `LoadBalancerRequestTransformer` will intercept the requests Ribbon will send (including all its retry attempts it may perform). 

## Ribbon Injection Test

For those, who are asking themselves how one can get access to Ribbon's load balancers on application level and have the various Ribbon components injected into their application code, we have created a sample which is available in `com.fonz.cloud.address.service.client.ribboninject`.

`RibbonInjectTest` is the main Spring Boot application. It declares a `@RibbonClient` with a custom configuration that overrides the beans provided by Spring Cloud's  [`RibbonAutoConfiguration`](https://github.com/spring-cloud/spring-cloud-netflix/blob/master/spring-cloud-netflix-ribbon/src/main/java/org/springframework/cloud/netflix/ribbon/RibbonAutoConfiguration.java) class. 

That custom configuration is given in `RibbonAutoConfigurationOverrides`:

```java
public class RibbonAutoConfigurationOverrides {

    @Autowired
    SpringClientFactory springClientFactory;
    
    @Bean
    public RetryableRibbonLoadBalancingHttpClient retryableRibbonLoadBalancingHttpClient(
            IClientConfig config, ServerIntrospector serverIntrospector,
            ILoadBalancer loadBalancer, RetryHandler retryHandler,
            LoadBalancedRetryFactory loadBalancedRetryFactory,
            CloseableHttpClient httpClient,
            RibbonLoadBalancerContext ribbonLoadBalancerContext) {

        CustomRetryableRibbonLoadBalancingHttpClient client = new CustomRetryableRibbonLoadBalancingHttpClient(httpClient, config, serverIntrospector, loadBalancedRetryFactory);
        client.setLoadBalancer(loadBalancer);
        client.setRetryHandler(retryHandler);
        client.setRibbonLoadBalancerContext(ribbonLoadBalancerContext);
        return client;
    }
    
    @Bean
    public LoadBalancerClient loadBalancerClient() {
        return new CustomRibbonLoadBalancerClient(springClientFactory);
    }
}
```

This configruation gets access to the `SpringClientFactory` by auto-wiring it. `SpringClientFactory` is the class that maintains the application contexts for the various `@RibbonClient`s you may have decelared in your application. Each application-context is identified by a unique name - the name of the `@RibbonClient`.
In combination with Eureka, the name of the `@RibbonClient` is the name of the service as registered in Eureka.

In essence, `SpringClientFactory` is the instance to get access to the created and used Ribbon components for a given client. Thus, if you want to get hold of the currently used Ribbon `ILoadBalancer`, `IPing`, `ServerList<Server>` or other implementations (as given [here](http://cloud.spring.io/spring-cloud-static/Edgware.SR5/multi/multi_spring-cloud-ribbon.html#_customizing_the_ribbon_client)), you get them from `SpringClientFactory`.

For some of the common Ribbon interfaces there are dedicated getters. For those that have no dedicated getter, you can use `SpringClientFactory`'s `getInstance(String name, Class clazz)` method. 

For example, `com.fonz.cloud.address.service.client.ribboninject.FailingAddressServiceClient` shows this: 

```java
public class FailingAddressServiceClient {
    @Autowired
    private SpringClientFactory factory;
    
    @HystrixCommand(fallbackMethod = "onErrorFallback", commandKey = "address-service/failing-address")
    public String getAddress() throws RestClientException, IOException {
        
        System.out.println("Factory: " + factory);
        
        ILoadBalancer lb = factory.getLoadBalancer("address-service");
        System.out.println("LoadBalancer: " + lb);
        
        IPing ping = factory.getInstance("address-service", IPing.class);
        System.out.println("Ping: " + ping);
    ...
    }
...
}
```
Note, how the `IPing` instance is retrieved.

### Running Ribbon Injection Test

Ribbon Injection Test comes with its own Spring Boot main class. 
To build the project with the `RibbonInjectTest` class as the main class, you can run the script `build-ribbon-inject.sh` in `address-service-client`.
This will enable a different profile in `address-service-client`'s `pom.xml` and set the main class for the project to `RibbonInjectTest`.
The resulting Jar in `address-service-client/target/` will then use `RibbonInjectTest` and can simply be started using `java -jar address-service-client/target/address-service-client-0.0.1-SNAPSHOT.jar`.

# References

* [Client Side Load Balancer: Ribbon](https://cloud.spring.io/spring-cloud-netflix/multi/multi_spring-cloud-ribbon.html)
* [Spring Cloud - LoadBalancerRequestTransformer](https://github.com/spring-cloud/spring-cloud-commons/blob/master/spring-cloud-commons/src/main/java/org/springframework/cloud/client/loadbalancer/LoadBalancerRequestTransformer.java)
* [Spring Cloud - LoadBalancerAutoConfiguration](https://github.com/spring-cloud/spring-cloud-commons/blob/master/spring-cloud-commons/src/main/java/org/springframework/cloud/client/loadbalancer/LoadBalancerAutoConfiguration.java)
* [Spring Cloud - RibbonAutoConfiguration](https://github.com/spring-cloud/spring-cloud-netflix/blob/master/spring-cloud-netflix-ribbon/src/main/java/org/springframework/cloud/netflix/ribbon/RibbonAutoConfiguration.java)
* [Spring Cloud - RibbonLoadBalancerClient](https://github.com/spring-cloud/spring-cloud-netflix/blob/master/spring-cloud-netflix-ribbon/src/main/java/org/springframework/cloud/netflix/ribbon/RibbonLoadBalancerClient.java)
* [Spring Cloud - RibbonClientConfiguration](https://github.com/spring-cloud/spring-cloud-netflix/blob/master/spring-cloud-netflix-ribbon/src/main/java/org/springframework/cloud/netflix/ribbon/RibbonClientConfiguration.java)
* [Spring Cloud - HttpClientRibbonConfiguration](https://github.com/spring-cloud/spring-cloud-netflix/blob/master/spring-cloud-netflix-ribbon/src/main/java/org/springframework/cloud/netflix/ribbon/apache/HttpClientRibbonConfiguration.java)
* [Pivotal - Surgical Routing Request Transformer](https://github.com/pivotal-cf/spring-cloud-services-connector/blob/master/spring-cloud-services-spring-connector/src/main/java/io/pivotal/spring/cloud/service/eureka/SurgicalRoutingRequestTransformer.java)
* [SpringClientFactory](https://github.com/spring-cloud/spring-cloud-netflix/blob/master/spring-cloud-netflix-ribbon/src/main/java/org/springframework/cloud/netflix/ribbon/SpringClientFactory.java)
* [Spring Cloud - RibbonClient](https://github.com/spring-cloud/spring-cloud-netflix/blob/master/spring-cloud-netflix-ribbon/src/main/java/org/springframework/cloud/netflix/ribbon/RibbonClient.java)
* [RibbonClientConfiguration isn't very useful](https://github.com/spring-cloud/spring-cloud-netflix/issues/935)
* [Ribbon: Unable to set default configuration using @RibbonClients(defaultConfiguration=...)](https://github.com/spring-cloud/spring-cloud-netflix/issues/374)
* [Understanding Application Context](https://spring.io/understanding/application-context)
* [Client Side Load Balancing with Ribbon and Spring Cloud](https://spring.io/guides/gs/client-side-load-balancing/)