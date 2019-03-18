package com.fonz.cloud.address.service.client.cf;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fonz.cloud.address.service.client.Address;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;

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

    @Autowired
    private RestTemplate restTemplate;
    
    @HystrixCommand(fallbackMethod = "onErrorFallback", commandKey = "address-service/failing-address")
    public String getAddress() throws RestClientException, IOException {
        
        MultiValueMap<String, String> headers = null;
        HttpEntity<Address> entity = new HttpEntity<Address>(headers);
        
        /**
         * The URL used here is a reference to the Eureka registry looking up a
         * service instance of "address-service". This works, because the RestTemplate
         * is loadbalanced (see {@link ClientApp#restTemplate()}) and uses Ribbon (which 
         * integrates with Eureka).
         */
        ResponseEntity<Address> response = restTemplate.exchange("http://address-service/failing-address", HttpMethod.GET, entity, Address.class);
        
        Address address = response.getBody();
        
        // Note: this should never be called. Instead the fallback should eventually be executed.
        String addressString = address.toString();
        System.out.println("Address from RestTemplate: ");
        System.out.println(addressString);
        
        return addressString;
    }
    
    @SuppressWarnings("unused")
    private String onErrorFallback() {
        return "Fallback called for FailingAddressService!";        
    }
}
