package booking;

import misc.MessageSenderService;
import misc.PropertyLoader;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;

public class FlightBookingSystem implements BookingSystem {

    private final int flightPortStart = Integer.parseInt(PropertyLoader.loadProperties().getProperty("bookingsystems.flight.port.start"));
    private final int minSeats = Integer.parseInt(PropertyLoader.loadProperties().getProperty("bookingsystems.flight.quantity.min"));
    private final int maxSeats = Integer.parseInt(PropertyLoader.loadProperties().getProperty("bookingsystems.flight.quantity.max"));
    private final int port;
    private final HashMap<Integer, String> airlineList;
    private int seats = new Random().nextInt(minSeats, maxSeats);
    private final HashMap<String, Boolean> bookingList = new HashMap<>();
    private final List<String> cancelList = new ArrayList<>();


    public FlightBookingSystem(int port, HashMap<Integer, String> airlineList) {
        this.port = port;
        this.airlineList = airlineList;
    }

    public void start(int backlog) {
        try (ServerSocket serverSocket = new ServerSocket(port, backlog)) {
            System.out.println("FlightBookingSystem running on port " + port);
            while (true) {
                Socket flightSocket = serverSocket.accept();
                Thread flightThread = new Thread(() -> {
                    try {
                        BufferedReader in = new BufferedReader(new InputStreamReader(flightSocket.getInputStream()));
                        String inputLine;
                        while ((inputLine = in.readLine()) != null) {
                            //Handle message and answer
                            System.out.println("FlightBookingSystem - Received message: " + inputLine);
                            handleRequest(inputLine, flightSocket);
                        }
                        in.close();
                        flightSocket.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                flightThread.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void handleRequest(String booking, Socket flightSocket) {
        //splitMessage -> [0]=BookingRq [1]=UUID [2]=Menge
        String[] splitMessage = booking.split(" ", 3);
        String processId = splitMessage[1];
        String whatAmI = splitMessage[0];
        int airlineNumber = port - flightPortStart;
        boolean successful;
        int requestedSeats = Integer.parseInt(splitMessage[2]);

        double randomNumber = Math.random();
        double probability = Double.parseDouble(PropertyLoader.loadProperties().getProperty("bookingsystems.bookingnomessage"));
        if (randomNumber > probability) {
            if (whatAmI.equals("BookingRq")) {
                if(bookingList.containsKey(processId)){
                    MessageSenderService.sendMessageToMessageBroker("Response " + processId + " " + bookingList.get(processId) + " flight F" + airlineNumber + " " + requestedSeats);
                    return;
                }
                successful = book(requestedSeats, processId);
                //<WhatAmI> <processId> <confirmation (true/false)> <type> <Flightnumber> <amount>
                MessageSenderService.sendMessageToMessageBroker("Response " + processId + " " + successful + " flight F" + airlineNumber + " " + requestedSeats);
            } else if (whatAmI.equals("CancellationRq")) {
                if(cancelList.contains(processId)){
                    MessageSenderService.sendMessageToMessageBroker("CancellationConfirmation " + processId + " true");
                    return;
                }
                successful = cancel(requestedSeats, processId);
                //<WhatAmI> <processId> <false>
                MessageSenderService.sendMessageToMessageBroker("CancellationConfirmation " + processId + " " + successful);
            }
        }
    }


    @Override
    public synchronized boolean cancel(int requestedSeats, String processId) {
        seats += requestedSeats;
        System.out.println("FlightBookingSystem: " + getName() + " " + requestedSeats + " seats freed. Remaining seats: " + seats + ".");
        cancelList.add(processId);
        return true;
    }

    @Override
    public synchronized boolean book(int requestedSeats, String processId) {
        if (requestedSeats > seats) {
            System.out.println("FlightBookingSystem: " + getName() + " " + requestedSeats + " seats could not be booked. Remaining seats: " + seats + ".");
            bookingList.put(processId, false);
            return false;
        }
        seats -= requestedSeats;
        System.out.println("FlightBookingSystem: " + getName() + " " + requestedSeats + " seats booked. Remaining seats: " + seats + ".");
        bookingList.put(processId, true);
        return true;

    }

    @Override
    public String getName() {
        return " F" + (port - flightPortStart) + " - " + airlineList.get(port);
    }
}
