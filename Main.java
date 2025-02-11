import java.util.*;
import java.time.*;
import java.util.concurrent.*;
import java.net.InetAddress;
import java.net.NetworkInterface;

public class Main {
    // Constants for ticket booking rules
    private static final int MAX_TICKETS_PER_BOOKING = 5; // Maximum tickets per booking
    private static final int MAX_BOOKINGS_PER_WINDOW = 3; // Maximum bookings per time window
    private static final int TIME_WINDOW_MINUTES = 30; // Time window in minutes
    private static final Map<String, UserBookingInfo> userBookingHistory = new ConcurrentHashMap<>(); // Stores user booking history
    private static int availableTickets = 500; // Total available tickets

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Welcome to the Railway Ticket Booking System!");

        while (true) {
            // Detect client information (IP address and machine ID)
            ClientInfo clientInfo = detectClientInfo();
            System.out.println("\nDetected Client Information:");
            System.out.println("IP Address: " + clientInfo.ipAddress);
            System.out.println("Machine ID: " + clientInfo.machineId);

            // Check if the client can make a booking based on their IP address
            if (!canBookTickets(clientInfo.ipAddress)) {
                System.out.println("\nBooking limit reached for this IP address!");
                System.out.println("You can make maximum " + MAX_BOOKINGS_PER_WINDOW +
                                 " bookings within " + TIME_WINDOW_MINUTES + " minutes.");
                System.out.println("Please try again later.");
                break;
            }

            // Check if tickets are available
            if (availableTickets <= 0) {
                System.out.println("Sorry, all tickets are currently sold out!");
                break;
            }

            // Prompt the user to enter the number of tickets to book
            System.out.println("\nYou can book up to " + MAX_TICKETS_PER_BOOKING + " tickets.");
            System.out.print("Enter number of tickets to book (1-" + MAX_TICKETS_PER_BOOKING + "): ");

            int requestedTickets;
            try {
                requestedTickets = Integer.parseInt(scanner.nextLine());
                if (requestedTickets < 1 || requestedTickets > MAX_TICKETS_PER_BOOKING) {
                    System.out.println("Invalid number of tickets. Must be between 1 and " + MAX_TICKETS_PER_BOOKING);
                    continue;
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a number.");
                continue;
            }

            // Process the booking
            processBooking(clientInfo, requestedTickets, scanner);

            // Ask if the user wants to book more tickets
            System.out.print("\nDo you want to book more tickets? (yes/no): ");
            if (!scanner.nextLine().trim().toLowerCase().equals("yes")) {
                break;
            }
        }
        scanner.close();
    }

    // Inner class to store client information
    static class ClientInfo {
        String ipAddress; // IP address of the client
        String machineId; // Machine ID (MAC address) of the client

        ClientInfo(String ipAddress, String machineId) {
            this.ipAddress = ipAddress;
            this.machineId = machineId;
        }
    }

    // Inner class to store user booking information
    static class UserBookingInfo {
        List<LocalDateTime> bookingTimes = new ArrayList<>(); // List of booking times

        // Check if the user can make a booking within the time window
        boolean canMakeBooking() {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime windowStart = now.minusMinutes(TIME_WINDOW_MINUTES);

            // Remove old bookings outside the time window
            bookingTimes.removeIf(time -> time.isBefore(windowStart));

            // Check if the number of bookings is within the limit
            return bookingTimes.size() < MAX_BOOKINGS_PER_WINDOW;
        }

        // Record a new booking
        void recordBooking() {
            bookingTimes.add(LocalDateTime.now());
        }

        // Get the number of remaining bookings allowed in the time window
        int getRemainingBookings() {
            return MAX_BOOKINGS_PER_WINDOW - bookingTimes.size();
        }
    }

    // Method to detect client information (IP address and machine ID)
    private static ClientInfo detectClientInfo() {
        String ipAddress = "Unknown";
        String machineId = "Unknown";

        try {
            InetAddress localHost = InetAddress.getLocalHost();
            ipAddress = localHost.getHostAddress();

            // Get the MAC address of the machine
            NetworkInterface network = NetworkInterface.getByInetAddress(localHost);
            if (network != null) {
                byte[] mac = network.getHardwareAddress();
                if (mac != null) {
                    StringBuilder sb = new StringBuilder();
                    for (byte b : mac) {
                        sb.append(String.format("%02X", b));
                    }
                    machineId = sb.toString();
                }
            }
        } catch (Exception e) {
            System.out.println("Note: Using fallback client identification due to detection error.");
            ipAddress = "127.0.0.1";
            machineId = "LOCAL-" + System.currentTimeMillis();
        }

        return new ClientInfo(ipAddress, machineId);
    }

    // Method to check if the user can book tickets based on their IP address
    private static boolean canBookTickets(String ipAddress) {
        UserBookingInfo userInfo = userBookingHistory.computeIfAbsent(ipAddress, k -> new UserBookingInfo());
        return userInfo.canMakeBooking();
    }

    // Method to process the booking
    private static void processBooking(ClientInfo clientInfo, int requestedTickets, Scanner scanner) {
        synchronized (Main.class) {
            // Check if there are enough tickets available
            if (requestedTickets > availableTickets) {
                System.out.println("Sorry, only " + availableTickets + " tickets available.");
                return;
            }

            // Collect passenger names
            List<String> passengerNames = new ArrayList<>();
            for (int i = 1; i <= requestedTickets; i++) {
                System.out.print("Enter name for passenger " + i + ": ");
                passengerNames.add(scanner.nextLine().trim());
            }

            // Simulate payment processing
            System.out.println("\nProcessing payment...");
            if (!processPayment()) {
                System.out.println("Payment failed. Booking cancelled.");
                return;
            }

            // Generate ticket codes and update available tickets
            List<String> ticketCodes = generateTickets(requestedTickets);
            availableTickets -= requestedTickets;

            // Record the booking
            UserBookingInfo userInfo = userBookingHistory.get(clientInfo.ipAddress);
            userInfo.recordBooking();

            // Display booking success message
            System.out.println("\nBooking Successful!");
            System.out.println("Remaining bookings allowed in this time window: " +
                             userInfo.getRemainingBookings());

            // Display ticket details
            System.out.println("\nTicket Details:");
            for (int i = 0; i < ticketCodes.size(); i++) {
                System.out.println("\nTicket " + (i + 1) + ":");
                System.out.println("Code: " + ticketCodes.get(i));
                System.out.println("Passenger: " + passengerNames.get(i));
            }

            // Display booking information
            System.out.println("\nBooking Information:");
            System.out.println("IP Address: " + clientInfo.ipAddress);
            System.out.println("Machine ID: " + clientInfo.machineId);
            System.out.println("Booking Time: " + LocalDateTime.now());
        }
    }

    // Simulate payment processing
    private static boolean processPayment() {
        try {
            Thread.sleep(1500); // Simulate payment processing time
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    // Generate a list of unique ticket codes
    private static List<String> generateTickets(int count) {
        List<String> tickets = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            tickets.add(generateUniqueTicketCode());
        }
        return tickets;
    }

    // Generate a unique ticket code
    private static String generateUniqueTicketCode() {
        Random random = new Random();
        return String.format("%s%s%s%04d",
            (char)('A' + random.nextInt(26)), // Random letter
            (char)('A' + random.nextInt(26)), // Random letter
            (char)('A' + random.nextInt(26)), // Random letter
            random.nextInt(10000)); // Random 4-digit number
    }
}