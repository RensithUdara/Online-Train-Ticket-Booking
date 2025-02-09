import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class SecureTicketBookingSystem {
    private static final int TOTAL_TICKETS = 500;
    private static final int MAX_TICKETS_PER_USER = 5;
    private static final int RATE_LIMIT_MINUTES = 15;
    private static final int MAX_REQUESTS_PER_WINDOW = 10;

    private final AtomicInteger availableTickets;
    private final Map<String, Integer> userTicketCounts;
    private final Map<String, Deque<LocalDateTime>> userRequestTimes;
    private final Map<String, Set<String>> userDeviceIPs;

    public SecureTicketBookingSystem() {
        this.availableTickets = new AtomicInteger(TOTAL_TICKETS);
        this.userTicketCounts = new ConcurrentHashMap<>();
        this.userRequestTimes = new ConcurrentHashMap<>();
        this.userDeviceIPs = new ConcurrentHashMap<>();
    }

    public BookingResult bookTicket(String userId, String deviceId, String ipAddress, int requestedTickets) {
        if (!isValidUser(userId)) {
            return new BookingResult(false, "Invalid user credentials");
        }

        if (isDeviceOrIPSuspicious(userId, deviceId, ipAddress)) {
            return new BookingResult(false, "Suspicious activity detected");
        }

        if (isRateLimitExceeded(userId)) {
            return new BookingResult(false, "Rate limit exceeded. Please try again later");
        }

        if (requestedTickets <= 0 || requestedTickets > MAX_TICKETS_PER_USER) {
            return new BookingResult(false,
                    "Invalid ticket quantity. Maximum " + MAX_TICKETS_PER_USER + " tickets per user");
        }

        int userTotal = userTicketCounts.getOrDefault(userId, 0);
        if (userTotal + requestedTickets > MAX_TICKETS_PER_USER) {
            return new BookingResult(false,
                    "Exceeds maximum allowed tickets per user (" + MAX_TICKETS_PER_USER + ")");
        }

        if (requestedTickets > availableTickets.get()) {
            return new BookingResult(false, "Not enough tickets available");
        }

        // Atomic operation to decrement tickets
        int remainingTickets = availableTickets.addAndGet(-requestedTickets);
        if (remainingTickets < 0) {
            availableTickets.addAndGet(requestedTickets); // Rollback
            return new BookingResult(false, "Not enough tickets available");
        }

        userTicketCounts.put(userId, userTotal + requestedTickets);
        recordRequest(userId);
        recordDeviceIP(userId, deviceId, ipAddress);

        return new BookingResult(true, "Successfully booked " + requestedTickets + " ticket(s)");
    }

    private boolean isValidUser(String userId) {
        // Implement actual user validation logic here
        return true;
    }

    private boolean isDeviceOrIPSuspicious(String userId, String deviceId, String ipAddress) {
        Set<String> userDevices = userDeviceIPs.getOrDefault(userId, new HashSet<>());

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
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(RATE_LIMIT_MINUTES);

        // Remove old requests
        while (!requests.isEmpty() && requests.peekFirst().isBefore(cutoff)) {
            requests.pollFirst();
        }

        return requests.size() >= MAX_REQUESTS_PER_WINDOW;
    }

    private void recordRequest(String userId) {
        Deque<LocalDateTime> requests = userRequestTimes.computeIfAbsent(userId, k -> new ArrayDeque<>());
        requests.addLast(LocalDateTime.now());
    }

    private void recordDeviceIP(String userId, String deviceId, String ipAddress) {
        userDeviceIPs.computeIfAbsent(userId, k -> new HashSet<>()).add(deviceId);
        userDeviceIPs.computeIfAbsent(userId, k -> new HashSet<>()).add(ipAddress);
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