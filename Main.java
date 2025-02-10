import java.util.*;
import java.time.*;
import java.util.concurrent.*;
import java.net.InetAddress;
import java.net.NetworkInterface;

public class Main {
    private static final int MAX_TICKETS_PER_BOOKING = 5;
    private static final int MAX_BOOKINGS_PER_WINDOW = 3;
    private static final int TIME_WINDOW_MINUTES = 30;
    private static final Map<String, UserBookingInfo> userBookingHistory = new ConcurrentHashMap<>();
    private static int availableTickets = 500;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Welcome to the Railway Ticket Booking System!");

        while (true) {
            ClientInfo clientInfo = detectClientInfo();
            System.out.println("\nDetected Client Information:");
            System.out.println("IP Address: " + clientInfo.ipAddress);
            System.out.println("Machine ID: " + clientInfo.machineId);
            
            if (!canBookTickets(clientInfo.ipAddress)) {
                System.out.println("\nBooking limit reached for this IP address!");
                System.out.println("You can make maximum " + MAX_BOOKINGS_PER_WINDOW + 
                                 " bookings within " + TIME_WINDOW_MINUTES + " minutes.");
                System.out.println("Please try again later.");
                break;
            }

           
            if (availableTickets <= 0) {
                System.out.println("Sorry, all tickets are currently sold out!");
                break;
            }

            
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

            processBooking(clientInfo, requestedTickets, scanner);

            System.out.print("\nDo you want to book more tickets? (yes/no): ");
            if (!scanner.nextLine().trim().toLowerCase().equals("yes")) {
                break;
            }
        }
        scanner.close();
    }

    static class ClientInfo {
        String ipAddress;
        String machineId;

        ClientInfo(String ipAddress, String machineId) {
            this.ipAddress = ipAddress;
            this.machineId = machineId;
        }
    }

    static class UserBookingInfo {
        List<LocalDateTime> bookingTimes = new ArrayList<>();

        boolean canMakeBooking() {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime windowStart = now.minusMinutes(TIME_WINDOW_MINUTES);
            
            bookingTimes.removeIf(time -> time.isBefore(windowStart));
            
            return bookingTimes.size() < MAX_BOOKINGS_PER_WINDOW;
        }

        void recordBooking() {
            bookingTimes.add(LocalDateTime.now());
        }

        int getRemainingBookings() {
            return MAX_BOOKINGS_PER_WINDOW - bookingTimes.size();
        }
    }

    private static ClientInfo detectClientInfo() {
        String ipAddress = "Unknown";
        String machineId = "Unknown";

        try {
            InetAddress localHost = InetAddress.getLocalHost();
            ipAddress = localHost.getHostAddress();

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

    private static boolean canBookTickets(String ipAddress) {
        UserBookingInfo userInfo = userBookingHistory.computeIfAbsent(ipAddress, k -> new UserBookingInfo());
        return userInfo.canMakeBooking();
    }

    private static void processBooking(ClientInfo clientInfo, int requestedTickets, Scanner scanner) {
        synchronized (Main.class) {
            if (requestedTickets > availableTickets) {
                System.out.println("Sorry, only " + availableTickets + " tickets available.");
                return;
            }

            List<String> passengerNames = new ArrayList<>();
            for (int i = 1; i <= requestedTickets; i++) {
                System.out.print("Enter name for passenger " + i + ": ");
                passengerNames.add(scanner.nextLine().trim());
            }

            System.out.println("\nProcessing payment...");
            if (!processPayment()) {
                System.out.println("Payment failed. Booking cancelled.");
                return;
            }

            List<String> ticketCodes = generateTickets(requestedTickets);
            availableTickets -= requestedTickets;
            
            UserBookingInfo userInfo = userBookingHistory.get(clientInfo.ipAddress);
            userInfo.recordBooking();
            
            System.out.println("\nBooking Successful!");
            System.out.println("Remaining bookings allowed in this time window: " + 
                             userInfo.getRemainingBookings());
            
            System.out.println("\nTicket Details:");
            for (int i = 0; i < ticketCodes.size(); i++) {
                System.out.println("\nTicket " + (i + 1) + ":");
                System.out.println("Code: " + ticketCodes.get(i));
                System.out.println("Passenger: " + passengerNames.get(i));
            }

            System.out.println("\nBooking Information:");
            System.out.println("IP Address: " + clientInfo.ipAddress);
            System.out.println("Machine ID: " + clientInfo.machineId);
            System.out.println("Booking Time: " + LocalDateTime.now());
        }
    }

    private static boolean processPayment() {
        try {
            Thread.sleep(1500); 
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static List<String> generateTickets(int count) {
        List<String> tickets = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            tickets.add(generateUniqueTicketCode());
        }
        return tickets;
    }

    private static String generateUniqueTicketCode() {
        Random random = new Random();
        return String.format("%s%s%s%04d",
            (char)('A' + random.nextInt(26)),
            (char)('A' + random.nextInt(26)),
            (char)('A' + random.nextInt(26)),
            random.nextInt(10000));
    }
}