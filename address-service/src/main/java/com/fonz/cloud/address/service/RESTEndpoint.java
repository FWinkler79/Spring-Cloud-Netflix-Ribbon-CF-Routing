package com.fonz.cloud.address.service;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class RESTEndpoint {
  
    @RequestMapping(value = "/address", method = RequestMethod.GET)
    public Address firstPage() throws Exception {
        
        // simulate random errors
        if(Math.random() > .5) {
            Thread.sleep(1500);
            System.out.println("Simulating random ADDRESS-SERVICE downtime.");
            throw new RuntimeException("Simulating random ADDRESS-SERVICE downtime.");
        }
        
        Address address = new Address();
        address.setCity("New York");
        address.setCountry("United States");
        address.setHouseNumber("101a");
        address.setPostalCode("52670");
        address.setStreetName("Fifth-Ave");

        return address;
    }
    
    @RequestMapping(value = "/failing-address", method = RequestMethod.GET)
    public Address failing() throws Exception {
        Thread.sleep(1500);
        System.out.println("Simulating failing ADDRESS-SERVICE");
        throw new RuntimeException("Simulating failing ADDRESS-SERVICE.");
    }
}
