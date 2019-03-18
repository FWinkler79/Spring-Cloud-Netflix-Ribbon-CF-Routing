package com.fonz.cloud.address.service.client.cf;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.netflix.ribbon.SpringClientFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fonz.cloud.address.service.client.Address;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.IPing;
import com.netflix.loadbalancer.Server;
import com.netflix.niws.loadbalancer.DiscoveryEnabledServer;

/**
 * Eureka - REST Template-based Address Service Client.
 * Uses RESTTemplate-Eureka-Integration to call a service 
 * looked up from the service registry.
 * The URL used in the RESTTemplate is not the actual service URL,
 * but an alias name (i.e. the service-name) for the service in Eureka.  
 * 
 * See also: https://spring.io/blog/2015/01/20/microservice-registration-and-discovery-with-spring-cloud-and-netflix-s-eureka
 */
public class FailingAddressServiceClient {

// This does not work! The beans are defined in a separate Application Context named / identified by the Eureka service name.
// The application context is created by SpringClientFactory.
//
//    @Autowired
//    private ILoadBalancer ribbonLoadBalancer;
//    
//    @Autowired
//    private ServerList<Server> ribbonServerList;
    
    @Autowired
    private SpringClientFactory factory;
    
    @Autowired
    private RestTemplate restTemplate;
    
    @HystrixCommand(fallbackMethod = "onErrorFallback", commandKey = "address-service/failing-address")
    public String getAddress() throws RestClientException, IOException {
        
        System.out.println("Factory: " + factory);
        
        ILoadBalancer lb = factory.getLoadBalancer("address-service");
        System.out.println("LoadBalancer: " + lb);
        
        IPing ping = factory.getInstance("address-service", IPing.class);
        System.out.println("Ping: " + ping);
        
        List<Server> serverList = lb.getReachableServers();
        System.out.println("ServerList: " + serverList);
        
        for(Server s : serverList) {
            DiscoveryEnabledServer server = (DiscoveryEnabledServer) s; // only do this when you run against Eureka and are a Eureka Client.
            
            InstanceInfo instanceInfo = server.getInstanceInfo();
            System.out.println("InstanceInfo - version: " + instanceInfo.getMetadata().get("version"));
            
            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(instanceInfo);
            System.err.println("-- InstanceInfo: (You can get all of this with getters!)" );
            System.err.println(json);
            
            System.out.println("ServerID:    " + server.getId());
            System.out.println("InstanceID:  " + server.getMetaInfo().getInstanceId());
            System.out.println("AppName:     " + server.getMetaInfo().getAppName());
            System.out.println("ServerGroup: " + server.getMetaInfo().getServerGroup());
            System.out.println("Zone:        " + server.getZone());
        }
        
        DiscoveryEnabledServer chosenServer = (DiscoveryEnabledServer) lb.chooseServer("address-service");
        
        HttpHeaders headers = new HttpHeaders();
        addInstanceIndexRoutingHeader(chosenServer, headers);
        
        /**
         * The URL used here is a reference to the Eureka registry looking up a
         * service instance of "address-service". This works, because the RestTemplate
         * is loadbalanced (see {@link ClientApp#restTemplate()}) and uses Ribbon (which 
         * integrates with Eureka).
         */
        
        HttpEntity<Address> entity = new HttpEntity<Address>(headers);
        ResponseEntity<Address> response = restTemplate.exchange("http://address-service/failing-address", HttpMethod.GET, entity, Address.class);
        
        Address address = response.getBody();
        
        // Note: this should never be called. Instead the fallback should eventually be executed.
        String addressString = address.toString();
        System.out.println("Address from RestTemplate: ");
        System.out.println(addressString);
        
        return addressString;
    }
    
    private void addInstanceIndexRoutingHeader(DiscoveryEnabledServer chosenServer, HttpHeaders headers) {
        final String HEADER_NAME = "X-CF-APP-INSTANCE";
        final String CF_APP_GUID = chosenServer.getInstanceInfo().getMetadata().get("cfAppGuid");
        final String CF_INSTANCE_INDEX = chosenServer.getInstanceInfo().getMetadata().get("cfInstanceIndex");
        
        final String HEADER_VALUE = CF_APP_GUID + ":" + CF_INSTANCE_INDEX;
        
        System.out.println("App-Guid:       " + CF_APP_GUID );
        System.out.println("Instance-Index: " + CF_INSTANCE_INDEX );
        System.out.println("Header (" + HEADER_NAME +  ", " + HEADER_VALUE + ")");
        
        headers.set(HEADER_NAME, HEADER_VALUE);
    } 
    
    @SuppressWarnings("unused")
    private String onErrorFallback() {
        return "Fallback called for FailingAddressService!";        
    }
}
