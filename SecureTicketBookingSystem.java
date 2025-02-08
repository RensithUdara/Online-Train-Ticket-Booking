import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SecureTicketBookingSystem {
    private static final Logger logger = Logger.getLogger(SecureTicketBookingSystem.class.getName());

    private final int totalTickets;
    private final int maxTicketsPerUser;
    private final int rateLimitMinutes;
    private final int maxRequestsPerWindow;

    private final AtomicInteger availableTickets;
    private final Map<String, Integer> userTicketCounts;
    private final Map<String, Deque<LocalDateTime>> userRequestTimes;
    private final Map<String, Set<String>> userDeviceIPs;

    public SecureTicketBookingSystem(int totalTickets, int maxTicketsPerUser, int rateLimitMinutes, int maxRequestsPerWindow) {
        this.totalTickets = totalTickets;
        this.maxTicketsPerUser = maxTicketsPerUser;
        this.rateLimitMinutes = rateLimitMinutes;
        this.maxRequestsPerWindow = maxRequestsPerWindow;

        this.availableTickets = new AtomicInteger(totalTickets);
        this.userTicketCounts = new ConcurrentHashMap<>();
        this.userRequestTimes = new ConcurrentHashMap<>();
        this.userDeviceIPs = new ConcurrentHashMap<>();
    }

    public BookingResult bookTicket(String userId, String deviceId, String ipAddress, int requestedTickets) {
        try {
            if (!isValidUser(userId)) {
                logger.warning("Invalid user credentials for user: " + userId);
                return new BookingResult(false, "Invalid user credentials");
            }

            if (isDeviceOrIPSuspicious(userId, deviceId, ipAddress)) {
                logger.warning("Suspicious activity detected for user: " + userId);
                return new BookingResult(false, "Suspicious activity detected");
            }

            if (isRateLimitExceeded(userId)) {
                logger.warning("Rate limit exceeded for user: " + userId);
                return new BookingResult(false, "Rate limit exceeded. Please try again later");
            }

            if (requestedTickets <= 0 || requestedTickets > maxTicketsPerUser) {
                logger.warning("Invalid ticket quantity requested by user: " + userId);
                return new BookingResult(false,
                        "Invalid ticket quantity. Maximum " + maxTicketsPerUser + " tickets per user");
            }

            int userTotal = userTicketCounts.getOrDefault(userId, 0);
            if (userTotal + requestedTickets > maxTicketsPerUser) {
                logger.warning("User " + userId + " exceeded maximum allowed tickets");
                return new BookingResult(false,
                        "Exceeds maximum allowed tickets per user (" + maxTicketsPerUser + ")");
            }

            if (requestedTickets > availableTickets.get()) {
                logger.warning("Not enough tickets available for user: " + userId);
                return new BookingResult(false, "Not enough tickets available");
            }

            // Atomic operation to decrement tickets
            int remainingTickets = availableTickets.addAndGet(-requestedTickets);
            if (remainingTickets < 0) {
                availableTickets.addAndGet(requestedTickets); // Rollback
                logger.warning("Rollback due to insufficient tickets for user: " + userId);
                return new BookingResult(false, "Not enough tickets available");
            }

            userTicketCounts.put(userId, userTotal + requestedTickets);
            recordRequest(userId);
            recordDeviceIP(userId, deviceId, ipAddress);

            logger.info("Successfully booked " + requestedTickets + " ticket(s) for user: " + userId);
            return new BookingResult(true, "Successfully booked " + requestedTickets + " ticket(s)");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unexpected error during ticket booking for user: " + userId, e);
            return new BookingResult(false, "An unexpected error occurred. Please try again later.");
        }
    }

    private boolean isValidUser(String userId) {
        // Implement actual user validation logic here (e.g., check against a database or user service)
        return true; // Placeholder
    }

    private boolean isDeviceOrIPSuspicious(String userId, String deviceId, String ipAddress) {
        Set<String> userDevices = userDeviceIPs.getOrDefault(userId, new HashSet<>());

        // Limit the number of devices/IPs per user
        if (userDevices.size() > 3) {
            return true;
        }

        // Check if the IP address is used by another user
        for (Map.Entry<String, Set<String>> entry : userDeviceIPs.entrySet()) {
            if (!entry.getKey().equals(userId) && entry.getValue().contains(ipAddress)) {
                return true;
            }
        }
        return false;
    }

    private boolean isRateLimitExceeded(String userId) {
        Deque<LocalDateTime> requests = userRequestTimes.computeIfAbsent(userId, k -> new ArrayDeque<>());
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(rateLimitMinutes);

        // Remove old requests
        while (!requests.isEmpty() && requests.peekFirst().isBefore(cutoff)) {
            requests.pollFirst();
        }

        return requests.size() >= maxRequestsPerWindow;
    }

    private void recordRequest(String userId) {
        Deque<LocalDateTime> requests = userRequestTimes.computeIfAbsent(userId, k -> new ArrayDeque<>());
        requests.addLast(LocalDateTime.now());
    }

    private void recordDeviceIP(String userId, String deviceId, String ipAddress) {
        userDeviceIPs.computeIfAbsent(userId, k -> new HashSet<>()).add(deviceId);
        userDeviceIPs.computeIfAbsent(userId, k -> new HashSet<>()).add(ipAddress);
    }

    public int getAvailableTickets() {
        return availableTickets.get();
    }
}

class BookingResult {
    private final boolean success;
    private final String message;

    public BookingResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }
}