package com.sap.cloud.address.service.client.retrytest;

import java.net.URI;
import java.util.Map;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerRequestTransformer;
import org.springframework.cloud.netflix.ribbon.RibbonLoadBalancerClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.niws.loadbalancer.DiscoveryEnabledServer;

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