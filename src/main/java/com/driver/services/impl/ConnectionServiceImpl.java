package com.driver.services.impl;

import com.driver.model.*;
import com.driver.repository.ConnectionRepository;
import com.driver.repository.ServiceProviderRepository;
import com.driver.repository.UserRepository;
import com.driver.services.ConnectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ConnectionServiceImpl implements ConnectionService {
    @Autowired
    UserRepository userRepository2;
    @Autowired
    ServiceProviderRepository serviceProviderRepository2;

    @Autowired
    ConnectionRepository connectionRepository2;

    @Override
    public User connect(int userId, String countryName) throws Exception{

        User user = userRepository2.findById(userId).get();

        if(user.getConnected()){
            throw new Exception("Already connected");
        }
        countryName = countryName.toUpperCase();

        if(user.getCountry().getCountryName().equals(countryName)){
            return user;
        }

        List<ServiceProvider> serviceProviderList = user.getServiceProviderList();
        if(serviceProviderList.size() == 0){
            throw new Exception("Unable to connect");
        }

        int smallestId = Integer.MAX_VALUE;
        ServiceProvider curProvider = null;
        Country curCountry = null;

        for(ServiceProvider serviceProvider:serviceProviderList){
            for(Country country:serviceProvider.getCountryList()){
                if(country.getCountryName().equals(countryName) && serviceProvider.getId() < smallestId){
                    smallestId = serviceProvider.getId();
                    curProvider = serviceProvider;
                    curCountry = country;
                }
            }
        }

        if(smallestId == Integer.MAX_VALUE){
            throw new Exception("Unable to connect");
        }

        Connection connection = new Connection();
        connection.setUser(user);
        connection.setServiceProvider(curProvider);

        user.getConnectionList().add(connection);
        curProvider.getConnectionList().add(connection);

        user.setConnected(true);

        //maskId
        String curCountryCode = curCountry.getCode();

        user.setMaskedIp(curCountryCode +"."+ curProvider.getId() +"."+ user.getId());

        serviceProviderRepository2.save(curProvider);

        return user;
    }
    @Override
    public User disconnect(int userId) throws Exception {
        //If the given user was not connected to a vpn, throw "Already disconnected" exception.
        //Else, disconnect from vpn, make masked Ip as null, update relevant attributes and return updated user.

        User user = userRepository2.findById(userId).get();
        if(!user.getConnected()){
            throw new Exception("Already disconnected");
        }
        user.setConnected(false);
        user.setMaskedIp(null);

        userRepository2.save(user);

        return user;
    }
    @Override
    public User communicate(int senderId, int receiverId) throws Exception {
        //Establish a connection between sender and receiver users
        //To communicate to the receiver, sender should be in the current country of the receiver.
        //If the receiver is connected to a vpn, his current country is the one he is connected to.
        //If the receiver is not connected to vpn, his current country is his original country.
        //The sender is initially not connected to any vpn. If the sender's original country
        // does not match receiver's current country, we need to connect the sender to a suitable vpn.
        // If there are multiple options, connect using the service provider having smallest id
        //If the sender's original country matches receiver's current country, we do not need to do anything
        // as they can communicate. Return the sender as it is.
        //If communication can not be established due to any reason, throw "Cannot establish communication" exception

        User sender = userRepository2.findById(senderId).get();
        User receiver = userRepository2.findById(receiverId).get();

       String receiverCountryCode = "";

       if(!receiver.getConnected()){
           receiverCountryCode = receiver.getCountry().getCode();
       }
       else{
           String maskedCountry = receiver.getMaskedIp().substring(0,3);
           for(CountryName curCountry:CountryName.values()){
               if(curCountry.toCode().equals(maskedCountry)) {
                   receiverCountryCode = curCountry.toCode();
               }
           }
       }

       if(sender.getCountry().getCode().equals(receiverCountryCode)){
            return sender;
       }
       else{
           try {
               sender = connect(senderId,receiverCountryCode);
           }
           catch (Exception e){
               throw new Exception("Cannot establish communication");
           }
       }

        if(sender.getConnected()==false) throw new Exception("Cannot establish communication");

        return sender;
    }
}
