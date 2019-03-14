package com.fonz.cloud.address.service.client;

public class Address {
    private String postalCode;
    private String city;
    private String streetName;
    private String houseNumber;
    private String country;
    
    public String getPostalCode() {
        return postalCode;
    }
    
    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }
    
    public String getCity() {
        return city;
    }
    
    public void setCity(String city) {
        this.city = city;
    }
    
    public String getStreetName() {
        return streetName;
    }
    
    public void setStreetName(String streetName) {
        this.streetName = streetName;
    }
    
    public String getHouseNumber() {
        return houseNumber;
    }
    
    public void setHouseNumber(String houseNumber) {
        this.houseNumber = houseNumber;
    }
    
    public String getCountry() {
        return country;
    }
    
    public void setCountry(String country) {
        this.country = country;
    }

    @Override
    public String toString() {
        return "Address [postalCode=" + postalCode + ", city=" + city + ", streetName=" + streetName + ", houseNumber="
                + houseNumber + ", country=" + country + "]";
    }
}
